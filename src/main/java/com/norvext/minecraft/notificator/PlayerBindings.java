package com.norvext.minecraft.notificator;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class PlayerBindings {
    private static final String USERNAME = "[a-zA-Z0-9_]{1,32}";
    private static final Pattern VALID_USERNAME = Pattern.compile(USERNAME);
    private static final Pattern BINDING = Pattern.compile(
            "([a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}):(" + USERNAME + ")");

    static boolean validUsername(String username) {
        return VALID_USERNAME.matcher(username).matches();
    }

    static Map<UUID, String> decode(Object value) {
        Map<UUID, String> bindings = new LinkedHashMap<>();
        if (!(value instanceof List<?> entries)) {
            return bindings;
        }

        var usernames = new HashSet<String>();
        for (Object entry : entries) {
            if (!(entry instanceof String text)) {
                continue;
            }
            var match = BINDING.matcher(text);
            if (!match.matches()) {
                continue;
            }
            UUID player = UUID.fromString(match.group(1));
            String username = match.group(2);
            if (!bindings.containsKey(player) && usernames.add(username.toLowerCase(Locale.ROOT))) {
                bindings.put(player, username);
            }
        }
        return bindings;
    }

    static List<String> encode(Map<UUID, String> bindings) {
        return bindings.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList();
    }
}
