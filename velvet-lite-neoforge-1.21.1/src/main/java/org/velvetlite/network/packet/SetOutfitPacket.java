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
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlManager;

import java.util.UUID;

/** C2S: switch a girl's model variant (see {@link AbstractGirlEntity#getModelVariant()}). */
public record SetOutfitPacket(UUID girlId, int variant) implements CustomPacketPayload {

    public static final Type<SetOutfitPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "set_outfit"));

    public static final StreamCodec<FriendlyByteBuf, SetOutfitPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SetOutfitPacket::girlId,
                    ByteBufCodecs.VAR_INT, SetOutfitPacket::variant,
                    SetOutfitPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetOutfitPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.variant() < AbstractGirlEntity.VARIANT_NUDE || pkt.variant() > AbstractGirlEntity.VARIANT_DRESSED) return;
            GirlManager.allByUuid(pkt.girlId()).forEach(g -> {
                if (!GirlManager.canCommand(player, g) || g.getCurrentAction().isBusy()) return;
                if (pkt.variant() == AbstractGirlEntity.VARIANT_NUDE) g.scene().strip();
                else g.setModelVariant(pkt.variant());
            });
        });
    }
}
