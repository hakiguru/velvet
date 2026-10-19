package org.velvet.entity.girl.brain;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.EyesRequestPacket;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * What an NPC sees, for language models that look at pictures. The server can't render anything, so her
 * owner's game does it: it draws the world from her eyes into a small picture (see EyeCamera) and sends it
 * back as a JPEG. Without her owner close enough to have her in view, or if it takes too long, the model
 * just doesn't get a picture this time.
 */
public final class EyesService {

    private EyesService() {}

    private static final long TIMEOUT_MS = 2500;
    private static final int MAX_BYTES = 400_000;
    private static final double RANGE = 96;

    private record Pending(UUID from, CompletableFuture<String> result, ByteArrayOutputStream bytes) {}

    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();
    private static int next;

    /** The picture as base64 JPEG, or null. */
    public static CompletableFuture<String> request(AbstractGirlEntity girl) {
        ServerPlayer viewer = viewer(girl);
        if (viewer == null) return CompletableFuture.completedFuture(null);
        int id = ++next;
        CompletableFuture<String> result = new CompletableFuture<String>().completeOnTimeout(null, TIMEOUT_MS, TimeUnit.MILLISECONDS);
        PENDING.put(id, new Pending(viewer.getUUID(), result, new ByteArrayOutputStream()));
        result.whenComplete((r, e) -> PENDING.remove(id));
        NetworkHandler.sendToPlayer(viewer, new EyesRequestPacket(id, girl.getId()));
        return result;
    }

    @Nullable
    private static ServerPlayer viewer(AbstractGirlEntity girl) {
        if (girl.getOwnerUUID() == null || girl.getServer() == null) return null;
        ServerPlayer owner = girl.getServer().getPlayerList().getPlayer(girl.getOwnerUUID());
        return owner != null && owner.level() == girl.level() && owner.distanceTo(girl) < RANGE ? owner : null;
    }

    public static void receive(ServerPlayer from, int id, int index, int total, byte[] data) {
        Pending p = PENDING.get(id);
        if (p == null || !p.from.equals(from.getUUID()) || total <= 0) return;
        synchronized (p) {
            if (p.bytes.size() + data.length > MAX_BYTES) {
                p.result.complete(null);
                return;
            }
            p.bytes.writeBytes(data);
            if (index == total - 1) p.result.complete(Base64.getEncoder().encodeToString(p.bytes.toByteArray()));
        }
    }
}
