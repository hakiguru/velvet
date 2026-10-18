package org.velvetlite.entity.girl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Changes to single bones of a girl's model made in the NPC editor: scale, shift (in model pixels) and rotation
 * (degrees) per bone on top of whatever the animations do, a colour multiplied into the bone and everything on
 * it, and hiding it. Synced to clients as a tag and applied while drawing.
 */
public final class BoneTweaks {

    public static final float MIN_SCALE = 0.25F, MAX_SCALE = 2.5F;
    public static final float MAX_OFFSET = 8;
    public static final float MAX_ROTATION = 180;
    public static final int NO_TINT = 0xFFFFFFFF;
    /** Tags with more bones than this are cut off (the models have about two hundred). */
    private static final int MAX_BONES = 320;

    public static final BoneTweaks NONE = new BoneTweaks(Map.of());

    /** One bone: x/y/z of scale, offset and rotation, its tint (ARGB) and whether it's hidden. */
    public static final class Tweak {
        public final float[] scale;
        public final float[] offset;
        public final float[] rotation;
        public int tint = NO_TINT;
        public boolean hidden;

        public Tweak(float[] scale, float[] offset, float[] rotation) {
            this.scale = scale;
            this.offset = offset;
            this.rotation = rotation;
        }

        public static Tweak identity() {
            return new Tweak(new float[]{1, 1, 1}, new float[3], new float[3]);
        }

        public Tweak copy() {
            Tweak t = new Tweak(scale.clone(), offset.clone(), rotation.clone());
            t.tint = tint;
            t.hidden = hidden;
            return t;
        }

        public boolean isIdentity() {
            if (tint != NO_TINT || hidden) return false;
            for (int i = 0; i < 3; i++) {
                if (scale[i] != 1 || offset[i] != 0 || rotation[i] != 0) return false;
            }
            return true;
        }

        public boolean movesBone() {
            for (int i = 0; i < 3; i++) {
                if (scale[i] != 1 || offset[i] != 0 || rotation[i] != 0) return true;
            }
            return false;
        }

        // accessors kept for older callers
        public float[] scale() { return scale; }
        public float[] offset() { return offset; }
        public float[] rotation() { return rotation; }
    }

    private final Map<String, Tweak> bones;

    private BoneTweaks(Map<String, Tweak> bones) {
        this.bones = bones;
    }

    public Tweak get(String bone) {
        return bones.get(bone);
    }

    public boolean isEmpty() {
        return bones.isEmpty();
    }

    /** A copy to edit. */
    public Map<String, Tweak> toMutableMap() {
        Map<String, Tweak> copy = new LinkedHashMap<>();
        bones.forEach((name, t) -> copy.put(name, t.copy()));
        return copy;
    }

    public static BoneTweaks of(Map<String, Tweak> bones) {
        Map<String, Tweak> kept = new LinkedHashMap<>();
        bones.forEach((name, t) -> {
            if (!t.isIdentity()) kept.put(name, t.copy());
        });
        return kept.isEmpty() ? NONE : new BoneTweaks(kept);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        bones.forEach((name, t) -> {
            CompoundTag bone = new CompoundTag();
            bone.put("scale", floats(t.scale));
            bone.put("offset", floats(t.offset));
            bone.put("rotation", floats(t.rotation));
            if (t.tint != NO_TINT) bone.putInt("tint", t.tint);
            if (t.hidden) bone.putBoolean("hidden", true);
            tag.put(name, bone);
        });
        return tag;
    }

    /** Reads and clamps a tag (it may come from a client). */
    public static BoneTweaks fromTag(CompoundTag tag) {
        Map<String, Tweak> bones = new LinkedHashMap<>();
        for (String name : tag.getAllKeys()) {
            if (bones.size() >= MAX_BONES || name.length() > 64) break;
            CompoundTag bone = tag.getCompound(name);
            Tweak t = Tweak.identity();
            read(bone, "scale", t.scale, MIN_SCALE, MAX_SCALE);
            read(bone, "offset", t.offset, -MAX_OFFSET, MAX_OFFSET);
            read(bone, "rotation", t.rotation, -MAX_ROTATION, MAX_ROTATION);
            // fully see-through tints would hide the bone: that's what "hidden" is for
            if (bone.contains("tint")) t.tint = bone.getInt("tint") | 0x20000000;
            t.hidden = bone.getBoolean("hidden");
            bones.put(name, t);
        }
        return of(bones);
    }

    private static ListTag floats(float[] values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    private static void read(CompoundTag bone, String key, float[] into, float min, float max) {
        ListTag list = bone.getList(key, Tag.TAG_FLOAT);
        if (list.size() != 3) return;
        for (int i = 0; i < 3; i++) {
            float v = list.getFloat(i);
            into[i] = Float.isFinite(v) ? Mth.clamp(v, min, max) : into[i];
        }
    }
}
