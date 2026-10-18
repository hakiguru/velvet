package org.velvetlite.skin;

import net.minecraft.server.level.ServerPlayer;
import org.velvetlite.VelvetLite;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.SkinChunkPacket;
import org.velvetlite.network.packet.SkinUploadResultPacket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of moving skin files: putting together uploads from the editor (one at a time per player,
 * checked against their hash and the size limits) and sending stored skins to clients in pieces.
 */
public final class SkinTransfers {

    private SkinTransfers() {}

    private static final int MAX_CHUNKS = SkinFiles.MAX_BYTES / SkinFiles.CHUNK + 1;

    private static final class Upload {
        final String hash;
        final byte[][] chunks;
        int received;

        Upload(String hash, int total) {
            this.hash = hash;
            this.chunks = new byte[total][];
        }
    }

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    public static void receiveUpload(ServerPlayer player, String hash, int index, int total, byte[] data) {
        if (!GirlSkin.isHash(hash) || total <= 0 || total > MAX_CHUNKS || index < 0 || index >= total) return;
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || !upload.hash.equals(hash) || upload.chunks.length != total) {
            upload = new Upload(hash, total);
            UPLOADS.put(player.getUUID(), upload);
        }
        if (upload.chunks[index] == null) upload.received++;
        upload.chunks[index] = data;
        if (upload.received < total) return;
        UPLOADS.remove(player.getUUID());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] chunk : upload.chunks) out.writeBytes(chunk);
        byte[] file = out.toByteArray();
        String error = SkinFiles.problem(file);
        if (error == null && !SkinFiles.sha1(file).equals(hash)) error = "the file arrived damaged";
        if (error == null) {
            try {
                SkinStore.save(player.server, file);
            } catch (IOException e) {
                VelvetLite.LOGGER.error("Couldn't store skin {}", hash, e);
                error = "the server couldn't store it";
            }
        }
        NetworkHandler.sendToPlayer(player, new SkinUploadResultPacket(hash, error == null ? "" : error));
    }

    public static void sendTo(ServerPlayer player, String hash) {
        byte[] file = SkinStore.load(player.server, hash);
        if (file == null) return;
        int total = Math.max(1, Math.ceilDiv(file.length, SkinFiles.CHUNK));
        for (int i = 0; i < total; i++) {
            int from = i * SkinFiles.CHUNK;
            byte[] chunk = java.util.Arrays.copyOfRange(file, from, Math.min(file.length, from + SkinFiles.CHUNK));
            NetworkHandler.sendToPlayer(player, new SkinChunkPacket(hash, i, total, chunk));
        }
    }

    public static void forget(ServerPlayer player) {
        UPLOADS.remove(player.getUUID());
    }
}
