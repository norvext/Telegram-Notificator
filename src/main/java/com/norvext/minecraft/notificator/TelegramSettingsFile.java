package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.Config;
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

final class TelegramSettingsFile {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path file = FMLPaths.CONFIGDIR.get().resolve(TelegramNotificator.MODID + "-local.toml");
    private final boolean loadFailed;
    // Accessed by the server thread after construction. HTTP callbacks use values captured before sending.
    private Config settings;

    TelegramSettingsFile() {
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

        settings = loaded;
        loadFailed = failed;
    }

    Config settings() {
        return settings;
    }

    void save(String key, Object value) throws IOException {
        var updated = Config.copy(settings);
        updated.set(key, value);
        save(updated);
    }

    void save(Config updated) throws IOException {
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
