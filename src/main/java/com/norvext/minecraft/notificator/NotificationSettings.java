package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.CommentedConfig;

record NotificationSettings(String serverName, boolean notifyOnStartup, MessageTemplate join,
                            MessageTemplate startup, MessageTemplate test) {
    private static final String DEFAULT_NAME = "Minecraft";
    private static final String DEFAULT_JOIN = "<PlayerName> вошёл на сервер <ServerName>\n<TgTags>";
    private static final String DEFAULT_STARTUP = "Сервер <ServerName> запущен\n<TgTags>";
    private static final String DEFAULT_TEST = "Пробное сообщение от <ServerName>";

    static NotificationSettings read(CommentedConfig settings) {
        String name = text(settings, "serverName", DEFAULT_NAME);
        if (name.isBlank() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("serverName должен быть непустой строкой без переноса");
        }
        Object startupEnabled = settings.getOrElse("notifyOnStartup", true);
        if (!(startupEnabled instanceof Boolean enabled)) {
            throw new IllegalArgumentException("notifyOnStartup должен иметь значение true или false");
        }
        return new NotificationSettings(name, enabled,
                template(settings, "joinMessage", DEFAULT_JOIN, true, true, name),
                template(settings, "startupMessage", DEFAULT_STARTUP, false, true, name),
                template(settings, "testMessage", DEFAULT_TEST, false, false, name));
    }

    static boolean addDefaults(CommentedConfig settings) {
        boolean changed = settings.add("serverName", DEFAULT_NAME);
        changed |= settings.add("notifyOnStartup", true);
        changed |= settings.add("joinMessage", DEFAULT_JOIN);
        changed |= settings.add("startupMessage", DEFAULT_STARTUP);
        changed |= settings.add("testMessage", DEFAULT_TEST);
        return changed;
    }

    private static String text(CommentedConfig settings, String key, String fallback) {
        Object value = settings.getOrElse(key, fallback);
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException(key + " должен быть строкой");
    }

    private static MessageTemplate template(CommentedConfig settings, String key, String fallback, boolean player,
                                            boolean tags, String name) {
        try {
            return new MessageTemplate(text(settings, key, fallback), player, tags, name);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(key + ": " + failure.getMessage());
        }
    }
}
