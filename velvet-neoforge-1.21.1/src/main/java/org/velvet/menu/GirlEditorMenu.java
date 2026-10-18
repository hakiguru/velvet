package org.velvet.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.velvet.Config;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlManager;
import org.velvet.entity.girl.OwnedGirls;
import org.velvet.entity.girl.brain.AiKeys;
import org.velvet.entity.girl.brain.AiProvider;
import org.velvet.registry.VelvetMenus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The NPC editor (opened with the editor wand, on a girl or anywhere). It has no slots: it's a menu so the
 * server knows who has it open (she waits meanwhile, if he's close) and closes it when she's gone, and so it
 * can bring along what only the server knows: her behavior JSON, the player's other girls (for the tabs on
 * the left, also the ones far away) and which language model services he has a key for.
 */
public class GirlEditorMenu extends AbstractContainerMenu {

    /** One of the player's girls, for the tabs. Loaded ones can be edited; the others only show. */
    public record GirlEntry(UUID id, ResourceLocation type, boolean loaded, ResourceLocation dimension, BlockPos pos,
                            CompoundTag appearance) {}

    /** What the screen gets on top of the girl herself. */
    public record Data(@Nullable UUID target, String behaviorJson, List<GirlEntry> girls, Set<AiProvider> keys,
                       boolean localEndpoints) {

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(target != null);
            if (target != null) buf.writeUUID(target);
            buf.writeUtf(behaviorJson, GirlBehavior.MAX_LENGTH * 2);
            buf.writeVarInt(girls.size());
            for (GirlEntry e : girls) {
                buf.writeUUID(e.id());
                buf.writeResourceLocation(e.type());
                buf.writeBoolean(e.loaded());
                buf.writeResourceLocation(e.dimension());
                buf.writeBlockPos(e.pos());
                buf.writeNbt(e.appearance());
            }
            buf.writeVarInt(keys.size());
            for (AiProvider p : keys) buf.writeUtf(p.id);
            buf.writeBoolean(localEndpoints);
        }

        static Data read(RegistryFriendlyByteBuf buf) {
            UUID target = buf.readBoolean() ? buf.readUUID() : null;
            String json = buf.readUtf(GirlBehavior.MAX_LENGTH * 2);
            int n = Math.min(buf.readVarInt(), 512);
            List<GirlEntry> girls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UUID id = buf.readUUID();
                ResourceLocation type = buf.readResourceLocation();
                boolean loaded = buf.readBoolean();
                ResourceLocation dim = buf.readResourceLocation();
                BlockPos pos = buf.readBlockPos();
                CompoundTag appearance = buf.readNbt();
                girls.add(new GirlEntry(id, type, loaded, dim, pos, appearance == null ? new CompoundTag() : appearance));
            }
            Set<AiProvider> keys = EnumSet.noneOf(AiProvider.class);
            int k = buf.readVarInt();
            for (int i = 0; i < k; i++) {
                AiProvider p = AiProvider.byId(buf.readUtf());
                if (p != null) keys.add(p);
            }
            return new Data(target, json, girls, keys, buf.readBoolean());
        }
    }

    /** The girl each player edited last, to open the wand used in the air on her again. */
    private static final Map<UUID, UUID> LAST_EDITED = new HashMap<>();

    @Nullable private final AbstractGirlEntity girl;
    private final Data data;

    public GirlEditorMenu(int id, Inventory playerInv, @Nullable AbstractGirlEntity girl, Data data) {
        super(VelvetMenus.GIRL_EDITOR.get(), id);
        this.girl = girl;
        this.data = data;
        if (girl != null && !playerInv.player.level().isClientSide) girl.addMenuViewer(playerInv.player);
    }

    public static GirlEditorMenu fromNetwork(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        var entity = entityId < 0 ? null : playerInv.player.level().getEntity(entityId);
        Data data = Data.read(buf);
        return new GirlEditorMenu(id, playerInv, entity instanceof AbstractGirlEntity g ? g : null, data);
    }

    /**
     * Opens the editor for {@code target}, or without one for the girl the player edited last (or his first
     * one) that's in a loaded chunk.
     */
    public static void open(ServerPlayer player, @Nullable AbstractGirlEntity target) {
        OwnedGirls owned = OwnedGirls.get(player.server);
        if (target == null) {
            UUID last = LAST_EDITED.get(player.getUUID());
            AbstractGirlEntity lastGirl = last == null ? null : loaded(last);
            if (lastGirl != null && GirlManager.canEdit(player, lastGirl)) target = lastGirl;
            for (OwnedGirls.Entry e : owned.of(player.getUUID())) {
                if (target != null) break;
                AbstractGirlEntity g = loaded(e.girl());
                if (g != null && g.isAlive() && !g.getCurrentAction().isBusy()) target = g;
            }
        }
        if (target != null && (target.getCurrentAction().isBusy() || !GirlManager.canEdit(player, target))) return;
        if (target != null) {
            target.claimBy(player);
            owned.update(target);
            LAST_EDITED.put(player.getUUID(), target.getGirlUUID());
        }

        List<GirlEntry> girls = new ArrayList<>();
        for (OwnedGirls.Entry e : owned.of(player.getUUID())) {
            AbstractGirlEntity g = loaded(e.girl());
            girls.add(g != null
                    ? new GirlEntry(e.girl(), BuiltInRegistries.ENTITY_TYPE.getKey(g.getType()), true,
                            g.level().dimension().location(), g.blockPosition(), g.appearance())
                    : new GirlEntry(e.girl(), e.type(), false, e.dimension(), e.pos(), e.appearance()));
        }
        // an operator editing someone else's girl: she gets a tab too
        AbstractGirlEntity t = target;
        if (t != null && girls.stream().noneMatch(e -> e.id().equals(t.getGirlUUID()))) {
            girls.add(new GirlEntry(t.getGirlUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()), true,
                    t.level().dimension().location(), t.blockPosition(), t.appearance()));
        }
        Data data = new Data(target == null ? null : target.getGirlUUID(),
                target == null ? new GirlBehavior().toJson() : target.behavior().toJson(),
                girls, AiKeys.get(player.server).withKey(player.getUUID()), mayUseLocalEndpoints(player));
        AbstractGirlEntity girl = target;
        Component title = girl == null ? Component.translatable("editor.velvet.no_npc") : girl.getName();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GirlEditorMenu(id, inv, girl, data), title), buf -> {
            buf.writeVarInt(girl == null ? -1 : girl.getId());
            data.write(buf);
        });
    }

    /** Local servers and custom addresses are reached by the server itself: operators and the host only, by default. */
    public static boolean mayUseLocalEndpoints(ServerPlayer player) {
        return Config.AI_LOCAL_FOR_EVERYONE.get() || player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    @Nullable
    private static AbstractGirlEntity loaded(UUID girl) {
        return GirlManager.byUuid(girl, false);
    }

    @Nullable
    public AbstractGirlEntity getGirl() {
        return girl;
    }

    public Data getData() {
        return data;
    }

    /** Client side: her behavior as it was when the editor opened. */
    public String getBehaviorJson() {
        return data.behaviorJson();
    }

    @Override
    public boolean stillValid(Player player) {
        if (girl == null) return data.target() == null;
        return girl.isAlive() && !girl.isRemoved() && !girl.getCurrentAction().isBusy() && GirlManager.canEdit(player, girl);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (girl != null && !player.level().isClientSide) girl.removeMenuViewer(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
