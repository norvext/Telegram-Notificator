package com.norvext.minecraft.notificator;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod(value = TelegramNotificator.MODID, dist = Dist.DEDICATED_SERVER)
public class TelegramNotificator {
    public static final String MODID = "telegramnotificator";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TelegramClient telegram = new TelegramClient();

    public TelegramNotificator(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("telegram")
                .then(literal("link").requires(TelegramNotificator::isPlayer)
                        .then(argument("username", StringArgumentType.word())
                                .executes(context -> link(context.getSource(), StringArgumentType.getString(context, "username")))))
                .then(literal("unlink").requires(source -> isPlayer(source) || isAdmin(source))
                        .executes(context -> unlinkSelf(context.getSource()))
                        .then(argument("username", StringArgumentType.word()).requires(TelegramNotificator::isAdmin)
                                .executes(context -> {
                                    String username = StringArgumentType.getString(context, "username");
                                    return unlink(context.getSource(), entry -> entry.getValue().equalsIgnoreCase(username));
                                })))
                .then(literal("token").requires(TelegramNotificator::isAdmin)
                        .then(literal("set").then(argument("token", StringArgumentType.greedyString())
                                .executes(context -> setToken(context.getSource(), StringArgumentType.getString(context, "token")))))
                        .then(literal("remove").executes(context -> removeToken(context.getSource()))))
                .then(literal("chat").requires(TelegramNotificator::isAdmin)
                        .then(literal("set").then(argument("chat_id", LongArgumentType.longArg(Long.MIN_VALUE, -1))
                                .executes(context -> saveSetting(context.getSource(), Config.CHAT_ID,
                                        LongArgumentType.getLong(context, "chat_id"), "Группа уведомлений сохранена")))))
                .then(literal("test").requires(TelegramNotificator::isAdmin)
                        .executes(context -> sendTest(context.getSource()))));
    }

    private static boolean isPlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && source.source == player.commandSource();
    }

    static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return source.source instanceof DedicatedServer
                || (player != null && source.source == player.commandSource()
                        && source.getServer().getPlayerList().isOp(player.nameAndId()));
    }

    private static int link(CommandSourceStack source, String username) {
        if (!Config.validUsername(username)) {
            source.sendFailure(Component.literal("Недопустимый формат Telegram username"));
            return 0;
        }

        UUID playerId = source.getPlayer().getUUID();
        Map<UUID, String> bindings = Config.bindings();
        boolean occupied = bindings.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(playerId) && entry.getValue().equalsIgnoreCase(username));
        if (occupied) {
            source.sendFailure(Component.literal("Этот Telegram username уже привязан"));
            return 0;
        }

        bindings.put(playerId, username);
        return saveBindings(source, bindings, "Привязка сохранена");
    }

    private static int unlinkSelf(CommandSourceStack source) throws CommandSyntaxException {
        UUID playerId = source.getPlayerOrException().getUUID();
        return unlink(source, entry -> entry.getKey().equals(playerId));
    }

    private static int unlink(CommandSourceStack source, Predicate<Map.Entry<UUID, String>> target) {
        Map<UUID, String> bindings = Config.bindings();
        if (!bindings.entrySet().removeIf(target)) {
            source.sendFailure(Component.literal("Привязка не найдена"));
            return 0;
        }
        return saveBindings(source, bindings, "Привязка удалена");
    }

    private static int saveBindings(CommandSourceStack source, Map<UUID, String> bindings, String success) {
        return saveSetting(source, Config.BINDINGS, bindings.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .toList(), success);
    }

    private static int setToken(CommandSourceStack source, String token) {
        if (!TelegramClient.validToken(token)) {
            source.sendFailure(Component.literal("Недопустимый формат токена"));
            return 0;
        }
        return saveSetting(source, Config.TOKEN, token, "Токен сохранён");
    }

    private static int removeToken(CommandSourceStack source) {
        if (Config.TOKEN.get().isEmpty()) {
            source.sendFailure(Component.literal("Токен не задан"));
            return 0;
        }
        return saveSetting(source, Config.TOKEN, "", "Токен удалён");
    }

    private static <T> int saveSetting(CommandSourceStack source, ModConfigSpec.ConfigValue<T> setting,
                                       T value, String success) {
        try {
            Config.save(setting, value);
        } catch (RuntimeException failure) {
            // Minecraft may log the entire command if an exception escapes its handler.
            LOGGER.warn("Не удалось сохранить настройки Telegram ({})", failure.getClass().getSimpleName());
            source.sendFailure(Component.literal("Не удалось сохранить настройки Telegram"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(success), false);
        return 1;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String mentions = Config.bindings().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(player.getUUID()))
                    .map(entry -> "@" + entry.getValue())
                    .collect(Collectors.joining(" "));
            String message = player.getGameProfile().name() + " вошёл на сервер Minecraft";
            send(mentions.isEmpty() ? message : message + "\n" + mentions);
        }
    }

    private int sendTest(CommandSourceStack source) {
        var result = send("Пробное сообщение от Minecraft");
        if (result == null) {
            source.sendFailure(Component.literal("Задайте токен и группу уведомлений"));
            return 0;
        }

        result.thenAccept(error -> source.getServer().execute(() -> {
            if (error == null) {
                source.sendSuccess(() -> Component.literal("Пробное сообщение отправлено"), false);
            } else {
                source.sendFailure(Component.literal("Пробное сообщение не отправлено: " + error));
            }
        }));
        return 1;
    }

    private CompletableFuture<String> send(String text) {
        String token = Config.TOKEN.get();
        long chatId = Config.CHAT_ID.get();
        if (token.isEmpty() || chatId == 0) {
            return null;
        }

        return telegram.send(token, chatId, text).thenApply(error -> {
            if (error != null) {
                LOGGER.warn("Не удалось отправить сообщение Telegram: {}", error);
            }
            return error;
        });
    }
}
