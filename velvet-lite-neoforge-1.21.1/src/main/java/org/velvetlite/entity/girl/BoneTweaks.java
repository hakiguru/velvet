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
 * (degrees) per bone, on top of whatever the animations do. Synced to clients as a tag and applied while drawing.
 */
public final class BoneTweaks {

    public static final float MIN_SCALE = 0.25F, MAX_SCALE = 2.5F;
    public static final float MAX_OFFSET = 8;
    public static final float MAX_ROTATION = 180;
    /** Tags with more bones than this are cut off (the models have about a hundred). */
    private static final int MAX_BONES = 256;

    public static final BoneTweaks NONE = new BoneTweaks(Map.of());

    /** One bone: x/y/z of scale, offset and rotation. */
    public record Tweak(float[] scale, float[] offset, float[] rotation) {
        public static Tweak identity() {
            return new Tweak(new float[]{1, 1, 1}, new float[3], new float[3]);
        }

        public Tweak copy() {
            return new Tweak(scale.clone(), offset.clone(), rotation.clone());
        }

        public boolean isIdentity() {
            for (int i = 0; i < 3; i++) {
                if (scale[i] != 1 || offset[i] != 0 || rotation[i] != 0) return false;
            }
            return true;
        }
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
            bone.put("scale", floats(t.scale()));
            bone.put("offset", floats(t.offset()));
            bone.put("rotation", floats(t.rotation()));
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
            read(bone, "scale", t.scale(), MIN_SCALE, MAX_SCALE);
            read(bone, "offset", t.offset(), -MAX_OFFSET, MAX_OFFSET);
            read(bone, "rotation", t.rotation(), -MAX_ROTATION, MAX_ROTATION);
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
