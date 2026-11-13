package com.norvext.minecraft.notificator.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTemplateTest {
    private static final MessageTemplate.Values VALUES = new MessageTemplate.Values("Immortal", "Alex", 3, 20);

    private static MessageTemplate join(String source) {
        return new MessageTemplate(source, true, true, "Immortal");
    }

    @Test
    void substitutesValuesAndDropsTheUnusedMentionLine() {
        var messages = join("<PlayerName> вошёл на сервер <ServerName> (<Online>/<MaxPlayers>)\n<TgTags>").render(VALUES);
        assertEquals(List.of(new TelegramMessage("Alex вошёл на сервер Immortal (3/20)")), messages);
    }

    @Test
    void placesMentionsWhereTheTemplateAsks() {
        var messages = join("<PlayerName> в игре\n<TgTags>").render(VALUES, List.of("alice", "bob"));
        assertEquals(List.of(new TelegramMessage("Alex в игре\n@alice @bob")), messages);
    }

    @Test
    void reportsStylesAsEntitiesOverSubstitutedText() {
        var message = join("**<PlayerName>** на `<ServerName>`").render(VALUES).get(0);
        assertEquals("Alex на Immortal", message.text());
        assertEquals(List.of(new TelegramMessage.Entity("bold", 0, 4), new TelegramMessage.Entity("code", 8, 8)),
                message.entities());
    }

    @Test
    void keepsMarkupInsideSubstitutedValuesLiteral() {
        var values = new MessageTemplate.Values("Immortal", "_Alex_", 1, 20);
        var message = join("<PlayerName> вошёл").render(values).get(0);
        assertEquals("_Alex_ вошёл", message.text());
        assertTrue(message.entities().isEmpty());
    }

    @Test
    void splitsMentionsThatDoNotFitIntoOneMessage() {
        List<String> usernames = IntStream.range(0, 400).mapToObj(index -> "user_with_long_name_" + index).toList();
        var messages = join("<PlayerName> вошёл\n<TgTags>").render(VALUES, usernames);
        assertTrue(messages.size() > 1);
        assertTrue(messages.get(0).text().startsWith("Alex вошёл\n@user_with_long_name_0 "));
        long mentioned = messages.stream().flatMap(message -> message.text().lines())
                .flatMap(line -> List.of(line.split(" ")).stream()).filter(word -> word.startsWith("@")).count();
        assertEquals(usernames.size(), mentioned);
        messages.forEach(message -> assertTrue(message.text().length() <= TelegramMessage.MAX_LENGTH));
    }

    @Test
    void rejectsTemplatesTheEventCannotFill() {
        assertThrows(IllegalArgumentException.class, () -> new MessageTemplate("<PlayerName>", false, false, "Immortal"));
        assertThrows(IllegalArgumentException.class, () -> new MessageTemplate("Тест <TgTags>", false, false, "Immortal"));
        assertThrows(IllegalArgumentException.class, () -> join("<TgTags> и <TgTags>"));
        assertThrows(IllegalArgumentException.class, () -> join("**<TgTags>**"));
        assertThrows(IllegalArgumentException.class, () -> join("   "));
    }
}
