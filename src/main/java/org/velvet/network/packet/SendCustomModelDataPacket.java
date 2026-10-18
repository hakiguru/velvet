package org.velvet.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

import java.util.UUID;

import static org.velvet.network.NetCodecs.VEC3_CODEC;

public record SendCustomModelDataPacket(Vec3 pos, UUID playerId) implements CustomPacketPayload {
    public static final Type<SendCustomModelDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "send_custom_model_data"));
    public static final StreamCodec<FriendlyByteBuf, SendCustomModelDataPacket> STREAM_CODEC =
            StreamCodec.composite(
                    VEC3_CODEC, SendCustomModelDataPacket::pos,
                    UUIDUtil.STREAM_CODEC, SendCustomModelDataPacket::playerId,
                    SendCustomModelDataPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SendCustomModelDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* TODO step 7 */ });
    }
}