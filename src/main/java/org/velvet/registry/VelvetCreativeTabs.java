package org.velvet.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;

public final class VelvetCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Velvet.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.velvet"))
                    .icon(() -> new ItemStack(VelvetItems.LOVE_HEART.get()))
                    .displayItems((params, output) -> {
                        output.accept(VelvetItems.NPC_EDITOR_WAND.get());
                        output.accept(VelvetItems.NPC_SPAWN_EGG.get());
                        output.accept(VelvetItems.NPC_DEBUG_TOOL.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}