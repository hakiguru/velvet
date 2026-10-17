package org.velvetlite.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.GirlManager;

import java.util.UUID;

public record SetGirlOwnerPacket(UUID girlId, UUID ownerId) implements CustomPacketPayload {

    public static final Type<SetGirlOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "set_girl_owner"));

    public static final StreamCodec<FriendlyByteBuf, SetGirlOwnerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SetGirlOwnerPacket::girlId,
                    UUIDUtil.STREAM_CODEC, SetGirlOwnerPacket::ownerId,
                    SetGirlOwnerPacket::new);

    /** Sent as ownerId to clear the owner (the codec can't carry null). */
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGirlOwnerPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // a player can only make a girl follow himself, and only if she isn't someone else's
            if (!NO_OWNER.equals(pkt.ownerId()) && !pkt.ownerId().equals(player.getUUID())) return;
            GirlManager.allByUuid(pkt.girlId()).forEach(g -> {
                if (GirlManager.canCommand(player, g)) g.setOwnerUUID(NO_OWNER.equals(pkt.ownerId()) ? null : pkt.ownerId());
            });
        });
    }
}