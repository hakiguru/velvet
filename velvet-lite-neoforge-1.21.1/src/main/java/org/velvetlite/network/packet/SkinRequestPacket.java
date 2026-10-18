package org.velvetlite.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.skin.SkinTransfers;

/** C2S: send me this skin (a girl near me wears it). */
public record SkinRequestPacket(String hash) implements CustomPacketPayload {

    public static final Type<SkinRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "skin_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(40), SkinRequestPacket::hash, SkinRequestPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkinRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) SkinTransfers.sendTo(player, pkt.hash());
        });
    }
}
