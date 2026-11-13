package com.norvext.minecraft.notificator;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.NewlineStyle;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

final class TelegramSettingsFile {
    record Contents(CommentedConfig settings, String source) {}

    private final Path file = FMLPaths.CONFIGDIR.get().resolve(TelegramNotificator.MODID + "-local.toml");

    // Insertion order keeps a hand-edited file stable across rewrites.
    static CommentedConfig newSettings() {
        return TomlFormat.newConfig(LinkedHashMap::new);
    }

    static CommentedConfig copy(CommentedConfig settings) {
        var copy = CommentedConfig.copy(settings, LinkedHashMap::new);
        // The ordered copy does not carry comments over.
        settings.commentMap().forEach((key, comment) -> copy.setComment(List.of(key), comment));
        return copy;
    }

    Path path() {
        return file;
    }

    Contents read() throws IOException {
        String source = readSource();
        try {
            var settings = newSettings();
            if (source != null) {
                new TomlParser().parse(new StringReader(source), settings, ParsingMode.REPLACE);
            }
            return new Contents(settings, source);
        } catch (RuntimeException failure) {
            throw new IOException("Не удалось разобрать TOML", failure);
        }
    }

    Contents save(Contents previous, CommentedConfig updated) throws IOException {
        if (previous == null) {
            throw new IOException("Настройки изменились на диске");
        }
        var writer = new TomlWriter();
        // A platform line separator would end up inside multi-line template strings.
        writer.setNewline(NewlineStyle.UNIX);
        writer.setWriteStringMultilinePredicate(value -> value.indexOf('\n') >= 0);
        String text = writer.writeToString(updated);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), TelegramNotificator.MODID, ".tmp");
        boolean replaced = false;
        try {
            Files.writeString(temporary, text);
            if (!Objects.equals(previous.source(), readSource())) {
                throw new IOException("Настройки изменились на диске");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
        return new Contents(updated, text);
    }

    private String readSource() throws IOException {
        try {
            return Files.readString(file);
        } catch (NoSuchFileException ignored) {
            return null;
        }
    }
}
