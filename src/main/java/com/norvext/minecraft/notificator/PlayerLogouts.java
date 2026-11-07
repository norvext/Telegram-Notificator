package com.norvext.minecraft.notificator;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class PlayerLogouts extends SavedData {
    private static final SavedDataType<PlayerLogouts> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TelegramNotificator.MODID, "player_logouts"),
            PlayerLogouts::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                    .xmap(PlayerLogouts::new, logouts -> logouts.lastLogout));

    private final Map<UUID, Long> lastLogout;

    PlayerLogouts() {
        this(Map.of());
    }

    private PlayerLogouts(Map<UUID, Long> lastLogout) {
        this.lastLogout = new HashMap<>(lastLogout);
    }

    static PlayerLogouts get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    boolean suppressJoin(UUID player, long joinedAt, int intervalSeconds) {
        Long last = lastLogout.get(player);
        return intervalSeconds > 0 && last != null
                && joinedAt - last < TimeUnit.SECONDS.toMillis(intervalSeconds);
    }

    void record(UUID player, long loggedOutAt) {
        lastLogout.put(player, loggedOutAt);
        setDirty();
    }
}
