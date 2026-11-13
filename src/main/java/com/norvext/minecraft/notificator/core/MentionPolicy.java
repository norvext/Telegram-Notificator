package com.norvext.minecraft.notificator.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public enum MentionPolicy {
    ALL, OFFLINE, NONE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MentionPolicy parse(Object value) {
        if (value instanceof String policy) {
            try {
                return valueOf(policy.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall back to the default for an unrecognized policy.
            }
        }
        return ALL;
    }

    /** Telegram usernames to mention when {@code joined} enters; the joining player is never mentioned. */
    public List<String> recipients(Map<UUID, String> bindings, UUID joined, Predicate<UUID> online) {
        if (this == NONE) {
            return List.of();
        }
        return bindings.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(joined))
                .filter(entry -> this == ALL || !online.test(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }
}
