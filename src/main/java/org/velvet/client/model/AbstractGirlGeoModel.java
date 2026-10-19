package org.velvet.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.velvet.Velvet;
import org.velvet.client.SceneCamera;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlType;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The model of every NPC: geometry, texture and animations come from her preset ({@link GirlType}). */
public class AbstractGirlGeoModel<T extends AbstractGirlEntity> extends GeoModel<T> {

    /**
     * Bones hidden by default: "steve" is the player's body for paired actions (drawn separately with the
     * partner's skin, see PartnerSkinLayer); the armor* bones are armor overlays.
     * <p>
     * Facial expressions need no hiding: the alternative mouths, closed eyes, blush etc. sit inside the
     * head (z = -3) and animations push them in front of the face (z = -4) with a position offset.
     */
    public static final String PARTNER_BONE = "steve";
    public static final String PARTNER_HEAD_BONE = "Head2";
    private static final List<String> HIDDEN_BONES = List.of(
            PARTNER_BONE,
            "armorHelmet", "armorBoobs", "armorChest", "armorShoulderL", "armorShoulderR", "armorHip",
            "armorBootyL", "armorBootyR", "armorPantsUpL", "armorPantsUpR", "armorPantsLowL", "armorPantsLowR",
            "armorShoesL", "armorShoesR");
    /** How far animations push a face bone forward to show it. */
    private static final float FACE_SHOW_OFFSET = -1.0F;
    private static final int BLINK_INTERVAL = 80;
    private static final int BLINK_LENGTH = 3;

    // per preset, index = model variant (0 nude, 1 dressed, 2 armored); resolved lazily because
    // GeckoLib bakes models after the renderer is created
    private final java.util.Map<GirlType, ResourceLocation[]> models = new java.util.IdentityHashMap<>();

    @Override
    public ResourceLocation getModelResource(T girl) {
        GirlType type = girl.getGirlType();
        int variant = Math.clamp(girl.getModelVariant(), 0, 2);
        if (type.isCustom()) {
            // its own model if it has one (and a dressed one too), else its base preset's
            org.velvet.client.preset.PresetCache.ensureBaked(type);
            boolean own = org.velvet.client.preset.PresetCache.has(type, org.velvet.preset.PresetFiles.MODEL);
            if (!own) type = type.base();
            else return variant > 0 && org.velvet.client.preset.PresetCache.has(type, org.velvet.preset.PresetFiles.DRESSED)
                    ? type.geo("dressed") : type.geo(type.id());
        }
        ResourceLocation[] variants = models.computeIfAbsent(type, t -> new ResourceLocation[3]);
        if (variants[variant] == null) variants[variant] = resolveModel(type, variant);
        return variants[variant];
    }

    // Variant files are named inconsistently (bia_dressed vs dressed) and some presets
    // have no dressed/armored model at all, so fall back to the base model.
    private static ResourceLocation resolveModel(GirlType type, int variant) {
        String base = type.baseModelName();
        String[] candidates = switch (variant) {
            case 1  -> new String[]{base + "_dressed", "dressed", base};
            case 2  -> new String[]{base + "_armored", "armored", base + "_dressed", "dressed", base};
            default -> new String[]{base};
        };
        for (String name : candidates) {
            ResourceLocation rl = type.geo(name);
            if (GeckoLibCache.getBakedModels().containsKey(rl)) return rl;
        }
        Velvet.LOGGER.error("No geo model found for {} (variant {})", type.id(), variant);
        return type.geo(base);
    }

    /** Her own texture, or a custom skin from the editor that replaces it (once it has arrived). */
    @Override
    public ResourceLocation getTextureResource(T girl) {
        org.velvet.skin.GirlSkin skin = girl.getSkin();
        if (skin != null && skin.mode() == org.velvet.skin.GirlSkin.Mode.REPLACE) {
            ResourceLocation custom = org.velvet.client.skin.SkinCache.texture(skin);
            if (custom != null) return custom;
        }
        return defaultTexture(girl.getGirlType());
    }


    /** The texture that comes with a preset, to export as a starting point for a skin. */
    public static ResourceLocation defaultTexture(GirlType type) {
        if (type.isCustom()) {
            org.velvet.client.preset.PresetCache.ensureBaked(type);
            if (!org.velvet.client.preset.PresetCache.has(type, org.velvet.preset.PresetFiles.TEXTURE)) return type.base().texture();
        }
        return type.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(T girl) {
        GirlType type = girl.getGirlType();
        if (type.isCustom() && !org.velvet.client.preset.PresetCache.has(type, org.velvet.preset.PresetFiles.ANIMATIONS)) {
            return type.base().animations();
        }
        return type.animations();
    }

    /** A custom preset's own animations, and its base preset's for whatever it doesn't have. */
    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(T girl) {
        GirlType type = girl.getGirlType();
        return type.isCustom() ? new ResourceLocation[]{type.base().animations()} : super.getAnimationResourceFallbacks(girl);
    }

    /**
     * Scenes and poses are shared by every preset: an animation named "animation.<preset>.<name>" comes
     * from that preset's file, whichever model plays it (they all have the same skeleton).
     */
    @Override
    public software.bernie.geckolib.animation.Animation getAnimation(T girl, String name) {
        software.bernie.geckolib.animation.Animation own = super.getAnimation(girl, name);
        if (own != null) return own;
        return org.velvet.client.animation.GirlAnimationController.sharedAnimation(name);
    }

    @Override
    public void setCustomAnimations(T girl, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(girl, instanceId, animationState);
        for (String name : HIDDEN_BONES) {
            getBone(name).ifPresent(bone -> bone.setHidden(true));
        }
        lookWithPartner(girl, animationState.getPartialTick());

        // Blink only while idle; during actions the animations drive the eyes themselves
        if (!girl.getCurrentAction().isBusy()) {
            boolean closed = (girl.tickCount + girl.getId() * 37) % BLINK_INTERVAL < BLINK_LENGTH;
            float z = closed ? FACE_SHOW_OFFSET : 0;
            for (String bone : new String[]{"closedL", "closedR", "closedLashL", "closedLashR"}) {
                getBone(bone).ifPresent(b -> b.setPosZ(z));
            }
        }
    }

    /**
     * Turns the "steve" head exactly where the scene partner looks, with no neck limits. The look is a world
     * direction while the bone rotates in its parent's space, and the parents (the whole model, "steve",
     * "Torso2") may be turned, bent or lying: so the head's rotation is the look with all of theirs taken
     * off. Rotating by the raw look angles instead twists the head as soon as the body isn't upright.
     */
    private void lookWithPartner(T girl, float partialTick) {
        GeoBone head = getBone(PARTNER_HEAD_BONE).orElse(null);
        if (head == null) return;
        Player partner = girl.getCurrentAction().isAttached() && girl.getPartnerUUID() != null
                ? girl.level().getPlayerByUUID(girl.getPartnerUUID()) : null;
        if (partner == null) return;
        // the side views orbit the camera with the mouse: the head then keeps its animated pose
        if (partner == Minecraft.getInstance().player && !SceneCamera.headFollowsLook()) return;

        // same order as the renderer: the model turned by the body yaw, then each bone by Z, Y, X
        float bodyYaw = Mth.rotLerp(partialTick, girl.yBodyRotO, girl.yBodyRot);
        Matrix3f parent = new Matrix3f().rotationY((180.0F - bodyYaw) * Mth.DEG_TO_RAD);
        Deque<GeoBone> ancestors = new ArrayDeque<>();
        for (GeoBone bone = head.getParent(); bone != null; bone = bone.getParent()) ancestors.push(bone);
        for (GeoBone bone : ancestors) parent.rotateZ(bone.getRotZ()).rotateY(bone.getRotY()).rotateX(bone.getRotX());

        // the head's face points to -Z: turn it to the view yaw, then tilt it by the pitch
        Matrix3f look = new Matrix3f()
                .rotationY((180.0F - partner.getViewYRot(partialTick)) * Mth.DEG_TO_RAD)
                .rotateX(-partner.getViewXRot(partialTick) * Mth.DEG_TO_RAD);
        Vector3f angles = parent.invert().mul(look).getEulerAnglesZYX(new Vector3f());
        head.setRotX(angles.x);
        head.setRotY(angles.y);
        head.setRotZ(angles.z);
    }

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(Velvet.MODID, path);
    }
}
