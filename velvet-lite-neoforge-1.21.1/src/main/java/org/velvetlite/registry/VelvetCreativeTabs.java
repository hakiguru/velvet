package org.velvetlite.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvetlite.VelvetLite;

public final class VelvetCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VelvetLite.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.velvet_lite"))
                    .icon(() -> new ItemStack(VelvetItems.ELSIE_SPAWN_EGG.get()))
                    .displayItems((params, output) -> {
                        output.accept(VelvetItems.NPC_EDITOR_WAND.get());
                        output.accept(VelvetItems.ELSIE_SPAWN_EGG.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}