package org.velvetlite.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvetlite.VelvetLite;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.menu.GirlMenu;

public final class VelvetMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, VelvetLite.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GirlMenu>> GIRL =
            MENUS.register("girl", () -> IMenuTypeExtension.create(GirlMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<GirlEditorMenu>> GIRL_EDITOR =
            MENUS.register("girl_editor", () -> IMenuTypeExtension.create(GirlEditorMenu::fromNetwork));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
