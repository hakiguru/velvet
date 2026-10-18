package org.velvet.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;
import org.velvet.entity.girl.GirlRegistry;
import org.velvet.item.*;

public final class VelvetItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Velvet.MODID);
    public static final DeferredItem<NpcEditorWandItem>  NPC_EDITOR_WAND  = ITEMS.register("npc_editor_wand",  NpcEditorWandItem::new);
    /** Creative only: the editor with everything players don't get to change. */
    public static final DeferredItem<NpcDebugToolItem>   NPC_DEBUG_TOOL   = ITEMS.register("npc_debug_tool",   NpcDebugToolItem::new);

    /** A blank NPC, white all over like the egg itself. */
    public static final DeferredItem<GirlSpawnEggItem>   NPC_SPAWN_EGG    = ITEMS.register("npc_spawn_egg",
            () -> new GirlSpawnEggItem(GirlRegistry.NPC, 0xFFFFFF, 0xDADADA, new Item.Properties()));

    /** Only the icon of the mod's creative tab: not in any tab, no recipe. */
    public static final DeferredItem<Item>               LOVE_HEART       = ITEMS.register("love_heart", () -> new Item(new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        // the egg of 1.4 and older made Elsie; it makes a blank NPC now
        ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "elsie_spawn_egg"), NPC_SPAWN_EGG.getId());
    }
}
