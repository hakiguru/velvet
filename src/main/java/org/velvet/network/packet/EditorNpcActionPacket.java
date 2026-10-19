package org.velvet.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;
import org.velvet.entity.girl.OwnedGirls;
import org.velvet.entity.girl.psyche.Emotion;
import org.velvet.menu.GirlEditorMenu;

import java.util.UUID;

/**
 * C2S, from the debug tool's editor (creative only): go to one of the NPCs, bring her over, delete her, or set
 * how she feels right now ({@code value}: an emotion's index and value, adrenaline as index 7).
 */
public record EditorNpcActionPacket(int action, UUID girl, int index, float value) implements CustomPacketPayload {

    public static final int TELEPORT_TO = 0, BRING = 1, DELETE = 2, EMOTION = 3;

    public static final Type<EditorNpcActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "editor_npc_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorNpcActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EditorNpcActionPacket::action,
            net.minecraft.core.UUIDUtil.STREAM_CODEC, EditorNpcActionPacket::girl,
            ByteBufCodecs.VAR_INT, EditorNpcActionPacket::index,
            ByteBufCodecs.FLOAT, EditorNpcActionPacket::value,
            EditorNpcActionPacket::new);

    public static EditorNpcActionPacket of(int action, UUID girl) {
        return new EditorNpcActionPacket(action, girl, 0, 0);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EditorNpcActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !player.isCreative()
                    || !(player.containerMenu instanceof GirlEditorMenu menu) || !menu.isDebug()) return;
            AbstractGirlEntity girl = GirlManager.byUuid(pkt.girl(), false);
            switch (pkt.action()) {
                case TELEPORT_TO -> {
                    if (girl != null && girl.level() instanceof ServerLevel level) {
                        player.closeContainer();
                        player.teleportTo(level, girl.getX(), girl.getY(), girl.getZ(), girl.getYRot(), player.getXRot());
                    } else {
                        // far away: her last known spot (from the owners' list)
                        for (OwnedGirls.Entry e : OwnedGirls.get(player.server).of(player.getUUID())) {
                            if (!e.girl().equals(pkt.girl())) continue;
                            ServerLevel level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.DIMENSION, e.dimension()));
                            if (level == null) return;
                            player.closeContainer();
                            player.teleportTo(level, e.pos().getX() + 0.5, e.pos().getY(), e.pos().getZ() + 0.5, player.getYRot(), player.getXRot());
                        }
                    }
                }
                case BRING -> {
                    if (girl == null) return;
                    if (girl.isPassenger()) girl.stopRiding();
                    girl.scene().stop();
                    if (girl.level() != player.level()) {
                        Entity moved = girl.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                                player.serverLevel(), player.position(), net.minecraft.world.phys.Vec3.ZERO, girl.getYRot(), 0,
                                net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
                        if (moved instanceof AbstractGirlEntity g) OwnedGirls.get(player.server).update(g);
                    } else {
                        girl.teleportTo(player.getX(), player.getY(), player.getZ());
                        girl.getNavigation().stop();
                    }
                }
                case DELETE -> {
                    if (girl == null) {
                        OwnedGirls.get(player.server).delete(pkt.girl());
                    } else {
                        girl.scene().stop();
                        OwnedGirls.get(player.server).remove(girl.getGirlUUID());
                        girl.discard();
                    }
                    player.displayClientMessage(Component.translatable("editor.velvet.debug.deleted").withStyle(ChatFormatting.GRAY), true);
                    player.closeContainer();
                }
                case EMOTION -> {
                    if (girl == null) return;
                    if (pkt.index() >= 0 && pkt.index() < Emotion.values().length) girl.psyche().set(Emotion.values()[pkt.index()], pkt.value());
                    else if (pkt.index() == Emotion.values().length) girl.psyche().setAdrenaline(pkt.value());
                }
                default -> {}
            }
        });
    }
}
