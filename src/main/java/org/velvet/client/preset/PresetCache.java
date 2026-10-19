package org.velvet.client.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.entity.girl.GirlType;
import org.velvet.network.packet.PresetListPacket;
import org.velvet.preset.PresetFiles;
import org.velvet.preset.PresetMeta;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client side: the custom presets' files, as the server sent them, and the editor's unsaved changes to them
 * (drafts, shown at once and sent with "Save"). Each preset's model, animations and texture are made out of
 * those files right here (GeckoLib and the texture manager get them as if they came with the game) and made
 * again whenever a file changes or a resource reload threw them away. Whatever file a preset hasn't got comes
 * from its base preset.
 */
public final class PresetCache {

    private PresetCache() {}

    private static final Map<String, Map<String, byte[]>> FILES = new HashMap<>();
    private static final Map<String, Map<String, byte[]>> DRAFTS = new HashMap<>();
    private static final Map<String, ByteArrayOutputStream> INCOMING = new HashMap<>();
    private static final Map<String, Integer> VERSION = new HashMap<>(), BAKED = new HashMap<>();
    private static final Map<String, DynamicTexture> TEXTURES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    // ---- From the server ----

    public static void onList(java.util.List<PresetListPacket.Entry> entries) {
        Set<String> ids = new HashSet<>();
        for (PresetListPacket.Entry e : entries) {
            PresetMeta meta = PresetMeta.fromJson(e.id(), e.metaJson());
            if (meta == null) continue;
            meta.register();
            ids.add(e.id());
            FILES.computeIfAbsent(e.id(), k -> new LinkedHashMap<>());
            bump(e.id());
        }
        for (GirlType t : GirlType.customs()) {
            if (ids.contains(t.id())) continue;
            GirlType.removeCustom(t.id());
            FILES.remove(t.id());
            DRAFTS.remove(t.id());
        }
        PresetEditing.onList();
    }

    public static void receive(String id, String path, int index, int total, byte[] data) {
        if (!PresetFiles.allowed(path)) return;
        String key = id + "|" + path;
        ByteArrayOutputStream out = index == 0 ? new ByteArrayOutputStream() : INCOMING.get(key);
        if (out == null) return;
        out.writeBytes(data);
        if (index < total - 1) {
            INCOMING.put(key, out);
            return;
        }
        INCOMING.remove(key);
        byte[] bytes = out.toByteArray();
        Map<String, byte[]> files = FILES.computeIfAbsent(id, k -> new LinkedHashMap<>());
        if (bytes.length == 0) files.remove(path);
        else files.put(path, bytes);
        // the editor's own change came back: the draft is saved now
        Map<String, byte[]> draft = DRAFTS.get(id);
        if (draft != null && draft.containsKey(path) && java.util.Arrays.equals(draft.get(path), bytes)) draft.remove(path);
        bump(id);
    }

    // ---- The editor's drafts ----

    /** A changed file, shown right away; {@code bytes} empty to drop the preset's own file. */
    public static void draft(String id, String path, byte[] bytes) {
        DRAFTS.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(path, bytes);
        bump(id);
    }

    /** A changed file whose change is already shown (the texture painted on): kept for "Save", not made again. */
    public static void draftQuietly(String id, String path, byte[] bytes) {
        DRAFTS.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(path, bytes);
    }

    public static Map<String, byte[]> drafts(String id) {
        return DRAFTS.getOrDefault(id, Map.of());
    }

    /** Forgets the unsaved changes; whatever was drawn from them (a painted texture) is made again from the files. */
    public static void dropDrafts(String id) {
        DRAFTS.remove(id);
        bump(id);
    }

    /** The file as it's shown: the draft if there's one, else what the server has; null if neither. */
    @Nullable
    public static byte[] file(String id, String path) {
        Map<String, byte[]> draft = DRAFTS.get(id);
        if (draft != null && draft.containsKey(path)) {
            byte[] d = draft.get(path);
            return d.length == 0 ? null : d;
        }
        Map<String, byte[]> files = FILES.get(id);
        return files == null ? null : files.get(path);
    }

    public static boolean has(GirlType type, String path) {
        return type.isCustom() && file(type.id(), path) != null;
    }

    /** Every sound file of the preset's own voice, as shown. */
    public static Map<String, byte[]> sounds(String id) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        Map<String, byte[]> files = FILES.get(id);
        if (files != null) files.forEach((p, b) -> {
            if (PresetFiles.isSound(p)) out.put(p, b);
        });
        drafts(id).forEach((p, b) -> {
            if (!PresetFiles.isSound(p)) return;
            if (b.length == 0) out.remove(p);
            else out.put(p, b);
        });
        return out;
    }

    private static void bump(String id) {
        VERSION.merge(id, 1, Integer::sum);
        FAILED.remove(id);
    }

    // ---- Making models, animations and textures of them ----

    /** Makes the preset's own model, animations and texture if they changed (or a reload threw them away). */
    public static void ensureBaked(GirlType type) {
        if (!type.isCustom() || FAILED.contains(type.id())) return;
        String id = type.id();
        int version = VERSION.getOrDefault(id, 0);
        boolean stale = !BAKED.containsKey(id) || BAKED.get(id) != version;
        if (!stale && has(type, PresetFiles.MODEL) && !GeckoLibCache.getBakedModels().containsKey(type.geo(id))) stale = true;
        if (!stale && has(type, PresetFiles.ANIMATIONS) && !GeckoLibCache.getBakedAnimations().containsKey(type.animations())) stale = true;
        if (!stale) return;
        BAKED.put(id, version);
        try {
            bakeModel(type, PresetFiles.MODEL, type.geo(id));
            bakeModel(type, PresetFiles.DRESSED, type.geo("dressed"));
            byte[] anims = file(id, PresetFiles.ANIMATIONS);
            if (anims != null) {
                JsonObject json = JsonParser.parseString(new String(anims, StandardCharsets.UTF_8)).getAsJsonObject();
                BakedAnimations baked = KeyFramesAdapter.GEO_GSON.fromJson(json.getAsJsonObject("animations"), BakedAnimations.class);
                GeckoLibCache.getBakedAnimations().put(type.animations(), baked);
            } else {
                GeckoLibCache.getBakedAnimations().remove(type.animations());
            }
            bakeTexture(type);
            org.velvet.client.animation.GirlAnimationController.clearCache();
        } catch (RuntimeException e) {
            FAILED.add(id);
            Velvet.LOGGER.warn("Custom preset {} has a broken file: {}", id, e.toString());
        }
    }

    private static void bakeModel(GirlType type, String path, ResourceLocation at) {
        byte[] bytes = file(type.id(), path);
        if (bytes == null) {
            GeckoLibCache.getBakedModels().remove(at);
            return;
        }
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Model.class);
        GeckoLibCache.getBakedModels().put(at, BakedModelFactory.getForNamespace(Velvet.MODID).constructGeoModel(GeometryTree.fromModel(model)));
    }

    private static void bakeTexture(GirlType type) {
        byte[] bytes = file(type.id(), PresetFiles.TEXTURE);
        DynamicTexture old = TEXTURES.remove(type.id());
        if (bytes == null) {
            if (old != null) Minecraft.getInstance().getTextureManager().release(type.texture());
            return;
        }
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(type.texture(), texture);
            TEXTURES.put(type.id(), texture);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("texture.png isn't a PNG", e);
        }
    }

    /** The preset's own texture as it's drawn, for painting on (null if it has none). */
    @Nullable
    public static DynamicTexture texture(GirlType type) {
        ensureBaked(type);
        return TEXTURES.get(type.id());
    }

    /** Leaving a world: the next one has its own presets. */
    public static void clear() {
        FILES.clear();
        DRAFTS.clear();
        INCOMING.clear();
        VERSION.clear();
        BAKED.clear();
        FAILED.clear();
        TEXTURES.keySet().forEach(id -> {
            GirlType t = GirlType.byId(id);
            if (t != null) Minecraft.getInstance().getTextureManager().release(t.texture());
        });
        TEXTURES.clear();
        for (GirlType t : GirlType.customs()) GirlType.removeCustom(t.id());
    }
}
