package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.norvext.minecraft.notificator.core.MessageTemplate;

record NotificationSettings(String serverName, boolean notifyOnStartup, boolean notifyOnLeave, MessageTemplate join,
                            MessageTemplate leave, MessageTemplate startup, MessageTemplate test) {
    static final String DEFAULT_NAME = "Minecraft";
    static final String DEFAULT_JOIN = "<PlayerName> вошёл на сервер <ServerName>\n<TgTags>";
    static final String DEFAULT_LEAVE = "<PlayerName> вышел с сервера <ServerName>";
    static final String DEFAULT_STARTUP = "Сервер <ServerName> запущен\n<TgTags>";
    static final String DEFAULT_TEST = "Пробное сообщение от <ServerName>";

    static NotificationSettings read(CommentedConfig settings) {
        String name = text(settings, "serverName", DEFAULT_NAME);
        if (name.isBlank() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("serverName должен быть непустой строкой без переноса");
        }
        return new NotificationSettings(name, flag(settings, "notifyOnStartup", true),
                flag(settings, "notifyOnLeave", false),
                template(settings, "joinMessage", DEFAULT_JOIN, true, true, name),
                template(settings, "leaveMessage", DEFAULT_LEAVE, true, false, name),
                template(settings, "startupMessage", DEFAULT_STARTUP, false, true, name),
                template(settings, "testMessage", DEFAULT_TEST, false, false, name));
    }

    private static boolean flag(CommentedConfig settings, String key, boolean fallback) {
        if (settings.getOrElse(key, fallback) instanceof Boolean enabled) {
            return enabled;
        }
        throw new IllegalArgumentException(key + " должен иметь значение true или false");
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
            // A file edited on Windows carries CRLF into multi-line strings.
            return new MessageTemplate(text(settings, key, fallback).replace("\r\n", "\n"), player, tags, name);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(key + ": " + failure.getMessage());
        }
    }
}
