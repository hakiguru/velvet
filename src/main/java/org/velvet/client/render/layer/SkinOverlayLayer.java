package org.velvet.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.velvet.client.skin.SkinCache;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.skin.GirlSkin;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** A custom skin in overlay mode: the model drawn once more with it, over her own texture. */
public class SkinOverlayLayer<T extends AbstractGirlEntity> extends GeoRenderLayer<T> {

    public SkinOverlayLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T girl, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        GirlSkin skin = girl.getSkin();
        if (skin == null || skin.mode() != GirlSkin.Mode.OVERLAY) return;
        ResourceLocation texture = SkinCache.texture(skin);
        if (texture == null) return;
        RenderType type = RenderType.entityTranslucent(texture);
        getRenderer().reRender(model, poseStack, bufferSource, girl, type, bufferSource.getBuffer(type),
                partialTick, packedLight, packedOverlay, 0xFFFFFFFF);
    }
}
