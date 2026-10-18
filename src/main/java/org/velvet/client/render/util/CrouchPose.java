package org.velvet.client.render.util;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Sneaking like a player, on top of whatever the movement animation does (standing or walking): leaning
 * forward from the waist, knees bent, the whole body a bit lower and the head up again. Every preset has
 * the same bones, so one pose fits all; presets with their own crouch animations don't use it.
 */
public final class CrouchPose {

    private CrouchPose() {}

    /** Added to a bone: rotation in radians and position in pixels, in GeckoLib's own axes. */
    public record Delta(float rotX, float rotY, float rotZ, float posX, float posY, float posZ) {}

    // Bedrock degrees as in the animation files (-x swings a limb forward); GeckoLib flips x and y of those
    private static final Map<String, Delta> POSE = Map.of(
            "body", bedrock(0, 0, 0, 0, -2.2F, 1.2F),
            "upperBody", bedrock(26, 0, 0, 0, 0, 0),
            "head", bedrock(-22, 0, 0, 0, 0, 0),
            "legR", bedrock(-20, 0, 0, 0, 0, 0),
            "legL", bedrock(-20, 0, 0, 0, 0, 0),
            "shinR", bedrock(28, 0, 0, 0, 0, 0),
            "shinL", bedrock(28, 0, 0, 0, 0, 0),
            "armR", bedrock(-12, 0, 0, 0, 0, 0),
            "armL", bedrock(-12, 0, 0, 0, 0, 0));

    @Nullable
    public static Delta of(String bone) {
        return POSE.get(bone);
    }

    private static Delta bedrock(float rx, float ry, float rz, float px, float py, float pz) {
        return new Delta(-rx * Mth.DEG_TO_RAD, -ry * Mth.DEG_TO_RAD, rz * Mth.DEG_TO_RAD, -px, py, pz);
    }
}
