package com.norvext.minecraft.notificator;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class TelegramNotifications {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Config config;
    private final TelegramClient telegram = new TelegramClient();
    private boolean stopping;

    TelegramNotifications(Config config) {
        this.config = config;
    }

    void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !PlayerLogouts.get(player.level().getServer()).suppressJoin(
                        player.getUUID(), System.currentTimeMillis(), config.rejoinSeconds())
                && isConfigured()) {
            send("вход игрока", joinMessages(player));
        }
    }

    private List<String> joinMessages(ServerPlayer player) {
        String header = player.getGameProfile().name() + " вошёл на сервер Minecraft";
        var messages = new ArrayList<String>();
        var text = new StringBuilder(header);
        char separator = '\n';
        for (String username : recipients(player)) {
            if (text.length() + 2 + username.length() > TelegramClient.MAX_TEXT_LENGTH) {
                messages.add(text.toString());
                text.setLength(0);
            }
            if (!text.isEmpty()) {
                text.append(separator);
            }
            text.append('@').append(username);
            separator = ' ';
        }
        messages.add(text.toString());
        return messages;
    }

    private List<String> recipients(ServerPlayer player) {
        var policy = config.mentionPolicy();
        if (policy == Config.MentionPolicy.NONE) {
            return List.of();
        }
        var players = player.level().getServer().getPlayerList();
        return config.bindings().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(player.getUUID()))
                .filter(entry -> policy == Config.MentionPolicy.ALL || players.getPlayer(entry.getKey()) == null)
                .map(Map.Entry::getValue)
                .toList();
    }

    void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!stopping && event.getEntity() instanceof ServerPlayer player) {
            PlayerLogouts.get(player.level().getServer()).record(player.getUUID(), System.currentTimeMillis());
        }
    }

    void onServerStopping(ServerStoppingEvent event) {
        // PlayerList removes online players after this event, producing further logout events.
        stopping = true;
        long stoppedAt = System.currentTimeMillis();
        var server = event.getServer();
        var logouts = PlayerLogouts.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            logouts.record(player.getUUID(), stoppedAt);
        }
    }

    CompletableFuture<Optional<String>> sendTest() {
        return send("проверка", List.of("Пробное сообщение от Minecraft"));
    }

    boolean isConfigured() {
        return !config.token().isEmpty() && config.destination().chatId() != 0;
    }

    private CompletableFuture<Optional<String>> send(String event, List<String> messages) {
        String token = config.token();
        Config.Destination destination = config.destination();
        var result = CompletableFuture.completedFuture(Optional.<String>empty());
        for (String text : messages) {
            result = result.thenCompose(error -> error.isPresent()
                    ? CompletableFuture.completedFuture(error)
                    : telegram.send(token, destination, text).thenApply(deliveryError -> {
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
