package org.velvetlite.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.skin.SkinFiles;
import org.velvetlite.skin.SkinTransfers;

/**
 * One piece of a skin file: to the server when uploading one from the editor, to a client that asked for
 * a skin it hasn't got.
 */
public record SkinChunkPacket(String hash, int index, int total, byte[] data) implements CustomPacketPayload {

    public static final Type<SkinChunkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "skin_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinChunkPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(40), SkinChunkPacket::hash,
            ByteBufCodecs.VAR_INT, SkinChunkPacket::index,
            ByteBufCodecs.VAR_INT, SkinChunkPacket::total,
            ByteBufCodecs.byteArray(SkinFiles.CHUNK), SkinChunkPacket::data,
            SkinChunkPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkinChunkPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                org.velvetlite.client.skin.SkinCache.receive(pkt.hash(), pkt.index(), pkt.total(), pkt.data());
            } else if (ctx.player() instanceof ServerPlayer player) {
                SkinTransfers.receiveUpload(player, pkt.hash(), pkt.index(), pkt.total(), pkt.data());
            }
        });
    }
}
