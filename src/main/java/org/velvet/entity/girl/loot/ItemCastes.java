package org.velvet.entity.girl.loot;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sorts every item into an {@link ItemCaste}: first by the caste tags (data packs can change them), then by
 * what the item is (gear by its material, potions by their effects, enchantments), and finally by the
 * situation the girl is in ({@link LootPriority}): food counts for more when she's hungry, gear when she's in
 * danger, and later things like wood in a cave.
 */
public final class ItemCastes {

    private ItemCastes() {}

    private static final ItemCaste[] HIGHEST_FIRST = {
            ItemCaste.LEGENDARY, ItemCaste.PRECIOUS, ItemCaste.VALUABLE, ItemCaste.JUNK, ItemCaste.COMMON};

    private static final List<LootPriority> PRIORITIES = new CopyOnWriteArrayList<>(List.of(
            LootPriority.HUNGER, LootPriority.DANGER, LootPriority.BEHAVIOR_OVERRIDES));

    /** Adds a rule that moves items up or down depending on the situation; applied after the built-in ones. */
    public static void registerPriority(LootPriority priority) {
        PRIORITIES.add(priority);
    }

    /** The caste of a stack for this girl right now. */
    public static ItemCaste caste(LootContext context, ItemStack stack) {
        ItemCaste caste = baseCaste(stack);
        for (LootPriority priority : PRIORITIES) caste = priority.adjust(context, stack, caste);
        return caste;
    }

    /** The caste of a stack regardless of the situation. */
    public static ItemCaste baseCaste(ItemStack stack) {
        if (stack.isEmpty()) return ItemCaste.JUNK;
        for (ItemCaste caste : HIGHEST_FIRST) {
            if (stack.is(caste.tag)) return withEnchantments(stack, caste);
        }
        return withEnchantments(stack, byKind(stack));
    }

    private static ItemCaste byKind(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SmithingTemplateItem) return ItemCaste.PRECIOUS;
        if (item instanceof ArmorItem armor) {
            var material = armor.getMaterial();
            if (material.is(ArmorMaterials.NETHERITE) || material.is(ArmorMaterials.DIAMOND)) return ItemCaste.PRECIOUS;
            if (material.is(ArmorMaterials.GOLD) || material.is(ArmorMaterials.TURTLE)) return ItemCaste.VALUABLE;
            return ItemCaste.COMMON;
        }
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            if (tier == Tiers.NETHERITE || tier == Tiers.DIAMOND) return ItemCaste.PRECIOUS;
            if (tier == Tiers.GOLD) return ItemCaste.VALUABLE;
            return ItemCaste.COMMON;
        }
        if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) return ItemCaste.VALUABLE;
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.hasEffects()) return byEffects(stack, potion);
        return switch (stack.getRarity()) {
            case EPIC -> ItemCaste.PRECIOUS;
            case RARE, UNCOMMON -> ItemCaste.VALUABLE;
            case COMMON -> ItemCaste.COMMON;
        };
    }

    /**
     * Good potions and tipped arrows are valuable; a potion that only harms whoever drinks it is junk, while a
     * harmful splash potion or arrow is still a weapon.
     */
    private static ItemCaste byEffects(ItemStack stack, PotionContents potion) {
        boolean good = false, bad = false;
        for (MobEffectInstance effect : potion.getAllEffects()) {
            if (effect.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        if (good && !bad) return ItemCaste.VALUABLE;
        if (good) return ItemCaste.COMMON;
        return stack.is(Items.POTION) ? ItemCaste.JUNK : ItemCaste.COMMON;
    }

    /**
     * Enchanted things are worth at least VALUABLE; rare enchantments (treasure ones like mending, or the
     * highest level of one with several levels) make them PRECIOUS. Only curses make nothing better.
     */
    private static ItemCaste withEnchantments(ItemStack stack, ItemCaste caste) {
        boolean enchanted = false, rare = false;
        for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            Holder<Enchantment> enchantment = e.getKey();
            if (enchantment.is(EnchantmentTags.CURSE)) continue;
            enchanted = true;
            int max = enchantment.value().getMaxLevel();
            if (enchantment.is(EnchantmentTags.TREASURE) || max >= 3 && e.getIntValue() >= max) rare = true;
        }
        if (rare) return caste.atLeast(ItemCaste.PRECIOUS);
        if (enchanted) return caste.atLeast(ItemCaste.VALUABLE);
        return caste;
    }

    /** Weapons, armor and whatever keeps her alive in a fight. */
    public static boolean isGear(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof ArmorItem || item instanceof ShieldItem || item instanceof BowItem
                || item instanceof CrossbowItem || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }
}
