package org.velvet.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.GirlManager;

import java.util.UUID;

import static org.velvet.network.NetCodecs.VEC3_CODEC;

public record SetHomePositionPacket(UUID girlId, Vec3 homePos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetHomePositionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "set_home_pos"));

    public static final StreamCodec<FriendlyByteBuf, SetHomePositionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, SetHomePositionPacket::girlId,
                    VEC3_CODEC,                        SetHomePositionPacket::homePos,
                    SetHomePositionPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetHomePositionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GirlManager.allByUuid(pkt.girlId()).stream().filter(g -> GirlManager.canCommand(player, g)).forEach(g ->
                    g.setHomePos(g.blockPosition())); // the client position is not trusted
        });
    }
}