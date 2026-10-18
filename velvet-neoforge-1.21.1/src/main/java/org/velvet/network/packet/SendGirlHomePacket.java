package org.velvet.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.GirlManager;

import java.util.UUID;

public record SendGirlHomePacket(UUID girlId) implements CustomPacketPayload {

    public static final Type<SendGirlHomePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "send_girl_home"));

    public static final StreamCodec<FriendlyByteBuf, SendGirlHomePacket> STREAM_CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, SendGirlHomePacket::girlId, SendGirlHomePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SendGirlHomePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GirlManager.allByUuid(pkt.girlId()).stream().filter(g -> GirlManager.canCommand(player, g)).forEach(g -> {
                if (g.getHomePos() != null) {
                    g.setFollowing(false); // she stays home once there
                    g.setShouldBeAtTarget(true);
                    g.setTargetVec(net.minecraft.world.phys.Vec3.atCenterOf(g.getHomePos()));
                }
            });
        });
    }
}