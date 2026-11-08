package com.norvext.minecraft.notificator;

import java.util.List;

record TelegramMessage(String text, List<Entity> entities) {
    TelegramMessage(String text) {
        this(text, List.of());
    }

    record Entity(String type, int offset, int length) {}
}
