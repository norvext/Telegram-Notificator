package com.norvext.minecraft.notificator;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class Config {
    enum MentionPolicy {
        ALL, OFFLINE, NONE
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final String USERNAME = "[a-zA-Z0-9_]{1,32}";
    private static final Pattern VALID_USERNAME = Pattern.compile(USERNAME);
    private static final Pattern BINDING = Pattern.compile(
            "[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}:" + USERNAME);

    static final ModConfigSpec.ConfigValue<String> TOKEN = BUILDER.define("token", "");
    static final ModConfigSpec.LongValue CHAT_ID = BUILDER.defineInRange("chatId", 0L, Long.MIN_VALUE, 0L);
    static final ModConfigSpec.ConfigValue<List<? extends String>> BINDINGS = BUILDER.defineListAllowEmpty(
            "bindings", List.of(), null, value -> value instanceof String entry && BINDING.matcher(entry).matches());
    static final ModConfigSpec.EnumValue<MentionPolicy> MENTION_POLICY =
            BUILDER.defineEnum("mentionPolicy", MentionPolicy.ALL);
    static final ModConfigSpec SPEC = BUILDER.build();

    static boolean validUsername(String username) {
        return VALID_USERNAME.matcher(username).matches();
    }

    static Map<UUID, String> bindings() {
        Map<UUID, String> bindings = new LinkedHashMap<>();
        for (String entry : BINDINGS.get()) {
            int separator = entry.indexOf(':');
            bindings.put(UUID.fromString(entry.substring(0, separator)), entry.substring(separator + 1));
        }
        return bindings;
    }

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
