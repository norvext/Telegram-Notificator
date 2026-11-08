package com.norvext.minecraft.notificator;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MessageTemplate {
    private static final String TAGS = "<TgTags>";
    private static final Pattern VALUES = Pattern.compile("<ServerName>|<PlayerName>");
    private static final Pattern STYLES = Pattern.compile(
            "```([\\s\\S]*?)```|`([^`\\n]+)`|\\*\\*([^\\n]+?)\\*\\*"
                    + "|(?<![\\p{L}\\p{N}_])_([^_\\n]+)_(?![\\p{L}\\p{N}_])"
                    + "|__([^\\n]+?)__|~~([^\\n]+?)~~");
    private static final List<String> STYLE_TYPES = List.of("pre", "code", "bold", "italic", "underline", "strikethrough");

    private static final Pattern WORD_AT_END = Pattern.compile("[\\p{L}\\p{N}_]\\z");
    private static final Pattern WORD_AT_START = Pattern.compile("^[\\p{L}\\p{N}_]");

    private final String beforeTags;
    private final String afterTags;
    private final boolean hasTags;

    MessageTemplate(String source, boolean allowsPlayer, boolean allowsTags, String serverName) {
        int tags = source.indexOf(TAGS);
        if (source.length() > 16384 || (!allowsPlayer && source.contains("<PlayerName>"))
                || (tags >= 0 && (!allowsTags || source.indexOf(TAGS, tags + TAGS.length()) >= 0))) {
            throw new IllegalArgumentException("Недопустимый шаблон сообщения");
        }
        if (tags >= 0) {
            var styles = STYLES.matcher(source);
            while (styles.find()) {
                if (styles.start() < tags && tags < styles.end()) {
                    throw new IllegalArgumentException("<TgTags> должен быть вне форматирования");
                }
            }
        }
        hasTags = tags >= 0;
        beforeTags = hasTags ? source.substring(0, tags) : source;
        afterTags = hasTags ? source.substring(tags + TAGS.length()) : "";
        String sample = format(withoutTags(), serverName, "0123456789abcdef").text();
        if (sample.isBlank() || sample.length() > TelegramClient.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Сообщение должно содержать от 1 до 4096 символов");
        }
    }

    List<TelegramMessage> render(String serverName, String playerName, List<String> usernames) {
        if (!hasTags || usernames.isEmpty()) {
            return List.of(format(withoutTags(), serverName, playerName));
        }

        var before = format(beforeTags.stripLeading(), serverName, playerName);
        var after = format(afterTags.stripTrailing(), serverName, playerName);
        String leftSpace = WORD_AT_END.matcher(before.text()).find() ? " " : "";
        String rightSpace = WORD_AT_START.matcher(after.text()).find() ? " " : "";
        int available = TelegramClient.MAX_TEXT_LENGTH
                - before.text().length() - after.text().length() - leftSpace.length() - rightSpace.length();
        var mentions = new StringJoiner(" ");
        var messages = new ArrayList<TelegramMessage>();
        for (String username : usernames) {
            int length = mentions.length() + (mentions.length() == 0 ? 0 : 1) + 1 + username.length();
            if (length > available) {
                if (messages.isEmpty()) {
                    messages.add(mentions.length() == 0
                            ? format(withoutTags(), serverName, playerName)
                            : combine(before, leftSpace + mentions + rightSpace, after));
                } else {
                    messages.add(new TelegramMessage(mentions.toString()));
                }
                available = TelegramClient.MAX_TEXT_LENGTH;
                mentions = new StringJoiner(" ");
            }
            mentions.add("@" + username);
        }
        messages.add(messages.isEmpty()
                ? combine(before, leftSpace + mentions + rightSpace, after)
                : new TelegramMessage(mentions.toString()));
        return messages;
    }

    private static TelegramMessage combine(TelegramMessage before, String mentions, TelegramMessage after) {
        var entities = new ArrayList<>(before.entities());
        int offset = before.text().length() + mentions.length();
        for (var entity : after.entities()) {
            entities.add(new TelegramMessage.Entity(entity.type(), entity.offset() + offset, entity.length()));
        }
        return new TelegramMessage(before.text() + mentions + after.text(), List.copyOf(entities));
    }

    private String withoutTags() {
        int lineStart = beforeTags.lastIndexOf('\n') + 1;
        int newline = afterTags.indexOf('\n');
        int lineEnd = newline < 0 ? afterTags.length() : newline;
        if (hasTags && beforeTags.substring(lineStart).isBlank() && afterTags.substring(0, lineEnd).isBlank()) {
            return (beforeTags.substring(0, lineStart) + afterTags.substring(Math.min(lineEnd + 1, afterTags.length()))).strip();
        }
        boolean separatesWords = WORD_AT_END.matcher(beforeTags).find() && WORD_AT_START.matcher(afterTags).find();
        return (beforeTags + (separatesWords ? " " : "") + afterTags).strip();
    }

    private static TelegramMessage format(String source, String serverName, String playerName) {
        var text = new StringBuilder();
        var entities = new ArrayList<TelegramMessage.Entity>();
        var styles = STYLES.matcher(source);
        int position = 0;
        while (styles.find()) {
            text.append(substitute(source.substring(position, styles.start()), serverName, playerName));
            for (int group = 1; group <= styles.groupCount(); group++) {
                if (styles.group(group) != null) {
                    String styled = substitute(styles.group(group), serverName, playerName);
                    if (group == 1) {
                        styled = styled.replaceFirst("^\\n", "").replaceFirst("\\n\\z", "");
                    }
                    if (!styled.isBlank()) {
                        entities.add(new TelegramMessage.Entity(STYLE_TYPES.get(group - 1), text.length(), styled.length()));
                    }
                    text.append(styled);
                    break;
                }
            }
            position = styles.end();
        }
        text.append(substitute(source.substring(position), serverName, playerName));
        return new TelegramMessage(text.toString(), List.copyOf(entities));
    }

    private static String substitute(String source, String serverName, String playerName) {
        return VALUES.matcher(source).replaceAll(match -> Matcher.quoteReplacement(
                match.group().equals("<ServerName>") ? serverName : playerName));
    }
}
