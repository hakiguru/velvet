package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.network.NetworkHandler;
import org.velvet.preset.PresetStore;

import java.util.Arrays;

/**
 * One piece of a custom preset's file: to the server when it's saved in the editor, to every client when the
 * server has it (on joining, and when it changes). An empty file means it was removed.
 */
public record PresetFilePacket(String preset, String path, int index, int total, byte[] data) implements CustomPacketPayload {

    /** Packets to the server can't be big; to clients they can be bigger. */
    public static final int TO_SERVER = 30000, TO_CLIENT = 256 * 1024;

    public static final Type<PresetFilePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "preset_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PresetFilePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PresetFilePacket::preset,
            ByteBufCodecs.stringUtf8(64), PresetFilePacket::path,
            ByteBufCodecs.VAR_INT, PresetFilePacket::index,
            ByteBufCodecs.VAR_INT, PresetFilePacket::total,
            ByteBufCodecs.byteArray(TO_CLIENT), PresetFilePacket::data,
            PresetFilePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Server: the whole file to a player, in pieces. */
    public static void send(ServerPlayer player, String preset, String path, byte[] bytes) {
        int total = Math.max(1, (bytes.length + TO_CLIENT - 1) / TO_CLIENT);
        for (int i = 0; i < total; i++) {
            NetworkHandler.sendToPlayer(player, new PresetFilePacket(preset, path, i, total,
                    Arrays.copyOfRange(bytes, i * TO_CLIENT, Math.min(bytes.length, (i + 1) * TO_CLIENT))));
        }
    }

    /** Client: the whole file to the server, in pieces. */
    public static void upload(String preset, String path, byte[] bytes) {
        int total = Math.max(1, (bytes.length + TO_SERVER - 1) / TO_SERVER);
        for (int i = 0; i < total; i++) {
            NetworkHandler.sendToServer(new PresetFilePacket(preset, path, i, total,
                    Arrays.copyOfRange(bytes, i * TO_SERVER, Math.min(bytes.length, (i + 1) * TO_SERVER))));
        }
    }

    public static void handle(PresetFilePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                org.velvet.client.preset.PresetCache.receive(pkt.preset(), pkt.path(), pkt.index(), pkt.total(), pkt.data());
            } else if (ctx.player() instanceof ServerPlayer player && pkt.data().length <= TO_SERVER) {
                PresetStore.receive(player, pkt.preset(), pkt.path(), pkt.index(), pkt.total(), pkt.data());
            }
        });
    }
}
