package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.logging.LogUtils;
import com.norvext.minecraft.notificator.core.MentionPolicy;
import com.norvext.minecraft.notificator.core.PlayerBindings;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CommentedConfig EMPTY = TelegramSettingsFile.newSettings();
    private static final int DEFAULT_REJOIN_SECONDS = 300;

    private final TelegramSettingsFile file = new TelegramSettingsFile();
    // All changes are applied on the server thread before an HTTP request takes a snapshot.
    private TelegramSettingsFile.Contents contents;
    private Map<UUID, String> bindings = Map.of();
    private NotificationSettings notifications = NotificationSettings.read(EMPTY);

    Config() {
        try {
            load(true);
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.warn("Не удалось прочитать настройки Telegram ({}): {}",
                    failure.getClass().getSimpleName(), failure.getMessage());
        }
    }

    void reload() throws IOException {
        load(false);
    }

    private void load(boolean allowMissing) throws IOException {
        var loaded = file.read();
        if (!allowMissing && loaded.source() == null) {
            throw new IOException("Файл настроек отсутствует");
        }
        var settings = loaded.settings();
        var nextNotifications = NotificationSettings.read(settings);
        var nextBindings = PlayerBindings.decode(settings.get("bindings"));
        if (describe(settings, nextBindings)) {
            try {
                loaded = file.save(loaded, settings);
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Не удалось записать настройки уведомлений по умолчанию ({})",
                        failure.getClass().getSimpleName());
            }
        }
        contents = loaded;
        bindings = nextBindings;
        notifications = nextNotifications;
    }

    /** Fills in missing keys and comments so the file documents itself; returns whether it changed. */
    private static boolean describe(CommentedConfig settings, Map<UUID, String> bindings) {
        boolean changed = entry(settings, "serverName", NotificationSettings.DEFAULT_NAME,
                "Имя сервера в сообщениях, подставляется вместо <ServerName>.");
        changed |= entry(settings, "token", "",
                "Токен бота от @BotFather. Команда: telegram token set <токен>");
        changed |= entry(settings, "chatId", 0L,
                "Отрицательный ID группы Telegram. Команда: telegram chat set <chat_id> [topic_id]");
        changed |= entry(settings, "topicId", 0,
                "ID темы внутри группы, 0 — общий чат.");
        changed |= entry(settings, "mentionPolicy", MentionPolicy.ALL.name(),
                "Кого упоминать при входе игрока: ALL — всех привязанных, OFFLINE — только тех, кто не в игре, NONE — никого.");
        changed |= entry(settings, "rejoinSeconds", DEFAULT_REJOIN_SECONDS,
                "Не сообщать о входе, если игрок вернулся раньше этого числа секунд после выхода. 0 отключает фильтр.");
        changed |= entry(settings, "notifyOnStartup", true,
                "Сообщать о запуске сервера.");
        changed |= entry(settings, "notifyOnLeave", false,
                "Сообщать о выходе игрока. Сообщение уходит, когда истёк rejoinSeconds и игрок не вернулся.");
        changed |= entry(settings, "joinMessage", NotificationSettings.DEFAULT_JOIN,
                "Шаблоны. Подстановки: <ServerName>, <PlayerName>, <Online>, <MaxPlayers>, <TgTags> — список упоминаний.\n"
                        + " Стили: **жирный**, _курсив_, __подчёркнутый__, ~~зачёркнутый~~, `моноширинный`.");
        changed |= entry(settings, "leaveMessage", NotificationSettings.DEFAULT_LEAVE, null);
        changed |= entry(settings, "startupMessage", NotificationSettings.DEFAULT_STARTUP, null);
        changed |= entry(settings, "testMessage", NotificationSettings.DEFAULT_TEST, null);

        List<String> encoded = PlayerBindings.encode(bindings);
        changed |= !encoded.equals(settings.get("bindings"));
        settings.set("bindings", encoded);
        changed |= comment(settings, "bindings",
                "Привязки игроков «UUID:username». Команды: telegram link <username>, telegram unlink");
        return changed;
    }

    private static boolean entry(CommentedConfig settings, String key, Object fallback, String comment) {
        return settings.add(key, fallback) | (comment != null && comment(settings, key, comment));
    }

    private static boolean comment(CommentedConfig settings, String key, String comment) {
        if (settings.getComment(key) != null) {
            return false;
        }
        settings.setComment(key, " " + comment);
        return true;
    }

    Path file() {
        return file.path();
    }

    NotificationSettings notifications() {
        return notifications;
    }

    private CommentedConfig settings() {
        return contents == null ? EMPTY : contents.settings();
    }

    String token() {
        return settings().get("token") instanceof String token ? token : "";
    }

    MentionPolicy mentionPolicy() {
        return MentionPolicy.parse(settings().get("mentionPolicy"));
    }

    int rejoinSeconds() {
        return nonNegativeInt("rejoinSeconds", DEFAULT_REJOIN_SECONDS);
    }

    record Destination(long chatId, int topicId) {}

    Destination destination() {
        Object value = settings().get("chatId");
        long chatId = value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : 0;
        return new Destination(Math.min(chatId, 0), nonNegativeInt("topicId", 0));
    }

    boolean isConfigured() {
        return !token().isEmpty() && destination().chatId() != 0;
    }

    private int nonNegativeInt(String key, int fallback) {
        return settings().get(key) instanceof Integer value && value >= 0 ? value : fallback;
    }

    Map<UUID, String> bindings() {
        return new LinkedHashMap<>(bindings);
    }

    void saveBindings(Map<UUID, String> bindings) throws IOException {
        save("bindings", PlayerBindings.encode(bindings));
        this.bindings = new LinkedHashMap<>(bindings);
    }

    void saveToken(String token) throws IOException {
        save("token", token);
    }

    void saveMentionPolicy(MentionPolicy policy) throws IOException {
        save("mentionPolicy", policy.name());
    }

    void saveRejoinSeconds(int seconds) throws IOException {
        save("rejoinSeconds", seconds);
    }

    void saveDestination(long chatId, int topicId) throws IOException {
        var updated = TelegramSettingsFile.copy(settings());
        updated.set("chatId", chatId);
        updated.set("topicId", topicId);
        contents = file.save(contents, updated);
    }

    void saveNotifyOnStartup(boolean enabled) throws IOException {
        saveNotification("notifyOnStartup", enabled);
    }

    void saveNotifyOnLeave(boolean enabled) throws IOException {
        saveNotification("notifyOnLeave", enabled);
    }

    private void saveNotification(String key, boolean enabled) throws IOException {
        var updated = TelegramSettingsFile.copy(settings());
        updated.set(key, enabled);
        var nextNotifications = NotificationSettings.read(updated);
        contents = file.save(contents, updated);
        notifications = nextNotifications;
    }

    private void save(String key, Object value) throws IOException {
        var updated = TelegramSettingsFile.copy(settings());
        updated.set(key, value);
        contents = file.save(contents, updated);
    }
}
