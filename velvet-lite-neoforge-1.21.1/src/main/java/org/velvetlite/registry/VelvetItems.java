package org.velvetlite.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.GirlRegistry;
import org.velvetlite.entity.girl.GirlType;
import org.velvetlite.item.*;

import java.util.function.Supplier;

public final class VelvetItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VelvetLite.MODID);
    public static final DeferredItem<NpcEditorWandItem>  NPC_EDITOR_WAND  = ITEMS.register("npc_editor_wand",  NpcEditorWandItem::new);

    public static final DeferredItem<DeferredSpawnEggItem> ELSIE_SPAWN_EGG     = spawnEgg(GirlType.ELSIE,     GirlRegistry.ELSIE);

    private static DeferredItem<DeferredSpawnEggItem> spawnEgg(GirlType type, Supplier<? extends EntityType<? extends Mob>> entity) {
        int base = type.spawnEggColor();
        int spots = (base >> 1) & 0x7F7F7F; // same hue, half brightness
        return ITEMS.register(type.id() + "_spawn_egg",
                () -> new DeferredSpawnEggItem(entity, base, spots, new Item.Properties()));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}