package com.norvext.minecraft.notificator;

import net.neoforged.neoforge.common.ModConfigSpec;

final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.ConfigValue<String> TOKEN = BUILDER.define("token", "");
    private static final ModConfigSpec.LongValue CHAT_ID = BUILDER.defineInRange("chatId", 0L, Long.MIN_VALUE, 0L);
    private static final ModConfigSpec.IntValue TOPIC_ID = BUILDER.defineInRange("topicId", 0, 0, Integer.MAX_VALUE);
    static final ModConfigSpec SPEC = BUILDER.build();

    record Destination(long chatId, int topicId) {}

    static synchronized Destination destination() {
        return new Destination(CHAT_ID.get(), TOPIC_ID.get());
    }

    static synchronized void saveDestination(long chatId, int topicId) {
        Destination previous = destination();
        try {
            CHAT_ID.set(chatId);
            TOPIC_ID.set(topicId);
            SPEC.save();
        } catch (RuntimeException failure) {
            CHAT_ID.set(previous.chatId());
            TOPIC_ID.set(previous.topicId());
            throw failure;
        }
    }

    static synchronized <T> void save(ModConfigSpec.ConfigValue<T> setting, T value) {
        T previous = setting.get();
        setting.set(value);
        try {
            SPEC.save();
        } catch (RuntimeException failure) {
            setting.set(previous);
            throw failure;
        }
    }
}
