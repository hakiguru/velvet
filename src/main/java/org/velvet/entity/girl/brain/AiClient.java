package org.velvet.entity.girl.brain;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.velvet.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Talks to the language model services over HTTP, off the game thread. Two protocols cover all of them:
 * Anthropic's Messages API and OpenAI's chat completions (which Gemini, DeepSeek, Qwen, Grok, Mistral,
 * OpenRouter, Ollama and LM Studio all offer too). Errors come back as {@link AiException} with the
 * service's own message, never with the key.
 */
public final class AiClient {

    private AiClient() {}

    public record Message(String role, String content) {
        public static Message user(String text) {
            return new Message("user", text);
        }

        public static Message assistant(String text) {
            return new Message("assistant", text);
        }
    }

    public static final class AiException extends RuntimeException {
        public AiException(String message) {
            super(message);
        }
    }

    private static final Gson GSON = new Gson();
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Her answer to the conversation so far. */
    public static CompletableFuture<String> chat(AiProvider provider, String baseUrl, @Nullable String key, String model,
                                                 String system, List<Message> messages, int maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray list = new JsonArray();
        HttpRequest.Builder request;
        if (provider.protocol == AiProvider.Protocol.ANTHROPIC) {
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("system", system);
            for (Message m : messages) list.add(message(m.role(), m.content()));
            body.add("messages", list);
            request = request(baseUrl + "/messages").header("anthropic-version", ANTHROPIC_VERSION);
            if (key != null) request.header("x-api-key", key);
        } else {
            list.add(message("system", system));
            for (Message m : messages) list.add(message(m.role(), m.content()));
            body.add("messages", list);
            // OpenAI's newer models only take max_completion_tokens (and spend some on thinking)
            if (provider == AiProvider.CHATGPT) body.addProperty("max_completion_tokens", Math.max(maxTokens, 1024));
            else body.addProperty("max_tokens", maxTokens);
            request = request(baseUrl + "/chat/completions");
            if (key != null) request.header("Authorization", "Bearer " + key);
        }
        HttpRequest built = request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
        return HTTP.sendAsync(built, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            JsonObject json = parse(response);
            String text = provider.protocol == AiProvider.Protocol.ANTHROPIC ? anthropicText(json) : openAiText(json);
            if (text == null || text.isBlank()) throw new AiException("empty answer");
            return text.trim();
        });
    }

    /** The models the service offers (GET …/models, which every one of them has). */
    public static CompletableFuture<List<String>> listModels(AiProvider provider, String baseUrl, @Nullable String key) {
        HttpRequest.Builder request = request(baseUrl + "/models");
        if (provider.protocol == AiProvider.Protocol.ANTHROPIC) {
            request.header("anthropic-version", ANTHROPIC_VERSION);
            if (key != null) request.header("x-api-key", key);
        } else if (key != null) {
            request.header("Authorization", "Bearer " + key);
        }
        return HTTP.sendAsync(request.GET().build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            JsonObject json = parse(response);
            List<String> models = new ArrayList<>();
            JsonElement data = json.has("data") ? json.get("data") : json.get("models");
            if (data != null && data.isJsonArray()) {
                for (JsonElement e : data.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    JsonObject m = e.getAsJsonObject();
                    String id = m.has("id") ? m.get("id").getAsString() : m.has("name") ? m.get("name").getAsString() : null;
                    if (id != null) models.add(id.startsWith("models/") ? id.substring("models/".length()) : id);
                }
            }
            models.sort(String::compareTo);
            return models;
        });
    }

    /** A readable message for a failed request. */
    public static String describe(Throwable error) {
        Throwable e = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (e instanceof AiException) return e.getMessage();
        if (e instanceof java.net.http.HttpTimeoutException) return "timed out";
        if (e instanceof java.net.ConnectException) return "can't connect (is it running?)";
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    private static HttpRequest.Builder request(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new AiException("bad address: " + url);
        }
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) throw new AiException("bad address: " + url);
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(Config.AI_TIMEOUT_SECONDS.get()))
                .header("User-Agent", "Velvet");
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static JsonObject parse(HttpResponse<String> response) {
        JsonObject json;
        try {
            JsonElement root = JsonParser.parseString(response.body());
            json = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            json = new JsonObject();
        }
        if (response.statusCode() / 100 != 2) {
            String detail = errorText(json);
            throw new AiException("HTTP " + response.statusCode() + (detail != null ? ": " + detail : ""));
        }
        return json;
    }

    @Nullable
    private static String errorText(JsonObject json) {
        JsonElement error = json.get("error");
        if (error == null) return null;
        if (error.isJsonPrimitive()) return error.getAsString();
        if (error.isJsonObject() && error.getAsJsonObject().has("message")) return error.getAsJsonObject().get("message").getAsString();
        return null;
    }

    @Nullable
    private static String anthropicText(JsonObject json) {
        if (!json.has("content") || !json.get("content").isJsonArray()) return null;
        StringBuilder out = new StringBuilder();
        for (JsonElement e : json.getAsJsonArray("content")) {
            JsonObject block = e.getAsJsonObject();
            if (block.has("type") && "text".equals(block.get("type").getAsString())) out.append(block.get("text").getAsString());
        }
        return out.toString();
    }

    @Nullable
    private static String openAiText(JsonObject json) {
        if (!json.has("choices") || json.getAsJsonArray("choices").isEmpty()) return null;
        JsonObject message = json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content") || message.get("content").isJsonNull()) return null;
        return message.get("content").getAsString();
    }
}
