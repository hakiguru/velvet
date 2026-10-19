package org.velvet.entity.girl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.velvet.Velvet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's girls, also the ones in unloaded chunks: who they belong to, where they were last and how
 * they look. Saved with the world (data/velvet_owned_girls.dat); the editor lists them as tabs. A girl's
 * entry goes when she dies or is removed for good.
 */
@EventBusSubscriber(modid = Velvet.MODID)
public final class OwnedGirls extends SavedData {

    private static final String NAME = "velvet_owned_girls";

    public record Entry(UUID girl, UUID owner, ResourceLocation type, ResourceLocation dimension, BlockPos pos,
                        CompoundTag appearance, long created) {}

    private final Map<UUID, Entry> girls = new LinkedHashMap<>();
    /** Deleted with the debug tool while far away: gone as soon as her chunk loads. */
    private final java.util.Set<UUID> deleted = new java.util.HashSet<>();

    public static OwnedGirls get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OwnedGirls::new, OwnedGirls::load, null), NAME);
    }

    /** Every player's girls. */
    public java.util.Collection<Entry> all() {
        return girls.values();
    }

    /** Oldest first, as they joined the player. */
    public List<Entry> of(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : girls.values()) if (e.owner().equals(owner)) out.add(e);
        out.sort(Comparator.comparingLong(Entry::created));
        return out;
    }

    public void update(AbstractGirlEntity girl) {
        UUID owner = girl.getOwnerUUID();
        UUID id = girl.getGirlUUID();
        if (owner == null) {
            if (girls.remove(id) != null) setDirty();
            return;
        }
        Entry old = girls.get(id);
        girls.put(id, new Entry(id, owner, BuiltInRegistries.ENTITY_TYPE.getKey(girl.getType()),
                girl.level().dimension().location(), girl.blockPosition(), girl.appearance(),
                old != null ? old.created() : girl.level().getGameTime()));
        setDirty();
    }

    public void remove(UUID girl) {
        if (girls.remove(girl) != null) setDirty();
    }

    /** Deletes her for good, also if she's in a chunk that isn't loaded right now. */
    public void delete(UUID girl) {
        remove(girl);
        deleted.add(girl);
        setDirty();
    }

    // ---- Keeping it up to date ----

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof AbstractGirlEntity girl && !event.getLevel().isClientSide() && girl.getServer() != null) {
            OwnedGirls owned = get(girl.getServer());
            if (owned.deleted.remove(girl.getGirlUUID())) {
                owned.setDirty();
                event.setCanceled(true);
                return;
            }
            owned.update(girl);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractGirlEntity girl) || event.getLevel().isClientSide() || girl.getServer() == null) return;
        Entity.RemovalReason reason = girl.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) get(girl.getServer()).remove(girl.getGirlUUID());
        else get(girl.getServer()).update(girl);
    }

    // ---- Saving ----

    private static OwnedGirls load(CompoundTag tag, HolderLookup.Provider registries) {
        OwnedGirls data = new OwnedGirls();
        for (net.minecraft.nbt.Tag d : tag.getList("_deleted", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            data.deleted.add(net.minecraft.nbt.NbtUtils.loadUUID(d));
        }
        for (String key : tag.getAllKeys()) {
            if (key.equals("_deleted")) continue;
            CompoundTag t = tag.getCompound(key);
            try {
                UUID id = UUID.fromString(key);
                ResourceLocation type = ResourceLocation.parse(t.getString("type"));
                ResourceLocation dim = ResourceLocation.parse(t.getString("dimension"));
                data.girls.put(id, new Entry(id, t.getUUID("owner"), type, dim, BlockPos.of(t.getLong("pos")),
                        t.getCompound("appearance"), t.getLong("created")));
            } catch (RuntimeException e) {
                Velvet.LOGGER.warn("Skipping a broken NPC entry {}", key);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        girls.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", e.owner());
            t.putString("type", e.type().toString());
            t.putString("dimension", e.dimension().toString());
            t.putLong("pos", e.pos().asLong());
            t.put("appearance", e.appearance());
            t.putLong("created", e.created());
            tag.put(id.toString(), t);
        });
        net.minecraft.nbt.ListTag gone = new net.minecraft.nbt.ListTag();
        for (UUID id : deleted) gone.add(net.minecraft.nbt.NbtUtils.createUUID(id));
        if (!gone.isEmpty()) tag.put("_deleted", gone);
        return tag;
    }
}
