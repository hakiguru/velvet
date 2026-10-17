package org.velvetlite.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.velvetlite.client.model.*;
import org.velvetlite.client.render.*;
import org.velvetlite.entity.girl.GirlRegistry;

public final class RendererRegistry {
    private RendererRegistry() {}

    public static void registerAll(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GirlRegistry.ELSIE.get(),
                ctx -> new ElsieRenderer(ctx));
    }
}