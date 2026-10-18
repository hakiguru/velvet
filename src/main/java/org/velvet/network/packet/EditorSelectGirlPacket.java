package org.velvet.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;
import org.velvet.menu.GirlEditorMenu;

import java.util.UUID;

/** C2S: a girl's tab clicked in the editor: it turns to her (and stays open). */
public record EditorSelectGirlPacket(UUID girl) implements CustomPacketPayload {

    public static final Type<EditorSelectGirlPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "editor_select_girl"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorSelectGirlPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EditorSelectGirlPacket::girl, EditorSelectGirlPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EditorSelectGirlPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof GirlEditorMenu menu)) return;
            AbstractGirlEntity girl = GirlManager.byUuid(pkt.girl(), false);
            if (girl == null || !girl.isAlive()) {
                player.displayClientMessage(Component.translatable("editor.velvet.not_loaded").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!GirlManager.canEdit(player, girl)) return;
            if (girl.getCurrentAction().isBusy()) {
                player.displayClientMessage(Component.translatable("gui.velvet.busy"), true);
                return;
            }
            menu.retarget(player, girl);
        });
    }
}
