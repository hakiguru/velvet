package org.velvetlite.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.velvetlite.VelvetLite;
import org.velvetlite.client.SceneCamera;
import org.velvetlite.client.model.AbstractGirlGeoModel;
import org.velvetlite.client.render.util.BoneVisibility;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;
import java.util.Set;

/**
 * Draws the scene partner: the girl's model contains a player body (the "steve" bone) animated together
 * with her and laid out for a 64x64 player skin. The main pass hides it; this pass renders only that bone
 * with the partner's own skin. The real player isn't drawn at all during a scene (see ClientEvents): he
 * looks out of this head, and the head turns where he looks (see AbstractGirlGeoModel).
 * <p>
 * The genitals inside that bone aren't part of a skin layout: their UVs point into the corner of
 * {@link #PARTNER_TEXTURE}, so they get their own pass with that texture.
 * <p>
 * It also reports where the partner's eyes are, so the riding partner's camera sits in that head.
 */
public class PartnerSkinLayer<T extends AbstractGirlEntity> extends GeoRenderLayer<T> {

    public static final String HEAD_BONE = "Head2";
    private static final ResourceLocation PARTNER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "textures/player/steve.png");
    /** Drawn with {@link #PARTNER_TEXTURE}, not with the skin (each with everything below it). */
    private static final List<String> GENITAL_BONES = List.of("cock", "ballL", "ballR");
    /** The player's eyes above the head bone's pivot (the neck): as high above the neck as on a vanilla player. */
    private static final double EYES_ABOVE_NECK = 1.62 - 1.407;
    // the model carries both arm shapes; only the one matching the skin is drawn
    private static final Set<String> WIDE_ARMS = Set.of("leftArmSteve", "rightArmSteve", "leftLowerArmSteve", "rightLowerArmSteve");
    private static final Set<String> SLIM_ARMS = Set.of("leftArmAlex", "rightArmAlex", "leftLowerArmAlex", "rightLowerArmAlex");

    public PartnerSkinLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T girl, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        girl.clientPartnerEyes = null;
        if (!girl.getCurrentAction().isAttached() || girl.getPartnerUUID() == null) return;
        Player partner = girl.level().getPlayerByUUID(girl.getPartnerUUID());
        if (!(partner instanceof AbstractClientPlayer clientPlayer)) return;
        boolean ownEyes = partner == Minecraft.getInstance().player && SceneCamera.isLookingThroughPartnerEyes();

        BoneVisibility visibility = BoneVisibility.showOnly(model,
                bone -> bone.getName().equals(AbstractGirlGeoModel.PARTNER_BONE));
        GENITAL_BONES.forEach(name -> visibility.hideTree(model, name));
        // looking out of this head: it would only block the view
        if (ownEyes) visibility.hideCubes(model, HEAD_BONE);
        PlayerSkin.Model arms = clientPlayer.getSkin().model();
        (arms == PlayerSkin.Model.SLIM ? WIDE_ARMS : SLIM_ARMS).forEach(name -> visibility.hideCubes(model, name));
        model.getBone(HEAD_BONE).ifPresent(b -> b.setTrackingMatrices(true));
        reRender(model, poseStack, bufferSource, girl, clientPlayer.getSkin().texture(), partialTick, packedLight, packedOverlay);
        visibility.restore();

        BoneVisibility genitals = BoneVisibility.showOnly(model, bone -> GENITAL_BONES.contains(bone.getName()));
        reRender(model, poseStack, bufferSource, girl, PARTNER_TEXTURE, partialTick, packedLight, packedOverlay);
        genitals.restore();

        GeoBone head = model.getBone(HEAD_BONE).orElse(null);
        if (head != null) {
            // only the position of GeckoLib's world matrix can be used: its rotation part has the identity
            // added to it (RenderUtil.translateMatrix), which would stretch any offset
            Vector3d neck = head.getWorldPosition();
            girl.clientPartnerEyes = new Vec3(neck.x, neck.y + EYES_ABOVE_NECK, neck.z);
        }
    }

    private void reRender(BakedGeoModel model, PoseStack poseStack, MultiBufferSource bufferSource, T girl,
                          ResourceLocation texture, float partialTick, int packedLight, int packedOverlay) {
        RenderType type = RenderType.entityTranslucent(texture);
        getRenderer().reRender(model, poseStack, bufferSource, girl, type, bufferSource.getBuffer(type),
                partialTick, packedLight, packedOverlay, 0xFFFFFFFF);
    }
}
