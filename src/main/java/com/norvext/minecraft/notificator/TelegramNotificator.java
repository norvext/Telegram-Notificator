package com.norvext.minecraft.notificator;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod(value = TelegramNotificator.MODID, dist = Dist.DEDICATED_SERVER)
public class TelegramNotificator {
    public static final String MODID = "telegramnotificator";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Config config = new Config();
    private final TelegramClient telegram = new TelegramClient();
    private boolean stopping;

    public TelegramNotificator() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        var mentions = literal("mentions").requires(TelegramNotificator::isAdmin);
        for (var policy : Config.MentionPolicy.values()) {
            String mode = policy.name().toLowerCase(Locale.ROOT);
            mentions.then(literal(mode).executes(context -> saveSetting(context.getSource(),
                    () -> config.saveMentionPolicy(policy), "Режим упоминаний сохранён: " + mode)));
        }

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
                                .executes(context -> setChat(context.getSource(),
                                        LongArgumentType.getLong(context, "chat_id"), 0))
                                .then(argument("topic_id", IntegerArgumentType.integer(1))
                                        .executes(context -> setChat(context.getSource(),
                                                LongArgumentType.getLong(context, "chat_id"),
                                                IntegerArgumentType.getInteger(context, "topic_id")))))))
                .then(literal("rejoin").requires(TelegramNotificator::isAdmin)
                        .then(literal("set").then(argument("seconds", IntegerArgumentType.integer(0))
                                .executes(context -> saveSetting(context.getSource(),
                                        () -> config.saveRejoinSeconds(IntegerArgumentType.getInteger(context, "seconds")),
                                        "Интервал перезахода сохранён")))))
                .then(mentions)
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

    private int link(CommandSourceStack source, String username) {
        if (!Config.validUsername(username)) {
            source.sendFailure(Component.literal("Недопустимый формат Telegram username"));
            return 0;
        }

        UUID playerId = source.getPlayer().getUUID();
        Map<UUID, String> bindings = config.bindings();
        boolean occupied = bindings.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(playerId) && entry.getValue().equalsIgnoreCase(username));
        if (occupied) {
            source.sendFailure(Component.literal("Этот Telegram username уже привязан"));
            return 0;
        }

        bindings.put(playerId, username);
        return saveBindings(source, bindings, "Привязка сохранена");
    }

    private int unlinkSelf(CommandSourceStack source) throws CommandSyntaxException {
        UUID playerId = source.getPlayerOrException().getUUID();
        return unlink(source, entry -> entry.getKey().equals(playerId));
    }

    private int unlink(CommandSourceStack source, Predicate<Map.Entry<UUID, String>> target) {
        Map<UUID, String> bindings = config.bindings();
        if (!bindings.entrySet().removeIf(target)) {
            source.sendFailure(Component.literal("Привязка не найдена"));
            return 0;
        }
        return saveBindings(source, bindings, "Привязка удалена");
    }

    private int saveBindings(CommandSourceStack source, Map<UUID, String> bindings, String success) {
        return saveSetting(source, () -> config.saveBindings(bindings), success);
    }

    private int setToken(CommandSourceStack source, String token) {
        if (!TelegramClient.validToken(token)) {
            source.sendFailure(Component.literal("Недопустимый формат токена"));
            return 0;
        }
        return saveSetting(source, () -> config.saveToken(token), "Токен сохранён");
    }

    private int removeToken(CommandSourceStack source) {
        if (config.token().isEmpty()) {
            source.sendFailure(Component.literal("Токен не задан"));
            return 0;
        }
        return saveSetting(source, () -> config.saveToken(""), "Токен удалён");
    }

    private int setChat(CommandSourceStack source, long chatId, int topicId) {
        return saveSetting(source, () -> config.saveDestination(chatId, topicId), "Группа уведомлений сохранена");
    }

    @FunctionalInterface
    private interface SaveSetting {
        void run() throws IOException;
    }

    private static int saveSetting(CommandSourceStack source, SaveSetting save, String success) {
        try {
            save.run();
        } catch (IOException | RuntimeException failure) {
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
        if (event.getEntity() instanceof ServerPlayer player
                && !PlayerLogouts.get(player.level().getServer()).suppressJoin(
                        player.getUUID(), System.currentTimeMillis(), config.rejoinSeconds())
                && isConfigured()) {
            send(joinMessages(player));
        }
    }

    private List<String> joinMessages(ServerPlayer player) {
        String header = player.getGameProfile().name() + " вошёл на сервер Minecraft";
        var messages = new ArrayList<String>();
        var text = new StringBuilder(header);
        char separator = '\n';
        for (String username : recipients(player)) {
            if (text.length() + 2 + username.length() > TelegramClient.MAX_TEXT_LENGTH) {
                messages.add(text.toString());
                text.setLength(0);
            }
            if (!text.isEmpty()) {
                text.append(separator);
            }
            text.append('@').append(username);
            separator = ' ';
        }
        messages.add(text.toString());
        return messages;
    }

    private List<String> recipients(ServerPlayer player) {
        var policy = config.mentionPolicy();
        if (policy == Config.MentionPolicy.NONE) {
            return List.of();
        }
        var players = player.level().getServer().getPlayerList();
        return config.bindings().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(player.getUUID()))
                .filter(entry -> policy == Config.MentionPolicy.ALL || players.getPlayer(entry.getKey()) == null)
                .map(Map.Entry::getValue)
                .toList();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!stopping && event.getEntity() instanceof ServerPlayer player) {
            PlayerLogouts.get(player.level().getServer()).record(player.getUUID(), System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // PlayerList removes online players after this event, producing further logout events.
        stopping = true;
        long stoppedAt = System.currentTimeMillis();
        var server = event.getServer();
        var logouts = PlayerLogouts.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            logouts.record(player.getUUID(), stoppedAt);
        }
    }

    private int sendTest(CommandSourceStack source) {
        if (!isConfigured()) {
            source.sendFailure(Component.literal("Задайте токен и группу уведомлений"));
            return 0;
        }

        send(List.of("Пробное сообщение от Minecraft")).thenAccept(error -> source.getServer().execute(() -> {
            if (error.isPresent()) {
                source.sendFailure(Component.literal("Пробное сообщение не отправлено: " + error.get()));
            } else {
                source.sendSuccess(() -> Component.literal("Пробное сообщение отправлено"), false);
            }
        }));
        return 1;
    }

    private boolean isConfigured() {
        return !config.token().isEmpty() && config.destination().chatId() != 0;
    }

    private CompletableFuture<Optional<String>> send(List<String> messages) {
        String token = config.token();
        Config.Destination destination = config.destination();
        var result = CompletableFuture.completedFuture(Optional.<String>empty());
        for (String text : messages) {
            result = result.thenCompose(error -> error.isPresent()
                    ? CompletableFuture.completedFuture(error)
                    : telegram.send(token, destination, text));
        }
        return result.thenApply(error -> {
            error.ifPresent(reason -> LOGGER.warn("Не удалось отправить сообщение Telegram: {}", reason));
            return error;
        });
    }
}
