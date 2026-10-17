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
import org.velvetlite.entity.girl.scene.SceneType;

import java.util.UUID;

/** C2S: the player picked a paid scene in the girl's menu. Cost and conditions are checked on the server. */
public record StartScenePacket(UUID girlId, int scene) implements CustomPacketPayload {

    public static final Type<StartScenePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "start_scene"));

    public static final StreamCodec<FriendlyByteBuf, StartScenePacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, StartScenePacket::girlId,
                    ByteBufCodecs.VAR_INT, StartScenePacket::scene,
                    StartScenePacket::new);

    public StartScenePacket(UUID girlId, SceneType scene) {
        this(girlId, scene.ordinal());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StartScenePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.scene() < 0 || pkt.scene() >= SceneType.values().length) return;
            SceneType type = SceneType.values()[pkt.scene()];
            GirlManager.allByUuid(pkt.girlId()).stream()
                    .filter(g -> GirlManager.canCommand(player, g))
                    .findFirst()
                    .ifPresent(g -> g.scene().start(player, type));
        });
    }
}
