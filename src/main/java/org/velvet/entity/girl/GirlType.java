package org.velvet.entity.girl;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The model presets an NPC can wear, picked in the editor: geometry, textures, animations and voice from
 * geo/, textures/entity/, animations/ and sounds/girls/. Every preset has the same bones (head, torso, hip,
 * arms, legs and the "steve" partner), so gear, poses, crouching and the editor work the same on all of them.
 * UNKNOWN is the blank one the spawn egg makes: a white player.
 */
public enum GirlType {

    //       id          folder     voice     height  strips
    UNKNOWN ("unknown",  "unknown", null,     1.8F,   false),
    ELSIE   ("elsie",    "elsie",   "elsie",  1.8F,   true),
    ELLIE   ("ellie",    "ellie",   "ellie",  1.8F,   true),
    BIA     ("bia",      "bia",     "bia",    1.8F,   true),
    SLIME   ("slime",    "slime",   "elsie",  1.44F,  true),
    ALLIE   ("allie",    "allie",   "allie",  1.8F,   false),
    LUNA    ("luna",     "cat",     "luna",   1.8F,   false),
    GALATH  ("galath",   "galath",  "galath", 1.8F,   true);

    private final String id;
    private final String assetFolder;
    @Nullable private final String voice;
    private final float height;
    private final boolean strips;

    GirlType(String id, String assetFolder, @Nullable String voice, float height, boolean strips) {
        this.id = id;
        this.assetFolder = assetFolder;
        this.voice = voice;
        this.height = height;
        this.strips = strips;
    }

    public String id()                  { return id; }
    /** Folder under geo/, textures/entity/ and animations/; also the animation name prefix. */
    public String assetFolder()         { return assetFolder; }
    /** Folder of her voice under sounds/girls/; null for a silent one. */
    @Nullable public String voice()     { return voice; }
    /** Standing height in blocks (the width is a player's). */
    public float height()               { return height; }
    /** Has a dressed model and the animation to take it off. */
    public boolean strips()             { return strips; }

    public Component displayName() {
        return Component.translatable("preset.velvet." + id);
    }

    @Nullable
    public static GirlType byId(String id) {
        for (GirlType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public String registryName() {
        return id;
    }
}
