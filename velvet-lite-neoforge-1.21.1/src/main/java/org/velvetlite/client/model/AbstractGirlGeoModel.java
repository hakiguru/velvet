package org.velvetlite.client.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlType;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractGirlGeoModel<T extends AbstractGirlEntity> extends GeoModel<T> {

    /**
     * Bones hidden by default: "steve" is the player's body for paired actions (drawn separately with the
     * partner's skin, see PartnerSkinLayer); the armor* bones are armor overlays.
     * <p>
     * Facial expressions need no hiding: the alternative mouths, closed eyes, blush etc. sit inside the
     * head (z = -3) and animations push them in front of the face (z = -4) with a position offset.
     */
    public static final String PARTNER_BONE = "steve";
    public static final String PARTNER_HEAD_BONE = "Head2";
    /** How far the partner's head turns to follow where the player looks. */
    private static final float HEAD_MAX_YAW = 70;
    private static final float HEAD_MAX_PITCH = 50;
    private static final List<String> HIDDEN_BONES = List.of(
            PARTNER_BONE,
            "armorHelmet", "armorBoobs", "armorChest", "armorShoulderL", "armorShoulderR", "armorHip",
            "armorBootyL", "armorBootyR", "armorPantsUpL", "armorPantsUpR", "armorPantsLowL", "armorPantsLowR",
            "armorShoesL", "armorShoesR");
    /** How far animations push a face bone forward to show it. */
    private static final float FACE_SHOW_OFFSET = -1.0F;
    private static final int BLINK_INTERVAL = 80;
    private static final int BLINK_LENGTH = 3;

    private final GirlType type;
    private final ResourceLocation texture;
    private final ResourceLocation animation;
    // index = model variant (0 nude, 1 dressed, 2 armored); resolved lazily because
    // GeckoLib bakes models after the renderer is created
    private final ResourceLocation[] models = new ResourceLocation[3];
    /**
     * Girl id -> {rotX, rotY the head was left with, the look offsets included in them}. GeckoLib leaves a
     * bone as it is on frames it doesn't animate it, so the previous offset has to come off first.
     */
    private final Map<Integer, float[]> partnerHeadLook = new HashMap<>();

    protected AbstractGirlGeoModel(GirlType type) {
        this.type = type;
        String folder = type.assetFolder();
        this.texture = loc("textures/entity/" + folder + "/" + folder + ".png");
        this.animation = loc("animations/" + folder + "/" + folder + ".animation.json");
    }

    public GirlType getType() {
        return type;
    }

    @Override
    public ResourceLocation getModelResource(T girl) {
        int variant = Math.clamp(girl.getModelVariant(), 0, 2);
        ResourceLocation model = models[variant];
        if (model == null) {
            model = resolveModel(variant);
            models[variant] = model;
        }
        return model;
    }

    // Variant files are named inconsistently (elsie_dressed vs dressed) and some variants
    // have no dressed/armored model at all, so fall back to the base model.
    private ResourceLocation resolveModel(int variant) {
        String folder = type.assetFolder();
        String base = type.id();
        String[] candidates = switch (variant) {
            case 1  -> new String[]{base + "_dressed", "dressed", base};
            case 2  -> new String[]{base + "_armored", "armored", base};
            default -> new String[]{base};
        };
        for (String name : candidates) {
            ResourceLocation rl = loc("geo/" + folder + "/" + name + ".geo.json");
            if (GeckoLibCache.getBakedModels().containsKey(rl)) return rl;
        }
        VelvetLite.LOGGER.error("No geo model found for {} (variant {})", type.id(), variant);
        return loc("geo/" + folder + "/" + base + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T girl) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T girl) {
        return animation;
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

    /** Turns the "steve" head where the scene partner looks, within what a neck allows. */
    private void lookWithPartner(T girl, float partialTick) {
        GeoBone head = getBone(PARTNER_HEAD_BONE).orElse(null);
        GeoBone body = getBone(PARTNER_BONE).orElse(null);
        if (head == null || body == null) return;
        float[] last = partnerHeadLook.remove(girl.getId());
        float baseX = head.getRotX();
        float baseY = head.getRotY();
        if (last != null && baseX == last[0]) baseX -= last[2];
        if (last != null && baseY == last[1]) baseY -= last[3];

        Player partner = girl.getCurrentAction().isAttached() && girl.getPartnerUUID() != null
                ? girl.level().getPlayerByUUID(girl.getPartnerUUID()) : null;
        float offsetX = 0;
        float offsetY = 0;
        if (partner != null) {
            // the bone stands half turned in the model file (facing the girl); animations turn it further
            float bodyYaw = Mth.rotLerp(partialTick, girl.yBodyRotO, girl.yBodyRot) - body.getRotY() * Mth.RAD_TO_DEG;
            float yaw = Mth.clamp(Mth.wrapDegrees(partner.getViewYRot(partialTick) - bodyYaw), -HEAD_MAX_YAW, HEAD_MAX_YAW);
            float pitch = Mth.clamp(partner.getViewXRot(partialTick), -HEAD_MAX_PITCH, HEAD_MAX_PITCH);
            offsetX = -pitch * Mth.DEG_TO_RAD;
            offsetY = -yaw * Mth.DEG_TO_RAD;
        }
        head.setRotX(baseX + offsetX);
        head.setRotY(baseY + offsetY);
        if (partner != null) partnerHeadLook.put(girl.getId(), new float[]{baseX + offsetX, baseY + offsetY, offsetX, offsetY});
    }

    private static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, path);
    }
}
