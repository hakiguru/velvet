package org.velvet.entity.girl.loot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;

import java.util.Locale;

/**
 * How much an item is worth to a girl, from junk to the rarest things in the game. With a full inventory she
 * only makes room for something of a higher caste than the lowest thing she carries.
 * <p>
 * Each caste is also an item tag ({@code velvet:caste/<name>}), so data packs can move items around;
 * items in no tag are sorted by the rules in {@link ItemCastes}.
 */
public enum ItemCaste {
    /** Cobwebs, rotten flesh, spider eyes, dirt and cobblestone, harmful potions. */
    JUNK,
    /** Iron, wood, wool, cheap armor and tools: most items. */
    COMMON,
    /** Gold and anything made of it, good potions, tipped arrows, music discs, enchanted gear. */
    VALUABLE,
    /** Diamonds, netherite, smithing templates, gear made of those, rare enchantments. */
    PRECIOUS,
    /** Dragon egg, nether star, elytra, beacon, totem and the like. */
    LEGENDARY;

    public final TagKey<Item> tag = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "caste/" + name().toLowerCase(Locale.ROOT)));

    public ItemCaste up(int steps) {
        return values()[Math.clamp(ordinal() + steps, 0, values().length - 1)];
    }

    public ItemCaste atLeast(ItemCaste other) {
        return other.ordinal() > ordinal() ? other : this;
    }

    public boolean above(ItemCaste other) {
        return ordinal() > other.ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static ItemCaste byId(String id) {
        for (ItemCaste caste : values()) if (caste.id().equals(id.trim().toLowerCase(Locale.ROOT))) return caste;
        return null;
    }
}
