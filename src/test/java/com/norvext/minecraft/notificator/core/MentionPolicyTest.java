package com.norvext.minecraft.notificator.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MentionPolicyTest {
    private static final UUID JOINED = new UUID(0, 1);
    private static final UUID ONLINE = new UUID(0, 2);
    private static final UUID OFFLINE = new UUID(0, 3);

    private static Map<UUID, String> bindings() {
        var bindings = new LinkedHashMap<UUID, String>();
        bindings.put(JOINED, "joined");
        bindings.put(ONLINE, "online");
        bindings.put(OFFLINE, "offline");
        return bindings;
    }

    @Test
    void neverMentionsTheJoiningPlayer() {
        var online = Set.of(JOINED, ONLINE);
        assertEquals(List.of("online", "offline"), MentionPolicy.ALL.recipients(bindings(), JOINED, online::contains));
        assertEquals(List.of("offline"), MentionPolicy.OFFLINE.recipients(bindings(), JOINED, online::contains));
        assertEquals(List.of(), MentionPolicy.NONE.recipients(bindings(), JOINED, online::contains));
    }

    @Test
    void parsesStoredValueWithoutRegardToCase() {
        assertEquals(MentionPolicy.OFFLINE, MentionPolicy.parse("offline"));
        assertEquals(MentionPolicy.NONE, MentionPolicy.parse("NONE"));
        assertEquals(MentionPolicy.ALL, MentionPolicy.parse("everyone"));
        assertEquals(MentionPolicy.ALL, MentionPolicy.parse(null));
    }
}
