package org.velvet.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

public record HornyMeterPacket(float value, float max, int action) implements CustomPacketPayload {

    public static final Type<HornyMeterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "horny_meter"));

    public static final StreamCodec<FriendlyByteBuf, HornyMeterPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,    HornyMeterPacket::value,
                    ByteBufCodecs.FLOAT,    HornyMeterPacket::max,
                    ByteBufCodecs.VAR_INT,  HornyMeterPacket::action,
                    HornyMeterPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(HornyMeterPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                org.velvet.client.overlay.HornyMeterOverlay.update(pkt.value(), pkt.max(), pkt.action());
            }
        });
    }
}