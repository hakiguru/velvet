package org.velvet.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.velvet.client.model.AbstractGirlGeoModel;
import org.velvet.entity.girl.AbstractGirlEntity;

/** Draws every NPC, in whatever preset she wears. */
public class GirlRenderer extends AbstractGirlRenderer<AbstractGirlEntity> {
    public GirlRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AbstractGirlGeoModel<>(), 0.6F);
    }
}
