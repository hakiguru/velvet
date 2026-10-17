package org.velvetlite.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.velvetlite.client.ClientConfig;
import org.velvetlite.client.SceneCamera;
import org.velvetlite.client.render.layer.GirlArmorLayer;
import org.velvetlite.client.render.layer.PartnerSkinLayer;
import org.velvetlite.client.render.util.SoftNormalVertexConsumer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import org.velvetlite.client.model.AbstractGirlGeoModel;
import org.velvetlite.entity.girl.AbstractGirlEntity;

public abstract class AbstractGirlRenderer<T extends AbstractGirlEntity>
        extends GeoEntityRenderer<T> {

    protected AbstractGirlRenderer(EntityRendererProvider.Context ctx,
                                   AbstractGirlGeoModel<T> model,
                                   float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
        addRenderLayer(new GirlArmorLayer<>(this));
        addRenderLayer(new PartnerSkinLayer<>(this));
    }

    private static final double NAME_DISTANCE = 64.0;
    /** Name height above the partner's eyes: about where a vanilla name tag is above a player's eyes. */
    private static final double NAME_ABOVE_EYES = 0.6;
    /** Name height above the girl's head pivot (her neck) during scenes. */
    private static final double NAME_ABOVE_NECK = 0.8;
    private static final String HEAD_BONE = "head";

    private final SoftNormalVertexConsumer softNormals = new SoftNormalVertexConsumer();

    /**
     * Set while the girl is drawn inside a GUI. The inventory preview is lit from the front, so normals
     * softened towards "up" would make her dark there.
     */
    public static boolean renderingInGui;

    @Override
    public void actuallyRender(PoseStack poseStack, T animatable, BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                               float partialTick, int packedLight, int packedOverlay, int colour) {
        float softness = ClientConfig.SHADING_SOFTNESS.get().floatValue();
        if (softness > 0 && buffer != null && !renderingInGui) {
            buffer = softNormals.wrap(buffer, softness);
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    /**
     * Always named, also while the scene partner rides her (GeckoLib hides names of ridden entities); only
     * not for her partner looking out of the model's head, where the name would hang right in his face.
     */
    @Override
    public boolean shouldShowName(T girl) {
        Minecraft mc = Minecraft.getInstance();
        boolean partnerEyes = mc.player != null && mc.player.getUUID().equals(girl.getPartnerUUID())
                && SceneCamera.isLookingThroughPartnerEyes();
        return Minecraft.renderNames() && !partnerEyes && girl != mc.getCameraEntity() && !girl.isInvisibleTo(mc.player)
                && this.entityRenderDispatcher.distanceToSqr(girl) < NAME_DISTANCE * NAME_DISTANCE;
    }

    @Override
    public void render(T girl, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        super.render(girl, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        renderPartnerName(girl, partialTick, poseStack, bufferSource, packedLight);
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

    @Override
    protected float getDeathMaxRotation(T entity) {
        return 0.0F;
    }
}