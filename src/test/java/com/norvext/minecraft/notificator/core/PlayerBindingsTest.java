package com.norvext.minecraft.notificator.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerBindingsTest {
    private static final String FIRST = "67539885-ad4e-468a-aaee-6f05c0da15f2";
    private static final String SECOND = "67539885-ad4e-468a-aaee-6f05c0da15f3";

    @Test
    void acceptsOnlyTelegramUsernameCharacters() {
        assertTrue(PlayerBindings.validUsername("Chaika_42"));
        assertFalse(PlayerBindings.validUsername("@chaika"));
        assertFalse(PlayerBindings.validUsername(""));
        assertFalse(PlayerBindings.validUsername("a".repeat(33)));
    }

    @Test
    void keepsTheFirstBindingForAPlayerAndForAUsername() {
        var decoded = PlayerBindings.decode(List.of(
                FIRST + ":alice", FIRST + ":other", SECOND + ":ALICE", "garbage", 42));
        assertEquals(Map.of(UUID.fromString(FIRST), "alice"), decoded);
    }

    @Test
    void roundTripsThroughTheStoredForm() {
        var decoded = PlayerBindings.decode(List.of(FIRST + ":alice", SECOND + ":bob"));
        assertEquals(List.of(FIRST + ":alice", SECOND + ":bob"), PlayerBindings.encode(decoded));
        assertEquals(Map.of(), PlayerBindings.decode("not a list"));
    }
}
