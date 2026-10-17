package org.velvetlite.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;

import java.util.Optional;
import java.util.UUID;

public record SetDynamicModelPacket(UUID ownerId, UUID targetId) implements CustomPacketPayload {

    public static final Type<SetDynamicModelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "set_dynamic_model"));

    public static final StreamCodec<FriendlyByteBuf, SetDynamicModelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                    pkt -> Optional.ofNullable(pkt.ownerId()),
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                    pkt -> Optional.ofNullable(pkt.targetId()),
                    (o, t) -> new SetDynamicModelPacket(o.orElse(null), t.orElse(null)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SetDynamicModelPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* TODO step 7 */ });
    }
}