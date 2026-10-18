package org.velvet.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.velvet.client.render.GirlRenderer;
import org.velvet.entity.girl.GirlRegistry;

public final class RendererRegistry {
    private RendererRegistry() {}

    public static void registerAll(EntityRenderersEvent.RegisterRenderers event) {
        for (var type : GirlRegistry.all()) event.registerEntityRenderer(type, GirlRenderer::new);
    }
}
