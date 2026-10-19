package org.velvet.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.BoneTweaks;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlManager;
import org.velvet.entity.girl.GirlSkill;
import org.velvet.entity.girl.GirlType;
import org.velvet.entity.girl.NpcSettings;
import org.velvet.entity.girl.OwnedGirls;
import org.velvet.menu.GirlEditorMenu;
import org.velvet.skin.GirlSkin;
import org.velvet.skin.SkinStore;

/**
 * C2S: the NPC editor's "Save": name, preset, bone changes, skin ("" for none, else "mode:hash" of an
 * uploaded one) and behavior JSON, all at once. From the debug tabs also her settings and skill levels (only
 * taken from creative players with the debug tool's editor; empty and -1 otherwise).
 */
public record SaveGirlEditorPacket(String name, String preset, CompoundTag boneTweaks, String skin, String behaviorJson,
                                   String settingsJson, int[] skillLevels, String personalityJson) implements CustomPacketPayload {

    public static final int MAX_NAME_LENGTH = 32;

    public static final Type<SaveGirlEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "save_girl_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveGirlEditorPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.name(), MAX_NAME_LENGTH * 4);
                buf.writeUtf(pkt.preset(), 32);
                buf.writeNbt(pkt.boneTweaks());
                buf.writeUtf(pkt.skin(), 64);
                buf.writeUtf(pkt.behaviorJson(), GirlBehavior.MAX_LENGTH * 4);
                buf.writeUtf(pkt.settingsJson(), GirlBehavior.MAX_LENGTH);
                buf.writeVarIntArray(pkt.skillLevels());
                buf.writeUtf(pkt.personalityJson(), GirlBehavior.MAX_LENGTH);
            },
            buf -> {
                String name = buf.readUtf(MAX_NAME_LENGTH * 4);
                String preset = buf.readUtf(32);
                CompoundTag tweaks = buf.readNbt();
                return new SaveGirlEditorPacket(name, preset, tweaks == null ? new CompoundTag() : tweaks, buf.readUtf(64),
                        buf.readUtf(GirlBehavior.MAX_LENGTH * 4), buf.readUtf(GirlBehavior.MAX_LENGTH),
                        buf.readVarIntArray(GirlSkill.values().length), buf.readUtf(GirlBehavior.MAX_LENGTH));
            });

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
                player.sendSystemMessage(Component.translatable("editor.velvet.json_error", e.getMessage())
                        .withStyle(ChatFormatting.RED));
                return;
            }
            // the server itself would call those addresses
            if (behavior.provider.local && !GirlEditorMenu.mayUseLocalEndpoints(player)
                    && !(behavior.provider == girl.behavior().provider && behavior.endpoint.equals(girl.behavior().endpoint))) {
                player.sendSystemMessage(Component.translatable("editor.velvet.ai.local_not_allowed").withStyle(ChatFormatting.RED));
                behavior.provider = girl.behavior().provider;
                behavior.endpoint = girl.behavior().endpoint;
            }
            GirlSkin skin = pkt.skin().isEmpty() ? null : GirlSkin.decode(pkt.skin());
            if (skin != null && !SkinStore.has(player.server, skin.hash())) skin = girl.getSkin(); // upload not through

            String name = StringUtil.filterText(pkt.name()).trim();
            if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
            GirlType preset = GirlType.byId(pkt.preset());
            if (preset != null && preset != girl.getGirlType()) {
                girl.setGirlType(preset);
                if (!preset.strips()) girl.setModelVariant(AbstractGirlEntity.VARIANT_DRESSED);
            }
            // names are unique: another NPC's or a player's can't be taken
            if (org.velvet.entity.girl.Names.taken(player.server, name, girl.getGirlUUID())) {
                player.sendSystemMessage(Component.translatable("editor.velvet.name_taken", name).withStyle(ChatFormatting.RED));
            } else {
                if (name.isEmpty() && girl.getGirlType() != GirlType.UNKNOWN) {
                    name = org.velvet.entity.girl.Names.freeName(player.server, girl.getGirlType(), girl.getGirlUUID());
                }
                girl.setCustomNameString(name);
            }
            if (menu.isDebug() && player.isCreative()) {
                if (!pkt.settingsJson().isEmpty()) girl.setSettings(NpcSettings.fromJson(pkt.settingsJson()));
                int[] levels = pkt.skillLevels();
                for (GirlSkill skill : GirlSkill.values()) {
                    if (skill.ordinal() < levels.length && levels[skill.ordinal()] >= 0) girl.setSkillLevel(skill, levels[skill.ordinal()]);
                }
                if (!pkt.personalityJson().isEmpty()) {
                    try {
                        var json = com.google.gson.JsonParser.parseString(pkt.personalityJson()).getAsJsonObject();
                        girl.psyche().setPersonality(org.velvet.entity.girl.psyche.Personality.fromJson(json, girl.getRandom()));
                        org.velvet.entity.girl.psyche.PsycheStore.write(girl.getGirlUUID(), girl.psyche());
                    } catch (RuntimeException ignored) {
                        // a broken one changes nothing
                    }
                }
                girl.writeSettings();
            }
            girl.setBoneTweaks(BoneTweaks.fromTag(pkt.boneTweaks()));
            girl.setSkin(skin);
            girl.setBehavior(behavior);
            OwnedGirls.get(player.server).update(girl);
        });
    }
}
