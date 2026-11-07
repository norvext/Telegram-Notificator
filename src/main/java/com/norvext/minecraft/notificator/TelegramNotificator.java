package com.norvext.minecraft.notificator;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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

import java.util.concurrent.CompletableFuture;

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
                .then(literal("token").requires(TelegramNotificator::isAdmin)
                        .then(literal("set").then(argument("token", StringArgumentType.greedyString())
                                .executes(context -> setToken(context.getSource(), StringArgumentType.getString(context, "token")))))
                        .then(literal("remove").executes(context -> removeToken(context.getSource()))))
                .then(literal("chat").requires(TelegramNotificator::isAdmin)
                        .then(literal("set").then(argument("chat_id", LongArgumentType.longArg(Long.MIN_VALUE, -1))
                                .executes(context -> setChat(context.getSource(),
                                        LongArgumentType.getLong(context, "chat_id"), 0))
                                .then(argument("topic_id", IntegerArgumentType.integer(1))
                                        .executes(context -> setChat(context.getSource(),
                                                LongArgumentType.getLong(context, "chat_id"),
                                                IntegerArgumentType.getInteger(context, "topic_id")))))))
                .then(literal("test").requires(TelegramNotificator::isAdmin)
                        .executes(context -> sendTest(context.getSource()))));
    }

    static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return source.source instanceof DedicatedServer
                || (player != null && source.source == player.commandSource()
                        && source.getServer().getPlayerList().isOp(player.nameAndId()));
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

    private static int setChat(CommandSourceStack source, long chatId, int topicId) {
        return saveSetting(source, () -> Config.saveDestination(chatId, topicId), "Группа уведомлений сохранена");
    }

    private static <T> int saveSetting(CommandSourceStack source, ModConfigSpec.ConfigValue<T> setting,
                                       T value, String success) {
        return saveSetting(source, () -> Config.save(setting, value), success);
    }

    private static int saveSetting(CommandSourceStack source, Runnable save, String success) {
        try {
            save.run();
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
            send(player.getGameProfile().name() + " вошёл на сервер Minecraft");
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
        Config.Destination destination = Config.destination();
        if (token.isEmpty() || destination.chatId() == 0) {
            return null;
        }

        return telegram.send(token, destination, text).thenApply(error -> {
            if (error != null) {
                LOGGER.warn("Не удалось отправить сообщение Telegram: {}", error);
            }
            return error;
        });
    }
}
