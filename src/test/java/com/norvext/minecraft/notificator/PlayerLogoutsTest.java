package com.norvext.minecraft.notificator;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLogoutsTest {
    @Test
    void permitsJoinAtTheExactThreshold() {
        var logouts = new PlayerLogouts();
        var player = UUID.fromString("67539885-ad4e-468a-aaee-6f05c0da15f2");
        logouts.record(player, 1_000L);

        assertTrue(logouts.suppressJoin(player, 300_000L, 300));
        assertFalse(logouts.suppressJoin(player, 301_000L, 300));
    }
}
