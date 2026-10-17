package org.velvetlite.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import org.velvetlite.client.model.FixedGeoModel;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import org.velvetlite.VelvetLite;
import org.velvetlite.client.animation.GirlAnimationController;
import org.velvetlite.registry.VelvetItems;

@EventBusSubscriber(modid = VelvetLite.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            VelvetLite.LOGGER.info("VelvetLite client setup");

        });
    }

    /** F3+T: animations added or renamed in the files are picked up without restarting the game. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> GirlAnimationController.clearCache());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyMappings.register(event);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RendererRegistry.registerAll(event);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
    }

    private static <T extends Item & GeoItem> void geoItem(RegisterClientExtensionsEvent event, T item,
                                                           String model, String texture, String animation,
                                                           String... hiddenBones) {
        event.registerItem(new IClientItemExtensions() {
            private GeoItemRenderer<T> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new FixedGeoModel<>(model, texture, animation, hiddenBones));
                return renderer;
            }
        }, item);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "horny_meter"),
                new org.velvetlite.client.overlay.HornyMeterOverlay());
    }

    public static void postInit() {
        org.velvetlite.custommodel.CustomModelManager.loadAll();
    }
}