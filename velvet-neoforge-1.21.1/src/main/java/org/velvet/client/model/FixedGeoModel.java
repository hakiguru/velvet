package org.velvet.client.model;

import net.minecraft.resources.ResourceLocation;
import org.velvet.Velvet;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.List;

/** GeoModel with fixed asset paths, for items whose files don't follow GeckoLib's default layout. */
public class FixedGeoModel<T extends GeoAnimatable> extends GeoModel<T> {

    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;
    private final List<String> hiddenBones;

    /** Paths are relative to assets/velvet/, e.g. "geo/allie/lamp.geo.json". */
    public FixedGeoModel(String model, String texture, String animation, String... hiddenBones) {
        this.hiddenBones = List.of(hiddenBones);
        this.model = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, model);
        this.texture = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, texture);
        this.animation = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, animation);
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return model;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animation;
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        for (String name : hiddenBones) {
            getBone(name).ifPresent(bone -> bone.setHidden(true));
        }
    }
}
