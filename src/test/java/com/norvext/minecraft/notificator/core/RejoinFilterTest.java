package com.norvext.minecraft.notificator.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejoinFilterTest {
    @Test
    void permitsJoinAtTheExactThreshold() {
        assertTrue(RejoinFilter.suppresses(1_000L, 300_000L, 300));
        assertTrue(RejoinFilter.suppresses(1_000L, 300_999L, 300));
        assertFalse(RejoinFilter.suppresses(1_000L, 301_000L, 300));
    }

    @Test
    void permitsFirstJoinAndDisabledFilter() {
        assertFalse(RejoinFilter.suppresses(null, 1_000L, 300));
        assertFalse(RejoinFilter.suppresses(1_000L, 1_001L, 0));
    }

    @Test
    void leaveBecomesDueExactlyWhenJoinStopsBeingSuppressed() {
        long due = RejoinFilter.quietUntil(1_000L, 300);
        assertEquals(301_000L, due);
        assertTrue(RejoinFilter.suppresses(1_000L, due - 1, 300));
        assertFalse(RejoinFilter.suppresses(1_000L, due, 300));
        assertEquals(1_000L, RejoinFilter.quietUntil(1_000L, 0));
    }
}
