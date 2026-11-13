package com.norvext.minecraft.notificator;

import com.mojang.logging.LogUtils;
import com.norvext.minecraft.notificator.core.MessageTemplate;
import com.norvext.minecraft.notificator.core.RejoinFilter;
import com.norvext.minecraft.notificator.core.TelegramMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TelegramNotifications {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record PendingLeave(String playerName, long dueAt) {}

    private final Config config;
    private final TelegramClient telegram = new TelegramClient();
    // A leave is announced only after the rejoin interval, so a quick rejoin produces no messages at all.
    private final Map<UUID, PendingLeave> pendingLeaves = new HashMap<>();
    private boolean stopping;

    TelegramNotifications(Config config) {
        this.config = config;
    }

    void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (PlayerLogouts.get(server).suppressJoin(player.getUUID(), System.currentTimeMillis(), config.rejoinSeconds())) {
            pendingLeaves.remove(player.getUUID());
            return;
        }
        PendingLeave left = pendingLeaves.remove(player.getUUID());
        if (left != null) {
            // The player is already counted as online again; the earlier leave happened without them.
            announceLeave(server, left, server.getPlayerCount() - 1);
        }
        if (config.isConfigured()) {
            var settings = config.notifications();
            var players = server.getPlayerList();
            send("вход игрока", settings.join().render(
                    values(server, player.getGameProfile().getName(), server.getPlayerCount()),
                    config.mentionPolicy().recipients(config.bindings(), player.getUUID(),
                            other -> players.getPlayer(other) != null)));
        }
    }

    void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (stopping || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerLogouts.get(player.server).record(player.getUUID(), now);
        if (config.notifications().notifyOnLeave()) {
            pendingLeaves.put(player.getUUID(), new PendingLeave(player.getGameProfile().getName(),
                    RejoinFilter.quietUntil(now, config.rejoinSeconds())));
        }
    }

    void onServerTick(ServerTickEvent.Post event) {
        if (pendingLeaves.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        MinecraftServer server = event.getServer();
        pendingLeaves.values().removeIf(left -> {
            if (now < left.dueAt()) {
                return false;
            }
            announceLeave(server, left, server.getPlayerCount());
            return true;
        });
    }

    private void announceLeave(MinecraftServer server, PendingLeave left, int online) {
        var settings = config.notifications();
        if (settings.notifyOnLeave() && config.isConfigured()) {
            send("выход игрока", settings.leave().render(values(server, left.playerName(), online)));
        }
    }

    void onServerStarted(ServerStartedEvent event) {
        var settings = config.notifications();
        if (!config.isConfigured()) {
            LOGGER.info("Telegram Notificator не настроен. Выполните в консоли: telegram token set <токен>, "
                    + "затем telegram chat set <chat_id> [topic_id]");
        } else if (settings.notifyOnStartup()) {
            send("запуск сервера", settings.startup().render(values(event.getServer(), "", 0),
                    List.copyOf(config.bindings().values())));
        }
    }

    void onServerStopping(ServerStoppingEvent event) {
        // PlayerList removes online players after this event, producing further logout events.
        stopping = true;
        pendingLeaves.clear();
        long stoppedAt = System.currentTimeMillis();
        var server = event.getServer();
        var logouts = PlayerLogouts.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            logouts.record(player.getUUID(), stoppedAt);
        }
    }

    CompletableFuture<Optional<String>> sendTest(MinecraftServer server) {
        return send("проверка", config.notifications().test().render(values(server, "", server.getPlayerCount())));
    }

    private MessageTemplate.Values values(MinecraftServer server, String playerName, int online) {
        return new MessageTemplate.Values(config.notifications().serverName(), playerName,
                Math.max(online, 0), server.getMaxPlayers());
    }

    private CompletableFuture<Optional<String>> send(String event, List<TelegramMessage> messages) {
        String token = config.token();
        Config.Destination destination = config.destination();
        var result = CompletableFuture.completedFuture(Optional.<String>empty());
        for (TelegramMessage message : messages) {
            result = result.thenCompose(error -> error.isPresent()
                    ? CompletableFuture.completedFuture(error)
                    : telegram.send(token, destination, message).thenApply(deliveryError -> {
                        if (deliveryError.isEmpty()) {
                            LOGGER.info("Сообщение Telegram отправлено ({})", event);
                        }
                        return deliveryError;
                    }));
        }
        return result.thenApply(error -> {
            error.ifPresent(reason -> LOGGER.warn("Не удалось отправить сообщение Telegram: {}", reason));
            return error;
        });
    }
}
