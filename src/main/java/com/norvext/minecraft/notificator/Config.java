package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class Config {
    enum MentionPolicy {
        ALL, OFFLINE, NONE
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CommentedConfig EMPTY = TomlFormat.newConfig();

    private final TelegramSettingsFile file = new TelegramSettingsFile();
    // All changes are applied on the server thread before an HTTP request takes a snapshot.
    private TelegramSettingsFile.Contents contents;
    private Map<UUID, String> bindings = Map.of();
    private NotificationSettings notifications = NotificationSettings.read(EMPTY);

    Config() {
        try {
            load(true);
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.warn("Не удалось прочитать настройки Telegram ({})", failure.getClass().getSimpleName());
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
        settings.set("bindings", PlayerBindings.encode(nextBindings));
        if (NotificationSettings.addDefaults(settings)) {
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
        if (settings().get("mentionPolicy") instanceof String policy) {
            try {
                return MentionPolicy.valueOf(policy.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall back to the default for an unrecognized policy.
            }
        }
        return MentionPolicy.ALL;
    }

    int rejoinSeconds() {
        return nonNegativeInt("rejoinSeconds", 300);
    }

    record Destination(long chatId, int topicId) {}

    Destination destination() {
        Object value = settings().get("chatId");
        long chatId = value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : 0;
        return new Destination(Math.min(chatId, 0), nonNegativeInt("topicId", 0));
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
        var updated = CommentedConfig.copy(settings());
        updated.set("chatId", chatId);
        updated.set("topicId", topicId);
        contents = file.save(contents, updated);
    }

    void saveNotifyOnStartup(boolean enabled) throws IOException {
        var updated = CommentedConfig.copy(settings());
        updated.set("notifyOnStartup", enabled);
        var nextNotifications = NotificationSettings.read(updated);
        contents = file.save(contents, updated);
        notifications = nextNotifications;
    }

    private void save(String key, Object value) throws IOException {
        var updated = CommentedConfig.copy(settings());
        updated.set(key, value);
        contents = file.save(contents, updated);
    }
}
