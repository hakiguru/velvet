package org.velvet.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.velvet.client.model.ElsieGeoModel;
import org.velvet.entity.girl.girls.ElsieEntity;

public class ElsieRenderer extends AbstractGirlRenderer<ElsieEntity> {
    public ElsieRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ElsieGeoModel(), 0.6F);
    }
}