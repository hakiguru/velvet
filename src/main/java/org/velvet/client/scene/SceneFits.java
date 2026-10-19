package org.velvet.client.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.VelvetPaths;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.BoneTweaks;
import org.velvet.entity.girl.GirlType;
import org.velvet.entity.girl.scene.SceneType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How each scene is fitted to each preset: the scenes come from one preset's animations, and on another
 * body her pose, the partner (the "steve" bone) and his cock may need moving, turning or scaling to meet.
 * A fit is a few bone changes drawn on top of the animation while she plays that scene.
 * <p>
 * The mod's own fits are in {@code assets/velvet/scene_fits.json}; the ones made in the editor (model tab,
 * "Scene") are kept in {@code config/velvet/scene_fits.json} and win over them. Both look like:
 * <pre>
 * {
 *   "jenny|deepthroat": {
 *     "body":  { "offset": [0, -4, 2], "rotation": [0, 0, 0], "scale": [1, 1, 1] },
 *     "steve": { "offset": [0, 0, -1.5] },
 *     "cock":  { "scale": [1, 1, 1.2] }
 *   }
 * }
 * </pre>
 * Offsets are in pixels (1/16 block), rotations in degrees. A custom preset without fits of its own uses its
 * base preset's.
 */
public final class SceneFits {

    private SceneFits() {}

    /** The bones a fit changes: her whole body, the partner's, and his cock. */
    public static final List<String> BONES = List.of("body", "steve", "cock");

    private static final ResourceLocation BUILT_IN = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "scene_fits.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Nullable private static Map<String, Map<String, BoneTweaks.Tweak>> builtIn;
    @Nullable private static Map<String, Map<String, BoneTweaks.Tweak>> own;

    public static String key(GirlType preset, SceneType scene) {
        return preset.id() + "|" + scene.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The fit she's drawn with right now: the editor's, or the scene she's playing; null for none. */
    @Nullable
    public static Map<String, BoneTweaks.Tweak> active(AbstractGirlEntity girl) {
        SceneType scene = girl.clientPreviewScene;
        if (scene == null && girl.getCurrentAction().isBusy()) scene = SceneType.ofStage(girl.getCurrentAction());
        if (scene == null) return null;
        Map<String, BoneTweaks.Tweak> fit = get(girl.getGirlType(), scene);
        return fit.isEmpty() ? null : fit;
    }

    /** The preset's fit of the scene (its base preset's if it has none); empty if it needs none. */
    public static Map<String, BoneTweaks.Tweak> get(GirlType preset, SceneType scene) {
        load();
        Map<String, BoneTweaks.Tweak> fit = find(key(preset, scene));
        if (fit == null && preset.isCustom()) fit = find(key(preset.base(), scene));
        return fit == null ? Map.of() : fit;
    }

    @Nullable
    private static Map<String, BoneTweaks.Tweak> find(String key) {
        Map<String, BoneTweaks.Tweak> fit = own.get(key);
        return fit != null ? fit : builtIn.get(key);
    }

    /** One bone of the preset's own fit of the scene, to change (a copy of what applies now). */
    public static BoneTweaks.Tweak edit(GirlType preset, SceneType scene, String bone) {
        load();
        Map<String, BoneTweaks.Tweak> fit = own.computeIfAbsent(key(preset, scene), k -> {
            Map<String, BoneTweaks.Tweak> start = new LinkedHashMap<>();
            get(preset, scene).forEach((b, t) -> start.put(b, t.copy()));
            return start;
        });
        return fit.computeIfAbsent(bone, b -> BoneTweaks.Tweak.identity());
    }

    /** Back to the mod's fit of the scene for that preset (or none). */
    public static void reset(GirlType preset, SceneType scene) {
        load();
        own.remove(key(preset, scene));
    }

    /** Keeps the fits made in the editor. */
    public static void save() {
        load();
        JsonObject root = new JsonObject();
        own.forEach((key, fit) -> {
            JsonObject bones = new JsonObject();
            fit.forEach((bone, t) -> {
                if (!t.movesBone()) return;
                JsonObject o = new JsonObject();
                o.add("offset", array(t.offset));
                o.add("rotation", array(t.rotation));
                o.add("scale", array(t.scale));
                bones.add(bone, o);
            });
            if (bones.size() > 0) root.add(key, bones);
        });
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't write {}: {}", file, e.toString());
        }
    }

    /** Reads both again (after the files were changed by hand, or the resources reloaded). */
    public static void reload() {
        builtIn = null;
        own = null;
        load();
    }

    public static Path file() {
        return VelvetPaths.root().resolve("scene_fits.json");
    }

    private static void load() {
        if (builtIn != null && own != null) return;
        builtIn = new HashMap<>();
        own = new HashMap<>();
        var resource = Minecraft.getInstance().getResourceManager().getResource(BUILT_IN);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                read(new String(in.readAllBytes(), StandardCharsets.UTF_8), builtIn);
            } catch (IOException | RuntimeException e) {
                Velvet.LOGGER.warn("Couldn't read {}: {}", BUILT_IN, e.toString());
            }
        }
        Path file = file();
        if (Files.isRegularFile(file)) {
            try {
                read(Files.readString(file, StandardCharsets.UTF_8), own);
            } catch (IOException | RuntimeException e) {
                Velvet.LOGGER.warn("Couldn't read {}: {}", file, e.toString());
            }
        }
    }

    private static void read(String text, Map<String, Map<String, BoneTweaks.Tweak>> into) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        for (var entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            Map<String, BoneTweaks.Tweak> fit = new LinkedHashMap<>();
            for (var bone : entry.getValue().getAsJsonObject().entrySet()) {
                if (!bone.getValue().isJsonObject()) continue;
                JsonObject o = bone.getValue().getAsJsonObject();
                BoneTweaks.Tweak t = BoneTweaks.Tweak.identity();
                vector(o.get("offset"), t.offset);
                vector(o.get("rotation"), t.rotation);
                vector(o.get("scale"), t.scale);
                fit.put(bone.getKey(), t);
            }
            into.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), fit);
        }
    }

    private static void vector(@Nullable JsonElement e, float[] into) {
        if (e == null || !e.isJsonArray()) return;
        JsonArray a = e.getAsJsonArray();
        for (int i = 0; i < 3 && i < a.size(); i++) into[i] = a.get(i).getAsFloat();
    }

    private static JsonArray array(float[] v) {
        JsonArray a = new JsonArray();
        for (float f : v) a.add(Math.round(f * 100) / 100.0F);
        return a;
    }
}
