package org.velvet.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

public record SetFutaStatePacket(boolean enabled) implements CustomPacketPayload {
    public static final Type<SetFutaStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "set_futa_state"));
    public static final StreamCodec<FriendlyByteBuf, SetFutaStatePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SetFutaStatePacket::enabled, SetFutaStatePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SetFutaStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* TODO step 8 */ });
    }
}