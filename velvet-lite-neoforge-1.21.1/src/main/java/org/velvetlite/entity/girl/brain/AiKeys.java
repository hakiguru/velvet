package org.velvetlite.entity.girl.brain;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API keys for the language model services, per player and service, saved with the world
 * (data/velvet_lite_ai_keys.dat) and only ever used by the server: a girl talks through her owner's key, so
 * on a server every player pays for their own companion. Keys never go back to any client; the editor only
 * learns which services have one.
 */
public final class AiKeys extends SavedData {

    private static final String NAME = "velvet_lite_ai_keys";
    public static final int MAX_KEY_LENGTH = 512;

    private final Map<UUID, Map<AiProvider, String>> keys = new HashMap<>();

    public static AiKeys get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AiKeys::new, AiKeys::load, null), NAME);
    }

    @Nullable
    public String key(UUID player, AiProvider provider) {
        Map<AiProvider, String> own = keys.get(player);
        return own == null ? null : own.get(provider);
    }

    /** Empty or null removes it. */
    public void set(UUID player, AiProvider provider, @Nullable String key) {
        if (key == null || key.isBlank()) {
            Map<AiProvider, String> own = keys.get(player);
            if (own != null) own.remove(provider);
        } else {
            keys.computeIfAbsent(player, p -> new HashMap<>()).put(provider, key.trim());
        }
        setDirty();
    }

    /** The services this player has a key for. */
    public Set<AiProvider> withKey(UUID player) {
        Set<AiProvider> out = EnumSet.noneOf(AiProvider.class);
        Map<AiProvider, String> own = keys.get(player);
        if (own != null) out.addAll(own.keySet());
        return out;
    }

    private static AiKeys load(CompoundTag tag, HolderLookup.Provider registries) {
        AiKeys data = new AiKeys();
        for (String player : tag.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(player);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag own = tag.getCompound(player);
            for (String provider : own.getAllKeys()) {
                AiProvider p = AiProvider.byId(provider);
                if (p != null) data.keys.computeIfAbsent(id, k -> new HashMap<>()).put(p, own.getString(provider));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        keys.forEach((player, own) -> {
            if (own.isEmpty()) return;
            CompoundTag t = new CompoundTag();
            own.forEach((p, key) -> t.putString(p.id, key));
            tag.put(player.toString(), t);
        });
        return tag;
    }
}
