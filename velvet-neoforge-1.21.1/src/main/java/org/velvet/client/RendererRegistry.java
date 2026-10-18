package org.velvet.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.velvet.client.model.*;
import org.velvet.client.render.*;
import org.velvet.entity.girl.GirlRegistry;

public final class RendererRegistry {
    private RendererRegistry() {}

    public static void registerAll(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GirlRegistry.ELSIE.get(),
                ctx -> new ElsieRenderer(ctx));
    }
}