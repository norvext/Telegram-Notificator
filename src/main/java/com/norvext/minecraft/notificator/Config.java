package com.norvext.minecraft.notificator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class Config {
    enum MentionPolicy {
        ALL, OFFLINE, NONE
    }

    private final TelegramSettingsFile file = new TelegramSettingsFile();
    private Map<UUID, String> bindings;

    Config() {
        var settings = file.settings();
        bindings = PlayerBindings.decode(settings.get("bindings"));
        settings.set("bindings", PlayerBindings.encode(bindings));
    }

    String token() {
        return file.settings().get("token") instanceof String token ? token : "";
    }

    MentionPolicy mentionPolicy() {
        if (file.settings().get("mentionPolicy") instanceof String policy) {
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
        Object value = file.settings().get("chatId");
        long chatId = value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : 0;
        return new Destination(Math.min(chatId, 0), nonNegativeInt("topicId", 0));
    }

    private int nonNegativeInt(String key, int fallback) {
        return file.settings().get(key) instanceof Integer value && value >= 0 ? value : fallback;
    }

    Map<UUID, String> bindings() {
        return new LinkedHashMap<>(bindings);
    }

    void saveBindings(Map<UUID, String> bindings) throws IOException {
        file.save("bindings", PlayerBindings.encode(bindings));
        this.bindings = new LinkedHashMap<>(bindings);
    }

    void saveToken(String token) throws IOException {
        file.save("token", token);
    }

    void saveMentionPolicy(MentionPolicy policy) throws IOException {
        file.save("mentionPolicy", policy.name());
    }

    void saveRejoinSeconds(int seconds) throws IOException {
        file.save("rejoinSeconds", seconds);
    }

    void saveDestination(long chatId, int topicId) throws IOException {
        var updated = com.electronwill.nightconfig.core.Config.copy(file.settings());
        updated.set("chatId", chatId);
        updated.set("topicId", topicId);
        file.save(updated);
    }
}
