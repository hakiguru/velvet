package org.velvet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityAttachment;
import org.velvet.client.gui.StatusIcons;
import org.velvet.client.gui.editor.ModelPicker;
import net.minecraft.util.FastColor;
import org.velvet.entity.girl.BoneTweaks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.velvet.client.ClientConfig;
import org.velvet.client.SceneCamera;
import org.velvet.client.render.layer.GirlArmorLayer;
import org.velvet.client.render.layer.GirlHeldItemLayer;
import org.velvet.client.render.layer.PartnerSkinLayer;
import org.velvet.client.render.util.CrouchPose;
import org.velvet.client.render.util.SoftNormalVertexConsumer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import org.velvet.client.model.AbstractGirlGeoModel;
import org.velvet.entity.girl.AbstractGirlEntity;

public abstract class AbstractGirlRenderer<T extends AbstractGirlEntity>
        extends GeoEntityRenderer<T> {

    protected AbstractGirlRenderer(EntityRendererProvider.Context ctx,
                                   AbstractGirlGeoModel<T> model,
                                   float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
        addRenderLayer(new GirlArmorLayer<>(this));
        addRenderLayer(new GirlHeldItemLayer<>(this));
        addRenderLayer(new PartnerSkinLayer<>(this));
        addRenderLayer(new org.velvet.client.render.layer.SkinOverlayLayer<>(this));
    }

    private static final double NAME_DISTANCE = 64.0;
    /** Hearts and food only show up close by. */
    private static final double STATUS_DISTANCE = 16.0;
    private static final double STATUS_ABOVE_NAME = 0.04;
    private static final int SELECTED_TINT = 0xFFFF9A9A;
    /** Name height above the partner's eyes: about where a vanilla name tag is above a player's eyes. */
    private static final double NAME_ABOVE_EYES = 0.6;
    /** Name height above the girl's head pivot (her neck) during scenes. */
    private static final double NAME_ABOVE_NECK = 0.8;
    private static final String HEAD_BONE = "head";

    private final SoftNormalVertexConsumer softNormals = new SoftNormalVertexConsumer();

    /**
     * Set while the girl is drawn inside a GUI. The inventory preview has its own lights, so the normals are
     * softened towards those instead of up (which would leave her face in the shade there), and she has no
     * name tag.
     */
    public static boolean renderingInGui;

    @Override
    public void actuallyRender(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                               float partialTick, int packedLight, int packedOverlay, int colour) {
        float softness = ClientConfig.SHADING_SOFTNESS.get().floatValue();
        if (softness > 0 && buffer != null) {
            buffer = softNormals.wrap(buffer, softness,
                    renderingInGui ? SoftNormalVertexConsumer.INVENTORY_LIGHT : SoftNormalVertexConsumer.WORLD_UP);
        }
        // looked up once for the whole model, not for each bone
        frameFit = org.velvet.client.scene.SceneFits.active(animatable);
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    /** The scene fit of the girl being drawn, see {@link org.velvet.client.scene.SceneFits}. */
    @org.jetbrains.annotations.Nullable private java.util.Map<String, BoneTweaks.Tweak> frameFit;

    /**
     * Always named, also while the scene partner rides her (GeckoLib hides names of ridden entities); only
     * not for her partner looking out of the model's head, where the name would hang right in his face, and
     * not in the menu's preview.
     */
    @Override
    public boolean shouldShowName(T girl) {
        Minecraft mc = Minecraft.getInstance();
        boolean partnerEyes = mc.player != null && mc.player.getUUID().equals(girl.getPartnerUUID())
                && SceneCamera.isLookingThroughPartnerEyes();
        return Minecraft.renderNames() && !renderingInGui && !partnerEyes && girl != mc.getCameraEntity() && !girl.isInvisibleTo(mc.player)
                && this.entityRenderDispatcher.distanceToSqr(girl) < NAME_DISTANCE * NAME_DISTANCE;
    }

    @Override
    public void render(T girl, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        super.render(girl, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        renderPartnerName(girl, partialTick, poseStack, bufferSource, packedLight);
        renderStatus(girl, poseStack, bufferSource, packedLight);
    }

    /** Her hearts and food above the name tag, like the player's HUD; not during scenes. */
    private void renderStatus(T girl, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (girl.getCurrentAction().isBusy() || !shouldShowName(girl)
                || this.entityRenderDispatcher.distanceToSqr(girl) > STATUS_DISTANCE * STATUS_DISTANCE) return;
        Vec3 nameTag = girl.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, girl.getViewYRot(1.0F));
        if (nameTag == null) return;
        poseStack.pushPose();
        // the name tag's top edge is half a block above its attachment point
        poseStack.translate(nameTag.x, nameTag.y + 0.5 + STATUS_ABOVE_NAME, nameTag.z);
        StatusIcons.renderInWorld(poseStack, bufferSource, this.entityRenderDispatcher.cameraOrientation(),
                girl.getHealth(), girl.getMaxHealth(), girl.getFood(), packedLight);
        poseStack.popPose();
    }

    /**
     * Bone changes from the editor (or its unsaved preview) and the sneaking pose on top of the animation,
     * only while this bone is drawn: shape, tint (for the bone and everything on it) and hiding. The bone
     * selected in the editor is highlighted.
     */
    @Override
    public void renderRecursively(PoseStack poseStack, T girl, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, int colour) {
        BoneTweaks tweaks = girl.clientPreviewTweaks != null ? girl.clientPreviewTweaks : girl.getBoneTweaks();
        BoneTweaks.Tweak t = tweaks.isEmpty() ? null : tweaks.get(bone.getName());
        // an animation made for another preset turns her body around that preset's body pivot
        if (bone.getName().equals(BODY_BONE)) {
            BoneTweaks.Tweak pivot = PivotMatch.body(girl, bone);
            if (pivot != null) t = combine(t, pivot);
        }
        // the scene's fit to her preset, on top
        BoneTweaks.Tweak f = frameFit == null ? null : frameFit.get(bone.getName());
        if (f != null && f.movesBone()) t = combine(t, f);
        if (t != null && t.tint != BoneTweaks.NO_TINT) colour = FastColor.ARGB32.multiply(colour, t.tint);
        if (bone.getName().equals(girl.clientSelectedBone)) colour = FastColor.ARGB32.multiply(colour, SELECTED_TINT);
        if (ModelPicker.active != null) parentPoses.put(bone, new Matrix4f(poseStack.last().pose()));
        CrouchPose.Delta crouch = crouching(girl) ? CrouchPose.of(bone.getName()) : null;
        if (t == null && crouch == null) {
            super.renderRecursively(poseStack, girl, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
            return;
        }
        boolean hidden = bone.isHidden(), childrenHidden = bone.isHidingChildren();
        if (t != null && t.hidden) bone.setHidden(true);
        if ((t == null || !t.movesBone()) && crouch == null) {
            try {
                super.renderRecursively(poseStack, girl, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                        packedLight, packedOverlay, colour);
            } finally {
                bone.setHidden(hidden);
                bone.setChildrenHidden(childrenHidden);
            }
            return;
        }
        float sx = bone.getScaleX(), sy = bone.getScaleY(), sz = bone.getScaleZ();
        float px = bone.getPosX(), py = bone.getPosY(), pz = bone.getPosZ();
        float rx = bone.getRotX(), ry = bone.getRotY(), rz = bone.getRotZ();
        if (t != null && t.movesBone()) {
            bone.updateScale(sx * t.scale()[0], sy * t.scale()[1], sz * t.scale()[2]);
            bone.updatePosition(px + t.offset()[0], py + t.offset()[1], pz + t.offset()[2]);
            bone.updateRotation(rx + t.rotation()[0] * Mth.DEG_TO_RAD, ry + t.rotation()[1] * Mth.DEG_TO_RAD,
                    rz + t.rotation()[2] * Mth.DEG_TO_RAD);
        }
        if (crouch != null) {
            bone.updatePosition(bone.getPosX() + crouch.posX(), bone.getPosY() + crouch.posY(), bone.getPosZ() + crouch.posZ());
            bone.updateRotation(bone.getRotX() + crouch.rotX(), bone.getRotY() + crouch.rotY(), bone.getRotZ() + crouch.rotZ());
        }
        try {
            super.renderRecursively(poseStack, girl, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        } finally {
            // the animation processor works from these values next frame: leave them as it set them
            bone.setScaleX(sx);
            bone.setScaleY(sy);
            bone.setScaleZ(sz);
            bone.setPosX(px);
            bone.setPosY(py);
            bone.setPosZ(pz);
            bone.setRotX(rx);
            bone.setRotY(ry);
            bone.setRotZ(rz);
            bone.setHidden(hidden);
            bone.setChildrenHidden(childrenHidden);
        }
    }

    private static final String BODY_BONE = "body";

    /** A bone change with another on top: scales multiply, offsets and turns add up; tint and hiding stay the first's. */
    private static BoneTweaks.Tweak combine(@org.jetbrains.annotations.Nullable BoneTweaks.Tweak base, BoneTweaks.Tweak more) {
        BoneTweaks.Tweak out = base == null ? BoneTweaks.Tweak.identity() : base.copy();
        for (int i = 0; i < 3; i++) {
            out.scale[i] *= more.scale[i];
            out.offset[i] += more.offset[i];
            out.rotation[i] += more.rotation[i];
        }
        return out;
    }

    /** Sneaking with the player-like pose drawn over her movement (presets with a crouch animation play that instead). */
    private static boolean crouching(AbstractGirlEntity girl) {
        return girl.isCrouching() && !girl.getCurrentAction().isBusy() && girl.clientPreviewAnimation == null
                && !org.velvet.client.animation.GirlAnimationController.hasCrouchAnimation(girl);
    }

    /** The matrix each bone's own movement starts from, for the editor's handles; only while it picks. */
    private final java.util.Map<GeoBone, Matrix4f> parentPoses = new java.util.IdentityHashMap<>();

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight,
                                  int packedOverlay, int colour) {
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        ModelPicker picker = ModelPicker.active;
        if (picker != null && !bone.isHidden()) picker.capture(poseStack, bone, parentPoses.remove(bone));
        else if (picker == null && !parentPoses.isEmpty()) parentPoses.clear();
    }

    /**
     * The scene partner isn't drawn himself (his body is the girl's "steve" bone), so his name goes above
     * that bone's head. Not for the local player looking out of it.
     */
    private void renderPartnerName(T girl, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight) {
        if (girl.clientPartnerEyes == null || girl.getPartnerUUID() == null || !Minecraft.renderNames()) return;
        Player partner = girl.level().getPlayerByUUID(girl.getPartnerUUID());
        Minecraft mc = Minecraft.getInstance();
        if (partner == null || partner.isInvisibleTo(mc.player)
                || partner == mc.player && !mc.gameRenderer.getMainCamera().isDetached()) return;

        // eyes are in world space at the girl's current (not interpolated) position
        drawName(girl.clientPartnerEyes.subtract(girl.position()).add(0, NAME_ABOVE_EYES, 0), partner.getDisplayName(),
                poseStack, bufferSource, packedLight);
    }

    /**
     * During scenes her body moves far from where a standing girl's head would be (and in doggy the partner
     * stands right on her spot), so the name follows her animated head instead.
     */
    @Override
    protected void renderNameTag(T girl, Component name, PoseStack poseStack, MultiBufferSource bufferSource,
                                 int packedLight, float partialTick) {
        GeoBone head = getGeoModel().getBone(HEAD_BONE).orElse(null);
        if (!girl.getCurrentAction().isBusy() || head == null || !head.isTrackingMatrices()) {
            super.renderNameTag(girl, name, poseStack, bufferSource, packedLight, partialTick);
            return;
        }
        Vector3d neck = head.getWorldPosition();
        drawName(new Vec3(neck.x - girl.getX(), neck.y - girl.getY() + NAME_ABOVE_NECK, neck.z - girl.getZ()), name,
                poseStack, bufferSource, packedLight);
    }

    @Override
    public void preRender(PoseStack poseStack, T girl, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, int colour) {
        super.preRender(poseStack, girl, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        model.getBone(HEAD_BONE).ifPresent(b -> b.setTrackingMatrices(true));
        // lay her flat while swimming, the way a player tips forward; eased by the vanilla swim amount
        if (!isReRender && !girl.getCurrentAction().isBusy() && girl.clientPreviewAnimation == null) {
            float swim = girl.getSwimAmount(partialTick);
            if (swim > 0) {
                // tip her flat, head forward, turning about her waist (about a block up); under water her look
                // pitch tilts her up or down like a player's crawl
                float pitch = girl.isUnderWater() ? girl.getViewXRot(partialTick) : 0;
                poseStack.translate(0, 0.9F, 0);
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(swim * (-90.0F + pitch)));
                poseStack.translate(0, -0.9F, 0);
            }
        }
    }

    /** A vanilla style name tag at {@code offset} from the entity's render origin. */
    private void drawName(Vec3 offset, Component name, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f pose = poseStack.last().pose();
        Font font = getFont();
        float x = -font.width(name) / 2.0F;
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(name, x, 0, 0x20FFFFFF, false, pose, bufferSource, Font.DisplayMode.SEE_THROUGH, background, packedLight);
        font.drawInBatch(name, x, 0, 0xFFFFFFFF, false, pose, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    /** Presets with see-through parts (the slime's body and dress) are blended, the others cut out. */
    @Override
    public RenderType getRenderType(T girl, net.minecraft.resources.ResourceLocation texture,
                                    @org.jetbrains.annotations.Nullable MultiBufferSource bufferSource, float partialTick) {
        return girl.getGirlType().translucent() ? RenderType.entityTranslucent(texture)
                : super.getRenderType(girl, texture, bufferSource, partialTick);
    }

    @Override
    protected float getDeathMaxRotation(T entity) {
        return 0.0F;
    }
}