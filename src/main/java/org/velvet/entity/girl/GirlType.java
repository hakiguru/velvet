package org.velvet.entity.girl;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The model presets an NPC can wear, picked in the editor: geometry, textures, animations and voice. The
 * built-in ones come with the mod (geo/, textures/entity/, animations/ and sounds/girls/ under one folder
 * each); custom ones are made in the editor or dropped into {@code config/velvet/presets/<id>/} and come
 * from there (see {@link org.velvet.preset.PresetStore}). Every preset has the same skeleton (head, torso,
 * hip, arms, legs and the "steve" partner), so gear, poses, crouching, scenes and the editor work the same on
 * all of them. UNKNOWN is the blank one the spawn egg makes: a white player.
 */
public final class GirlType {

    private static final Map<String, GirlType> BY_ID = new LinkedHashMap<>();
    /** Ids presets used to have: worlds from before keep working. */
    private static final Map<String, String> ALIASES = Map.of("elsie", "jenny");

    //                                       id         folder     voice     height  strips
    public static final GirlType UNKNOWN = builtIn("unknown", "unknown", null, 1.8F, false);
    public static final GirlType JENNY   = builtIn("jenny",   "elsie",   "elsie",  1.8F, true);
    public static final GirlType ELLIE   = builtIn("ellie",   "ellie",   "ellie",  1.8F, true);
    public static final GirlType BIA     = builtIn("bia",     "bia",     "bia",    1.8F, true);
    public static final GirlType SLIME   = builtIn("slime",   "slime",   "elsie",  1.44F, true).seeThrough();
    public static final GirlType ALLIE   = builtIn("allie",   "allie",   "allie",  1.8F, false);
    public static final GirlType LUNA    = builtIn("luna",    "cat",     "luna",   1.8F, false);
    public static final GirlType GALATH  = builtIn("galath",  "galath",  null,     1.8F, true);

    private final String id;
    private final String assetFolder;
    // a custom preset can be changed in the editor: these then change in place, so everyone who has it
    // (the NPCs wearing it, the open editor) sees the change
    @Nullable private String voice;
    private float height;
    private boolean strips;
    /** Made in the editor or dropped into the presets folder, not part of the mod. */
    private final boolean custom;
    /** For custom ones: the name its maker gave it. */
    @Nullable private String customName;
    /** Her texture has see-through parts (slime), drawn blended instead of cut out. */
    private boolean translucent;
    /** Custom ones: what they're made from; whatever file they don't have of their own comes from it. */
    @Nullable private GirlType base;

    private GirlType(String id, String assetFolder, @Nullable String voice, float height, boolean strips,
                     boolean custom, @Nullable String customName) {
        this.id = id;
        this.assetFolder = assetFolder;
        this.voice = voice;
        this.height = height;
        this.strips = strips;
        this.custom = custom;
        this.customName = customName;
    }

    private static GirlType builtIn(String id, String folder, @Nullable String voice, float height, boolean strips) {
        GirlType type = new GirlType(id, folder, voice, height, strips, false, null);
        BY_ID.put(id, type);
        return type;
    }

    /**
     * Adds (or replaces) a custom preset; its files live under {@code custom/<id>/} in the velvet
     * namespace once {@link org.velvet.preset.PresetStore} has put them in place.
     */
    public static synchronized GirlType registerCustom(String id, String name, @Nullable String voice, float height,
                                                       boolean strips, boolean translucent, @Nullable GirlType base) {
        GirlType existing = BY_ID.get(id);
        if (existing != null && !existing.custom) return existing;
        GirlType type = existing != null ? existing : new GirlType(id, "custom/" + id, voice, height, strips, true, name);
        type.voice = voice;
        type.height = height;
        type.strips = strips;
        type.customName = name;
        type.translucent = translucent;
        type.base = base != null && !base.custom ? base : JENNY;
        BY_ID.put(id, type);
        return type;
    }

    private GirlType seeThrough() {
        this.translucent = true;
        return this;
    }

    public static synchronized void removeCustom(String id) {
        GirlType existing = BY_ID.get(id);
        if (existing != null && existing.custom) BY_ID.remove(id);
    }

    public String id()                  { return id; }
    /** Folder under geo/, textures/entity/ and animations/ (custom ones: "custom/<id>"). */
    public String assetFolder()         { return assetFolder; }
    /** Folder of her voice under sounds/girls/; null for a silent one. */
    @Nullable public String voice()     { return voice; }
    /** Standing height in blocks (the width is a player's). */
    public float height()               { return height; }
    /** Has a dressed model and the animation to take it off. */
    public boolean strips()             { return strips; }
    public boolean isCustom()           { return custom; }
    public boolean translucent()        { return translucent; }
    /** Custom ones: the built-in preset they're made from; built-in ones: themselves. */
    public GirlType base()              { return base != null ? base : this; }

    /** What her animations are called in her animation file: "animation.<this>.<name>". */
    public String animationPrefix() {
        return custom ? id : assetFolder;
    }

    /** A model file of hers by name ("elsie", "dressed", ...). */
    public ResourceLocation geo(String name) {
        return loc("geo/" + assetFolder + "/" + name + ".geo.json");
    }

    /** Her own texture. */
    public ResourceLocation texture() {
        String file = custom ? id : assetFolder;
        return loc("textures/entity/" + assetFolder + "/" + file + ".png");
    }

    public ResourceLocation animations() {
        String file = custom ? id : assetFolder;
        return loc("animations/" + assetFolder + "/" + file + ".animation.json");
    }

    /** The base model's file name: the id for the built-ins and custom ones alike. */
    public String baseModelName() {
        return id.equals("jenny") ? "elsie" : id;
    }

    /** Every custom preset (not the built-in ones). */
    public static synchronized List<GirlType> customs() {
        List<GirlType> out = new ArrayList<>();
        for (GirlType t : BY_ID.values()) if (t.custom) out.add(t);
        return out;
    }

    public Component displayName() {
        return customName != null ? Component.literal(customName) : Component.translatable("preset.velvet." + id);
    }

    @Nullable
    public static synchronized GirlType byId(String id) {
        if (id == null || id.isEmpty()) return null;
        String key = id.toLowerCase(Locale.ROOT);
        GirlType type = BY_ID.get(ALIASES.getOrDefault(key, key));
        return type;
    }

    /** Every preset: the built-in ones in their order, then the custom ones. */
    public static synchronized List<GirlType> values() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public String registryName() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(Velvet.MODID, path);
    }
}
