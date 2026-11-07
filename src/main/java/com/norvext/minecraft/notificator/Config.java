package com.norvext.minecraft.notificator;

import net.neoforged.neoforge.common.ModConfigSpec;

final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.ConfigValue<String> TOKEN = BUILDER.define("token", "");
    static final ModConfigSpec.LongValue CHAT_ID = BUILDER.defineInRange("chatId", 0L, Long.MIN_VALUE, 0L);
    static final ModConfigSpec SPEC = BUILDER.build();

    static <T> void save(ModConfigSpec.ConfigValue<T> setting, T value) {
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
