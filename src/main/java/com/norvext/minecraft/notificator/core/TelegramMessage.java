package com.norvext.minecraft.notificator.core;

import java.util.List;

public record TelegramMessage(String text, List<Entity> entities) {
    /** Telegram limits message text to 4096 UTF-16 code units. */
    public static final int MAX_LENGTH = 4096;

    public record Entity(String type, int offset, int length) {}

    public TelegramMessage(String text) {
        this(text, List.of());
    }
}
