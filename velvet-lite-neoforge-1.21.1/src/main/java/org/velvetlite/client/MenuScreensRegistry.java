package org.velvetlite.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.velvetlite.VelvetLite;
import org.velvetlite.registry.VelvetMenus;

@EventBusSubscriber(modid = VelvetLite.MODID, value = Dist.CLIENT)
public final class MenuScreensRegistry {
    private MenuScreensRegistry() {}


    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(VelvetMenus.GIRL.get(), org.velvetlite.client.gui.GirlScreen::new);
        event.register(VelvetMenus.GIRL_EDITOR.get(), org.velvetlite.client.gui.GirlEditorScreen::new);
    }
}