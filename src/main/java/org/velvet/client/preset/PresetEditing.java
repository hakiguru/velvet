package org.velvet.client.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.entity.girl.GirlType;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.PresetActionPacket;
import org.velvet.network.packet.PresetFilePacket;
import org.velvet.preset.PresetFiles;
import org.velvet.preset.PresetMeta;
import software.bernie.geckolib.cache.GeckoLibCache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What the editor's preset tools share: a custom preset's files as they're being edited (drafts in
 * {@link PresetCache}, shown at once), starting from its base preset's when it has none of its own yet, saving
 * them to the server, and making a custom preset (from scratch, or an editable copy of a built-in one).
 */
public final class PresetEditing {

    private PresetEditing() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Waiting for the server to make the preset the editor asked for: its id and what to do with it then. */
    @Nullable private static String pendingId;
    @Nullable private static Consumer<GirlType> pendingThen;

    // ---- Making presets ----

    /** Asks the server for a new custom preset; {@code then} gets it once it's there. */
    public static void create(String name, GirlType base, float height, String voice, boolean strips, boolean translucent,
                              Consumer<GirlType> then) {
        String id = PresetMeta.idFor(name);
        String unique = id;
        for (int n = 2; GirlType.byId(unique) != null; n++) unique = id + "_" + n;
        PresetMeta meta = new PresetMeta(unique, name, base.base().id(), height, voice, strips, translucent);
        pendingId = unique;
        pendingThen = then;
        NetworkHandler.sendToServer(new PresetActionPacket(PresetActionPacket.CREATE, unique, meta.toJson()));
    }

    /** An editable copy of a built-in preset: same model, texture, voice and size, a name of its own. */
    public static void copyOf(GirlType builtIn, Consumer<GirlType> then) {
        String name = builtIn.displayName().getString() + " 2";
        String voice = builtIn.voice() == null ? "" : builtIn.voice();
        create(name, builtIn, builtIn.height(), voice, builtIn.strips(), builtIn.translucent(), then);
    }

    public static void update(GirlType type, PresetMeta meta) {
        NetworkHandler.sendToServer(new PresetActionPacket(PresetActionPacket.UPDATE, type.id(), meta.toJson()));
    }

    public static void delete(GirlType type) {
        PresetCache.dropDrafts(type.id());
        NetworkHandler.sendToServer(new PresetActionPacket(PresetActionPacket.DELETE, type.id(), "{}"));
    }

    /** Called when the server's list arrives: hands over the preset that was asked for. */
    public static void onList() {
        if (pendingId == null) return;
        GirlType made = GirlType.byId(pendingId);
        if (made == null || !made.isCustom()) return;
        Consumer<GirlType> then = pendingThen;
        pendingId = null;
        pendingThen = null;
        if (then != null) then.accept(made);
    }

    // ---- Files as they're edited ----

    /** The model of a custom preset as JSON, to change: its draft, its own file, or a copy of its base preset's. */
    public static JsonObject model(GirlType type) {
        byte[] own = PresetCache.file(type.id(), PresetFiles.MODEL);
        if (own != null) return parse(own);
        GirlType base = type.base();
        return parse(read(base.geo(base.baseModelName())));
    }

    public static void setModel(GirlType type, JsonObject json) {
        PresetCache.draft(type.id(), PresetFiles.MODEL, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    /** Its own animations as JSON ("animations": {...}); empty if it has none yet. */
    public static JsonObject animations(GirlType type) {
        byte[] own = PresetCache.file(type.id(), PresetFiles.ANIMATIONS);
        if (own != null) return parse(own);
        JsonObject o = new JsonObject();
        o.addProperty("format_version", "1.8.0");
        o.add("animations", new JsonObject());
        return o;
    }

    public static void setAnimations(GirlType type, JsonObject json) {
        PresetCache.draft(type.id(), PresetFiles.ANIMATIONS, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    /** Its texture as PNG: its own, or its base preset's. */
    public static byte[] texturePng(GirlType type) {
        byte[] own = PresetCache.file(type.id(), PresetFiles.TEXTURE);
        return own != null ? own : read(type.base().texture());
    }

    /** Any animation by its full name as JSON, from whichever file has it (to copy into a custom preset's). */
    @Nullable
    public static JsonObject animationJson(String fullName) {
        int dot = fullName.indexOf('.', "animation.".length());
        if (dot < 0) return null;
        String prefix = fullName.substring("animation.".length(), dot);
        for (GirlType t : GirlType.values()) {
            if (!t.animationPrefix().equals(prefix)) continue;
            JsonObject file = t.isCustom() ? animations(t) : parse(read(t.animations()));
            JsonObject anims = file.getAsJsonObject("animations");
            return anims != null && anims.has(fullName) ? anims.getAsJsonObject(fullName).deepCopy() : null;
        }
        return null;
    }

    /** Whether there's anything changed and not saved for it. */
    public static boolean unsaved(GirlType type) {
        return type.isCustom() && !PresetCache.drafts(type.id()).isEmpty();
    }

    /** Sends every changed file to the server, which keeps them and hands them to everyone. */
    public static void save(GirlType type) {
        if (!type.isCustom()) return;
        for (Map.Entry<String, byte[]> e : Map.copyOf(PresetCache.drafts(type.id())).entrySet()) {
            PresetFilePacket.upload(type.id(), e.getKey(), e.getValue());
        }
    }

    public static void discard(GirlType type) {
        if (type.isCustom()) PresetCache.dropDrafts(type.id());
    }

    // ---- Helpers ----

    public static JsonObject parse(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** A file of the game's resources (the built-in presets'); empty if it isn't there. */
    public static byte[] read(ResourceLocation location) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) return new byte[0];
        try (InputStream in = resource.get().open()) {
            return in.readAllBytes();
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't read {}: {}", location, e.toString());
            return new byte[0];
        }
    }

    /** Whether GeckoLib has a model at that place right now. */
    public static boolean hasModel(ResourceLocation location) {
        return GeckoLibCache.getBakedModels().containsKey(location);
    }
}
