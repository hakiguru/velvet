package org.velvet.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.velvet.Velvet;
import org.velvet.registry.VelvetMenus;

@EventBusSubscriber(modid = Velvet.MODID, value = Dist.CLIENT)
public final class MenuScreensRegistry {
    private MenuScreensRegistry() {}


    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(VelvetMenus.GIRL.get(), org.velvet.client.gui.GirlScreen::new);
        event.register(VelvetMenus.GIRL_EDITOR.get(), org.velvet.client.gui.GirlEditorScreen::new);
    }
}