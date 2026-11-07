package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

final class Config {
    enum MentionPolicy {
        ALL, OFFLINE, NONE
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String USERNAME = "[a-zA-Z0-9_]{1,32}";
    private static final Pattern VALID_USERNAME = Pattern.compile(USERNAME);
    private static final Pattern BINDING = Pattern.compile(
            "([a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}):(" + USERNAME + ")");

    private final Path file = FMLPaths.CONFIGDIR.get().resolve(TelegramNotificator.MODID + "-local.toml");
    private final boolean loadFailed;
    // Accessed by the server thread after construction. HTTP callbacks use values captured before sending.
    private UnmodifiableConfig settings;
    private Map<UUID, String> bindings;

    Config() {
        var loaded = TomlFormat.newConfig();
        boolean failed = false;
        try (var reader = Files.newBufferedReader(file)) {
            loaded = new TomlParser().parse(reader);
        } catch (NoSuchFileException ignored) {
            // A new installation starts with defaults.
        } catch (IOException | RuntimeException failure) {
            failed = true;
            LOGGER.warn("Не удалось прочитать настройки Telegram ({})", failure.getClass().getSimpleName());
        }

        bindings = decodeBindings(loaded.get("bindings"));
        loaded.set("bindings", encodeBindings(bindings));
        settings = loaded;
        loadFailed = failed;
    }

    static boolean validUsername(String username) {
        return VALID_USERNAME.matcher(username).matches();
    }

    String token() {
        return settings.get("token") instanceof String token ? token : "";
    }

    MentionPolicy mentionPolicy() {
        if (settings.get("mentionPolicy") instanceof String policy) {
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
        Object value = settings.get("chatId");
        long chatId = value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : 0;
        return new Destination(Math.min(chatId, 0), nonNegativeInt("topicId", 0));
    }

    private int nonNegativeInt(String key, int fallback) {
        return settings.get(key) instanceof Integer value && value >= 0 ? value : fallback;
    }

    Map<UUID, String> bindings() {
        return new LinkedHashMap<>(bindings);
    }

    private static Map<UUID, String> decodeBindings(Object value) {
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

    private static List<String> encodeBindings(Map<UUID, String> bindings) {
        return bindings.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList();
    }

    void saveBindings(Map<UUID, String> bindings) throws IOException {
        save("bindings", encodeBindings(bindings));
        this.bindings = new LinkedHashMap<>(bindings);
    }

    void saveToken(String token) throws IOException {
        save("token", token);
    }

    void saveMentionPolicy(MentionPolicy policy) throws IOException {
        save("mentionPolicy", policy.name());
    }

    void saveRejoinSeconds(int seconds) throws IOException {
        save("rejoinSeconds", seconds);
    }

    void saveDestination(long chatId, int topicId) throws IOException {
        var updated = com.electronwill.nightconfig.core.Config.copy(settings);
        updated.set("chatId", chatId);
        updated.set("topicId", topicId);
        save(updated);
    }

    private void save(String key, Object value) throws IOException {
        var updated = com.electronwill.nightconfig.core.Config.copy(settings);
        updated.set(key, value);
        save(updated);
    }

    private void save(UnmodifiableConfig updated) throws IOException {
        if (loadFailed) {
            throw new IOException("Cannot replace unreadable Telegram settings");
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), TelegramNotificator.MODID, ".tmp");
        boolean replaced = false;
        try {
            Files.writeString(temporary, new TomlWriter().writeToString(updated));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
        settings = updated;
    }
}
