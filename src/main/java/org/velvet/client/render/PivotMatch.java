package org.velvet.client.render;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.BoneTweaks;
import org.velvet.entity.girl.GirlAction;
import org.velvet.entity.girl.GirlType;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Animations are shared between presets, but the presets don't all turn their body bone around the same
 * point: Allie's is at her hips (14.8 px up), the others' at her feet. Turned by an animation made for the
 * other kind (Allie lying on her back in deepthroat, on Jenny), the body would swing around the wrong point,
 * into the ground. Moving the bone by the difference makes it end up where it does on the preset the
 * animation was made for: a point turned around pivot P lands at R(v - P) + P, so the gap between two pivots
 * is (I - R)(P_animation - P_model).
 */
final class PivotMatch {

    private PivotMatch() {}

    /** Body pivot of each preset's model, px (GeckoLib's own axes). */
    private static final Map<GirlType, Vector3f> BODY_PIVOTS = new ConcurrentHashMap<>();

    /** The move that puts her body where the playing animation's preset would have it; null if none's needed. */
    @Nullable
    static BoneTweaks.Tweak body(AbstractGirlEntity girl, GeoBone bone) {
        GirlType source = animationPreset(girl);
        if (source == null || source.base() == girl.getGirlType().base()) return null;
        Vector3f from = pivot(source);
        if (from == null) return null;
        Vector3f gap = new Vector3f(from).sub(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
        if (gap.lengthSquared() < 1.0E-4F) return null;
        // the bone's turn as the renderer does it: Z, then Y, then X
        Matrix3f turn = new Matrix3f().rotateZ(bone.getRotZ()).rotateY(bone.getRotY()).rotateX(bone.getRotX());
        Vector3f move = new Vector3f(gap).sub(turn.transform(new Vector3f(gap)));
        if (move.lengthSquared() < 1.0E-4F) return null;
        BoneTweaks.Tweak t = BoneTweaks.Tweak.identity();
        // the renderer moves a bone by (-x, y, z) of its position
        t.offset[0] = -move.x;
        t.offset[1] = move.y;
        t.offset[2] = move.z;
        return t;
    }

    /** Whose animation she's playing: the editor's pose, or the scene stage's preset. */
    @Nullable
    private static GirlType animationPreset(AbstractGirlEntity girl) {
        String preview = girl.clientPreviewAnimation;
        String prefix = null;
        if (preview != null) {
            int dot = preview.indexOf('.', "animation.".length());
            if (preview.startsWith("animation.") && dot > 0) prefix = preview.substring("animation.".length(), dot);
        } else {
            GirlAction action = girl.getCurrentAction();
            if (action.isBusy()) prefix = action.source;
        }
        if (prefix == null) return null;
        for (GirlType type : GirlType.values()) {
            if (!type.isCustom() && type.animationPrefix().equals(prefix)) return type;
        }
        return null;
    }

    @Nullable
    private static Vector3f pivot(GirlType type) {
        Vector3f known = BODY_PIVOTS.get(type);
        if (known != null) return known;
        ResourceLocation geo = type.geo(type.baseModelName());
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(geo);
        if (model == null) return null;
        GeoBone body = model.getBone("body").orElse(null);
        if (body == null) return null;
        Vector3f pivot = new Vector3f(body.getPivotX(), body.getPivotY(), body.getPivotZ());
        BODY_PIVOTS.put(type, pivot);
        return pivot;
    }
}
