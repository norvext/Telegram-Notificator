package com.norvext.minecraft.notificator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

final class TelegramClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern BOT_TOKEN = Pattern.compile("[0-9]+:[a-zA-Z0-9_-]+");
    private static final URI API = URI.create("https://api.telegram.org");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final URI apiBase;

    TelegramClient() {
        // Allow an integration server to exercise HTTP locally.
        String localApi = FMLEnvironment.isProduction() ? "" : System.getProperty("telegramnotificator.apiBase", "");
        apiBase = localApi.matches("http://127\\.0\\.0\\.1:[0-9]{1,5}") ? URI.create(localApi) : API;
    }

    static boolean validToken(String token) {
        return BOT_TOKEN.matcher(token).matches();
    }

    CompletableFuture<String> send(String token, Config.Destination destination, String text) {
        if (!validToken(token)) {
            return CompletableFuture.completedFuture("недопустимый формат токена");
        }

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", destination.chatId());
        if (destination.topicId() != 0) {
            body.addProperty("message_thread_id", destination.topicId());
        }
        body.addProperty("text", text);
        try {
            HttpRequest request = HttpRequest.newBuilder(apiBase.resolve("/bot" + token + "/sendMessage"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            var delivery = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            return delivery.copy().orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).handle((response, failure) -> {
                if (failure == null) {
                    return responseError(response, destination.topicId() != 0);
                }
                delivery.cancel(true);
                return transportError(failure);
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(transportError(failure));
        }
    }

    private static String responseError(HttpResponse<String> response, boolean hasTopic) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            return switch (status) {
                case 400 -> hasTopic
                        ? "HTTP 400: проверьте группу и тему уведомлений"
                        : "HTTP 400: проверьте группу уведомлений";
                case 401, 404 -> "HTTP " + status + ": проверьте токен";
                case 403 -> "HTTP 403: проверьте доступ бота к группе";
                case 429 -> "HTTP 429: превышен лимит запросов";
                default -> "HTTP " + status;
            };
        }
        try {
            if (JsonParser.parseString(response.body()) instanceof JsonObject body
                    && body.get("ok") != null && body.get("ok").isJsonPrimitive()
                    && body.get("ok").getAsJsonPrimitive().isBoolean()) {
                return body.get("ok").getAsBoolean() ? null : "Telegram отклонил запрос";
            }
        } catch (JsonParseException ignored) {
            // Telegram responses may contain credentials or user data.
        }
        return "некорректный ответ Telegram";
    }

    private static String transportError(Throwable failure) {
        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return "истекло время ожидания";
        }
        return "ошибка соединения (" + cause.getClass().getSimpleName() + ")";
    }
}
