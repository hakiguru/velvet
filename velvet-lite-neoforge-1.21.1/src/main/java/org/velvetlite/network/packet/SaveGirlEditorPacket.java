package org.velvetlite.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.BoneTweaks;
import org.velvetlite.entity.girl.GirlBehavior;
import org.velvetlite.entity.girl.GirlManager;
import org.velvetlite.entity.girl.OwnedGirls;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.skin.GirlSkin;
import org.velvetlite.skin.SkinStore;

/**
 * C2S: the NPC editor's "Save": name, bone changes, skin ("" for none, else "mode:hash" of an uploaded one)
 * and behavior JSON, all at once.
 */
public record SaveGirlEditorPacket(String name, CompoundTag boneTweaks, String skin, String behaviorJson) implements CustomPacketPayload {

    public static final int MAX_NAME_LENGTH = 32;

    public static final Type<SaveGirlEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "save_girl_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveGirlEditorPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), SaveGirlEditorPacket::name,
                    ByteBufCodecs.COMPOUND_TAG, SaveGirlEditorPacket::boneTweaks,
                    ByteBufCodecs.stringUtf8(64), SaveGirlEditorPacket::skin,
                    ByteBufCodecs.stringUtf8(GirlBehavior.MAX_LENGTH * 4), SaveGirlEditorPacket::behaviorJson,
                    SaveGirlEditorPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveGirlEditorPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // only the girl whose editor this player has open
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof GirlEditorMenu menu)) return;
            AbstractGirlEntity girl = menu.getGirl();
            if (girl == null || !GirlManager.canEdit(player, girl)) return;

            GirlBehavior behavior;
            try {
                behavior = GirlBehavior.fromJson(pkt.behaviorJson());
            } catch (RuntimeException e) {
                player.sendSystemMessage(Component.translatable("editor.velvet_lite.json_error", e.getMessage())
                        .withStyle(ChatFormatting.RED));
                return;
            }
            // the server itself would call those addresses
            if (behavior.provider.local && !GirlEditorMenu.mayUseLocalEndpoints(player)
                    && !(behavior.provider == girl.behavior().provider && behavior.endpoint.equals(girl.behavior().endpoint))) {
                player.sendSystemMessage(Component.translatable("editor.velvet_lite.ai.local_not_allowed").withStyle(ChatFormatting.RED));
                behavior.provider = girl.behavior().provider;
                behavior.endpoint = girl.behavior().endpoint;
            }
            GirlSkin skin = pkt.skin().isEmpty() ? null : GirlSkin.decode(pkt.skin());
            if (skin != null && !SkinStore.has(player.server, skin.hash())) skin = girl.getSkin(); // upload not through

            String name = StringUtil.filterText(pkt.name()).trim();
            if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
            girl.setCustomNameString(name);
            girl.setBoneTweaks(BoneTweaks.fromTag(pkt.boneTweaks()));
            girl.setSkin(skin);
            girl.setBehavior(behavior);
            OwnedGirls.get(player.server).update(girl);
        });
    }
}
