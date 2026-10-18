package org.velvet.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.SkinChunkPacket;
import org.velvet.network.packet.SkinRequestPacket;
import org.velvet.skin.GirlSkin;
import org.velvet.skin.SkinFiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client side: custom skins as textures, by hash. A skin nobody has sent yet is asked for once from the
 * server; until it's there the girl keeps her own texture. Skins picked in the editor are loaded from the
 * file right away (for the preview) and uploaded in the background.
 */
public final class SkinCache {

    private SkinCache() {}

    private static final class Entry {
        @Nullable ResourceLocation texture;
        byte[][] chunks;
        int received;
        boolean failed;
    }

    private static final Map<String, Entry> SKINS = new HashMap<>();
    @Nullable private static BiConsumer<String, String> uploadListener;

    /** The texture of a skin, or null while it's still on its way (or broken). */
    @Nullable
    public static ResourceLocation texture(GirlSkin skin) {
        Entry e = SKINS.get(skin.hash());
        if (e == null) {
            SKINS.put(skin.hash(), new Entry());
            NetworkHandler.sendToServer(new SkinRequestPacket(skin.hash()));
            return null;
        }
        return e.texture;
    }

    public static void receive(String hash, int index, int total, byte[] data) {
        Entry e = SKINS.computeIfAbsent(hash, h -> new Entry());
        if (e.texture != null || e.failed || total <= 0 || index < 0 || index >= total) return;
        if (e.chunks == null || e.chunks.length != total) {
            e.chunks = new byte[total][];
            e.received = 0;
        }
        if (e.chunks[index] == null) e.received++;
        e.chunks[index] = data;
        if (e.received < total) return;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : e.chunks) out.writeBytes(chunk);
        e.chunks = null;
        register(hash, out.toByteArray(), e);
    }

    /**
     * A file picked in the editor: checked, shown at once and sent to the server. Returns its hash, or throws
     * with what's wrong with it.
     */
    public static String loadLocal(byte[] data) throws IOException {
        String problem = SkinFiles.problem(data);
        if (problem != null) throw new IOException(problem);
        String hash = SkinFiles.sha1(data);
        Entry e = SKINS.computeIfAbsent(hash, h -> new Entry());
        if (e.texture == null) {
            e.failed = false;
            register(hash, data, e);
            if (e.texture == null) throw new IOException("not a readable PNG image");
        }
        int total = Math.max(1, Math.ceilDiv(data.length, SkinFiles.CHUNK));
        for (int i = 0; i < total; i++) {
            int from = i * SkinFiles.CHUNK;
            NetworkHandler.sendToServer(new SkinChunkPacket(hash, i, total,
                    java.util.Arrays.copyOfRange(data, from, Math.min(data.length, from + SkinFiles.CHUNK))));
        }
        return hash;
    }

    /** The editor hears when an upload is stored (error empty) or refused. */
    public static void setUploadListener(@Nullable BiConsumer<String, String> listener) {
        uploadListener = listener;
    }

    public static void uploadResult(String hash, String error) {
        if (uploadListener != null) uploadListener.accept(hash, error);
    }

    private static void register(String hash, byte[] data, Entry e) {
        try {
            NativeImage image = NativeImage.read(data);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "skins/" + hash);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            e.texture = id;
        } catch (IOException | RuntimeException ex) {
            Velvet.LOGGER.warn("Skin {} isn't a readable image: {}", hash, ex.getMessage());
            e.failed = true;
        }
    }

    /** Leaving a world: the next one has its own skins. */
    public static void clear() {
        for (Entry e : SKINS.values()) {
            if (e.texture != null) Minecraft.getInstance().getTextureManager().release(e.texture);
        }
        SKINS.clear();
    }
}
