package com.norvext.minecraft.notificator.core;

import java.util.concurrent.TimeUnit;

public final class RejoinFilter {
    private RejoinFilter() {}

    /** A join is hidden only when it happens strictly earlier than the interval after the last logout. */
    public static boolean suppresses(Long lastLogout, long joinedAt, int intervalSeconds) {
        return intervalSeconds > 0 && lastLogout != null && joinedAt < quietUntil(lastLogout, intervalSeconds);
    }

    /** The moment from which a logout at {@code loggedOutAt} no longer hides the next join. */
    public static long quietUntil(long loggedOutAt, int intervalSeconds) {
        return loggedOutAt + TimeUnit.SECONDS.toMillis(Math.max(intervalSeconds, 0));
    }
}
