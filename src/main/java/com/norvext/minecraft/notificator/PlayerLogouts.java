package com.norvext.minecraft.notificator;

import com.norvext.minecraft.notificator.core.RejoinFilter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PlayerLogouts extends SavedData {
    private static final String NAME = TelegramNotificator.MODID + "_player_logouts";
    private static final Factory<PlayerLogouts> FACTORY = new Factory<>(PlayerLogouts::new, PlayerLogouts::load);

    private final Map<UUID, Long> lastLogout = new HashMap<>();

    static PlayerLogouts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static PlayerLogouts load(CompoundTag tag, HolderLookup.Provider registries) {
        var logouts = new PlayerLogouts();
        for (String key : tag.getAllKeys()) {
            try {
                logouts.lastLogout.put(UUID.fromString(key), tag.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // Skip an entry that is not keyed by a player UUID.
            }
        }
        return logouts;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        lastLogout.forEach((player, loggedOutAt) -> tag.putLong(player.toString(), loggedOutAt));
        return tag;
    }

    boolean suppressJoin(UUID player, long joinedAt, int intervalSeconds) {
        return RejoinFilter.suppresses(lastLogout.get(player), joinedAt, intervalSeconds);
    }

    void record(UUID player, long loggedOutAt) {
        lastLogout.put(player, loggedOutAt);
        setDirty();
    }
}
