package org.velvetlite.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.velvetlite.client.model.ElsieGeoModel;
import org.velvetlite.entity.girl.girls.ElsieEntity;

public class ElsieRenderer extends AbstractGirlRenderer<ElsieEntity> {
    public ElsieRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ElsieGeoModel(), 0.6F);
    }
}