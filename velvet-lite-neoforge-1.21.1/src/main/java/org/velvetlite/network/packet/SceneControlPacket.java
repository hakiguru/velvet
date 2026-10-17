package org.velvetlite.network.packet;

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

/** C2S: the scene partner holds or releases the speed key, or leaves the scene. */
public record SceneControlPacket(Control control) implements CustomPacketPayload {

    public enum Control { STOP, FAST_ON, FAST_OFF }

    public static final Type<SceneControlPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "scene_control"));

    public static final StreamCodec<FriendlyByteBuf, SceneControlPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Control.values()[Math.floorMod(i, Control.values().length)], Control::ordinal),
            SceneControlPacket::control, SceneControlPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SceneControlPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // only the girl this player is partnered with
            AbstractGirlEntity girl = GirlManager.partnerOf(player);
            if (girl == null) return;
            switch (pkt.control()) {
                case STOP -> girl.scene().stop();
                case FAST_ON -> girl.scene().setFast(player, true);
                case FAST_OFF -> girl.scene().setFast(player, false);
            }
        });
    }
}
