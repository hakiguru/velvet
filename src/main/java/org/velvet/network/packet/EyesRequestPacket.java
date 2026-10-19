package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

/** To her owner's game: render what the NPC sees and send it back (for a language model that sees images). */
public record EyesRequestPacket(int request, int entityId) implements CustomPacketPayload {

    public static final Type<EyesRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "eyes_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EyesRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EyesRequestPacket::request,
            ByteBufCodecs.VAR_INT, EyesRequestPacket::entityId,
            EyesRequestPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EyesRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.velvet.client.EyeCamera.request(pkt.request(), pkt.entityId()));
    }
}
