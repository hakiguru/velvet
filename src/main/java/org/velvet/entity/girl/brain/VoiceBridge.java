package org.velvet.entity.girl.brain;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.velvet.Config;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Her voice, when a voice chat mod is there to carry it (Simple Voice Chat, see
 * {@code org.velvet.compat.voicechat}): what players say near her becomes text she hears like chat, and what
 * she answers is spoken out loud from where she stands. Speech to text and text to speech go through an
 * OpenAI-compatible speech service with her owner's ChatGPT key ({@link Config#VOICE_URL}). Without the
 * voice chat mod nothing here does anything.
 */
public final class VoiceBridge {

    private VoiceBridge() {}

    /** What the voice chat mod does for us; set by its plugin once its server is up. */
    public interface Backend {
        /** Plays 48 kHz mono 16-bit samples from her. */
        void play(AbstractGirlEntity girl, short[] samples);
    }

    @Nullable private static volatile Backend backend;

    public static void setBackend(@Nullable Backend b) {
        backend = b;
    }

    public static boolean available() {
        return backend != null;
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** 48 kHz: a voice chat frame is 20 ms of it. */
    public static final int RATE = 48000;
    /** A pause this long ends what someone said. */
    private static final long PAUSE_MS = 800;
    /** Nobody talks longer than this in one go (and it keeps requests small). */
    private static final int MAX_SAMPLES = RATE * 12;

    private static final class Speech {
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        long last;
        int samples;
    }

    private static final Map<UUID, Speech> SPEAKING = new ConcurrentHashMap<>();

    // ---- Speaking ----

    /** Says {@code text} out loud from her, if she's set to and the voice chat is there. */
    public static void speak(AbstractGirlEntity girl, String text) {
        Backend b = backend;
        if (b == null || !girl.behavior().voiceSpeak) return;
        String key = key(girl);
        if (key == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("model", Config.VOICE_TTS_MODEL.get());
        body.addProperty("voice", girl.behavior().ttsVoice);
        body.addProperty("input", text);
        body.addProperty("response_format", "pcm");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/audio/speech")))
                .timeout(Duration.ofSeconds(Config.AI_TIMEOUT_SECONDS.get()))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
            if (response.statusCode() / 100 != 2) {
                Velvet.LOGGER.warn("Text to speech failed: HTTP {}", response.statusCode());
                return;
            }
            // 24 kHz 16-bit little endian from the service, doubled up to the voice chat's 48 kHz
            short[] in = toShorts(response.body());
            short[] out = new short[in.length * 2];
            for (int i = 0; i < in.length; i++) {
                short next = i + 1 < in.length ? in[i + 1] : in[i];
                out[2 * i] = in[i];
                out[2 * i + 1] = (short) ((in[i] + next) / 2);
            }
            Backend now = backend;
            if (now != null && girl.isAlive()) now.play(girl, out);
        }).exceptionally(e -> {
            Velvet.LOGGER.warn("Text to speech failed: {}", AiClient.describe(e));
            return null;
        });
    }

    // ---- Hearing ----

    /** 20 ms of a player's microphone (48 kHz mono), from the voice chat; on the voice chat's thread. */
    public static void onMicrophone(UUID player, short[] samples) {
        Speech s = SPEAKING.computeIfAbsent(player, id -> new Speech());
        synchronized (s) {
            if (s.samples >= MAX_SAMPLES) return;
            ByteBuffer buf = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short v : samples) buf.putShort(v);
            s.pcm.writeBytes(buf.array());
            s.samples += samples.length;
            s.last = System.currentTimeMillis();
        }
    }

    /** Every tick on the server: whoever paused long enough has said something. */
    public static void tick(MinecraftServer server) {
        if (backend == null || SPEAKING.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var e : SPEAKING.entrySet()) {
            Speech s = e.getValue();
            byte[] pcm;
            synchronized (s) {
                if (now - s.last < PAUSE_MS) continue;
                pcm = s.pcm.toByteArray();
            }
            SPEAKING.remove(e.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            // half a second or less is a cough, not a sentence
            if (player == null || pcm.length < RATE) continue;
            AbstractGirlEntity listener = listenerFor(player);
            if (listener != null) transcribe(server, player, listener, pcm);
        }
    }

    /** The girl who'd hear him: hers first, then the closest one listening. */
    @Nullable
    private static AbstractGirlEntity listenerFor(ServerPlayer player) {
        AbstractGirlEntity best = null;
        double bestRank = Double.MAX_VALUE, range = Config.VOICE_DISTANCE.get();
        for (AbstractGirlEntity girl : GirlManager.all()) {
            if (girl.level() != player.level() || !girl.isAlive() || !girl.behavior().voiceListen) continue;
            double d = girl.distanceTo(player);
            if (d > range) continue;
            double rank = d - (player.getUUID().equals(girl.getOwnerUUID()) ? 1000 : 0);
            if (rank < bestRank) {
                bestRank = rank;
                best = girl;
            }
        }
        return best;
    }

    private static void transcribe(MinecraftServer server, ServerPlayer player, AbstractGirlEntity girl, byte[] pcm) {
        String key = key(girl);
        if (key == null) return;
        String boundary = "velvet" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n" + Config.VOICE_STT_MODEL.get()
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        body.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        body.writeBytes(wav(pcm));
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/audio/transcriptions")))
                .timeout(Duration.ofSeconds(Config.AI_TIMEOUT_SECONDS.get()))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        CompletableFuture<HttpResponse<String>> sent = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        sent.thenAccept(response -> {
            if (response.statusCode() / 100 != 2) {
                Velvet.LOGGER.warn("Speech to text failed: HTTP {}", response.statusCode());
                return;
            }
            String text;
            try {
                text = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject().get("text").getAsString().trim();
            } catch (RuntimeException e) {
                return;
            }
            if (text.isEmpty()) return;
            server.execute(() -> {
                if (!girl.isAlive() || player.hasDisconnected()) return;
                boolean addressed = ChatListener.addressedTo(girl.getName().getString(), text) != null;
                girl.brain().hear(player, text, addressed || player.getUUID().equals(girl.getOwnerUUID()));
            });
        }).exceptionally(e -> {
            Velvet.LOGGER.warn("Speech to text failed: {}", AiClient.describe(e));
            return null;
        });
    }

    @Nullable
    private static String key(AbstractGirlEntity girl) {
        MinecraftServer server = girl.getServer();
        if (server == null || girl.getOwnerUUID() == null) return null;
        return AiKeys.get(server).key(girl.getOwnerUUID(), AiProvider.CHATGPT);
    }

    private static String url(String path) {
        String base = Config.VOICE_URL.get().trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    private static short[] toShorts(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[bytes.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = buf.getShort();
        return out;
    }

    /** 48 kHz mono 16-bit PCM in a WAV file. */
    private static byte[] wav(byte[] pcm) {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        h.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(h.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }
}
