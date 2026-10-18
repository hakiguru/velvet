package org.velvetlite.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.GirlManager;

import java.util.UUID;

/** C2S: "Follow me" / "Stop following" from her menu; she only ever follows her owner. */
public record SetFollowingPacket(UUID girlId, boolean following) implements CustomPacketPayload {

    public static final Type<SetFollowingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "set_following"));

    public static final StreamCodec<FriendlyByteBuf, SetFollowingPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SetFollowingPacket::girlId,
                    ByteBufCodecs.BOOL, SetFollowingPacket::following,
                    SetFollowingPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetFollowingPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GirlManager.allByUuid(pkt.girlId()).forEach(g -> {
                if (!GirlManager.canCommand(player, g) || !player.getUUID().equals(g.getOwnerUUID())) return;
                g.setFollowing(pkt.following());
                if (pkt.following()) g.setShouldBeAtTarget(false); // called back on the way home
            });
        });
    }
}
