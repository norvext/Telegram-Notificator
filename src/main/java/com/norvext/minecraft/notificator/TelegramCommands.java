package com.norvext.minecraft.notificator;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

final class TelegramCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Config config;
    private final TelegramNotifications notifications;

    TelegramCommands(Config config, TelegramNotifications notifications) {
        this.config = config;
        this.notifications = notifications;
    }

    void registerCommands(RegisterCommandsEvent event) {
        var mentions = literal("mentions").requires(TelegramCommands::isAdmin);
        for (var policy : Config.MentionPolicy.values()) {
            String mode = policy.name().toLowerCase(Locale.ROOT);
            mentions.then(literal(mode).executes(context -> saveSetting(context.getSource(),
                    () -> config.saveMentionPolicy(policy), "Режим упоминаний сохранён: " + mode)));
        }

        event.getDispatcher().register(literal("telegram")
                .then(literal("link").requires(TelegramCommands::isPlayer)
                        .then(argument("username", StringArgumentType.word())
                                .executes(context -> link(context.getSource(), StringArgumentType.getString(context, "username")))))
                .then(literal("unlink").requires(source -> isPlayer(source) || isAdmin(source))
                        .executes(context -> unlinkSelf(context.getSource()))
                        .then(argument("username", StringArgumentType.word()).requires(TelegramCommands::isAdmin)
                                .executes(context -> {
                                    String username = StringArgumentType.getString(context, "username");
                                    return unlink(context.getSource(), entry -> entry.getValue().equalsIgnoreCase(username));
                                })))
                .then(literal("token").requires(TelegramCommands::isAdmin)
                        .then(literal("set").then(argument("token", StringArgumentType.greedyString())
                                .executes(context -> setToken(context.getSource(), StringArgumentType.getString(context, "token")))))
                        .then(literal("remove").executes(context -> removeToken(context.getSource()))))
                .then(literal("chat").requires(TelegramCommands::isAdmin)
                        .then(literal("set").then(argument("chat_id", LongArgumentType.longArg(Long.MIN_VALUE, -1))
                                .executes(context -> setChat(context.getSource(),
                                        LongArgumentType.getLong(context, "chat_id"), 0))
                                .then(argument("topic_id", IntegerArgumentType.integer(1))
                                        .executes(context -> setChat(context.getSource(),
                                                LongArgumentType.getLong(context, "chat_id"),
                                                IntegerArgumentType.getInteger(context, "topic_id")))))))
                .then(literal("rejoin").requires(TelegramCommands::isAdmin)
                        .then(literal("set").then(argument("seconds", IntegerArgumentType.integer(0))
                                .executes(context -> saveSetting(context.getSource(),
                                        () -> config.saveRejoinSeconds(IntegerArgumentType.getInteger(context, "seconds")),
                                        "Интервал перезахода сохранён")))))
                .then(mentions)
                .then(literal("startup").requires(TelegramCommands::isAdmin)
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setStartup(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")))))
                .then(literal("reload").requires(TelegramCommands::isAdmin)
                        .executes(context -> reload(context.getSource())))
                .then(literal("test").requires(TelegramCommands::isAdmin)
                        .executes(context -> sendTest(context.getSource()))));
    }

    private static boolean isPlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && source.source == player.commandSource();
    }

    private static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return source.source instanceof DedicatedServer
                || (player != null && source.source == player.commandSource()
                        && source.getServer().getPlayerList().isOp(player.nameAndId()));
    }

    private int link(CommandSourceStack source, String username) {
        if (!PlayerBindings.validUsername(username)) {
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

    private int setStartup(CommandSourceStack source, boolean enabled) {
        return saveSetting(source, () -> config.saveNotifyOnStartup(enabled),
                enabled ? "Уведомление о запуске включено" : "Уведомление о запуске выключено");
    }

    private int reload(CommandSourceStack source) {
        try {
            config.reload();
        } catch (IllegalArgumentException failure) {
            source.sendFailure(Component.literal("Настройки Telegram не обновлены: " + failure.getMessage()));
            return 0;
        } catch (IOException failure) {
            source.sendFailure(Component.literal("Не удалось прочитать настройки Telegram"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Настройки Telegram перечитаны"), false);
        return 1;
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
            source.sendFailure(Component.literal(
                    "Не удалось сохранить настройки Telegram. Проверьте файл и выполните /telegram reload"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(success), false);
        return 1;
    }

    private int sendTest(CommandSourceStack source) {
        if (!notifications.isConfigured()) {
            source.sendFailure(Component.literal("Задайте токен и группу уведомлений"));
            return 0;
        }

        notifications.sendTest().thenAccept(error -> source.getServer().execute(() -> {
            if (error.isPresent()) {
                source.sendFailure(Component.literal("Пробное сообщение не отправлено: " + error.get()));
            } else {
                source.sendSuccess(() -> Component.literal("Пробное сообщение отправлено"), false);
            }
        }));
        return 1;
    }
}
