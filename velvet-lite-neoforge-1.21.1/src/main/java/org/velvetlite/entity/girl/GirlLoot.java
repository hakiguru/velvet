package org.velvetlite.entity.girl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.Tags;

import java.util.Map;

/**
 * Picking up items: anything thrown to her (or lying where she walks) goes into her inventory. Armor, a shield
 * and weapons go straight into her equipment slots when those are free or hold something worse, so nothing
 * in the inventory has to make room for them.
 * <p>
 * With a full inventory she keeps what's worth more to her right now, and drops the least valuable stack to
 * make room: normally resources and good enchantments, food on top of that when she's hungry, and weapons
 * and armor when she's in danger.
 */
public final class GirlLoot {

    private GirlLoot() {}

    public enum Mood { CALM, HUNGRY, IN_DANGER }

    /** Hungrier than this (food points), food is worth much more to her. */
    private static final int HUNGRY_BELOW = 14;
    /** Thrown away stacks can't be picked up again for this long, as when a player drops them. */
    private static final int DROP_PICKUP_DELAY = 40;

    private static final Map<Item, Double> WORTH = Map.ofEntries(
            Map.entry(Items.NETHERITE_BLOCK, 1080.0), Map.entry(Items.NETHERITE_INGOT, 120.0),
            Map.entry(Items.NETHERITE_SCRAP, 30.0), Map.entry(Items.ANCIENT_DEBRIS, 30.0),
            Map.entry(Items.DIAMOND_BLOCK, 360.0), Map.entry(Items.DIAMOND, 40.0),
            Map.entry(Items.EMERALD_BLOCK, 225.0), Map.entry(Items.EMERALD, 25.0),
            Map.entry(Items.GOLD_BLOCK, 90.0), Map.entry(Items.GOLD_INGOT, 10.0), Map.entry(Items.RAW_GOLD, 8.0),
            Map.entry(Items.IRON_BLOCK, 54.0), Map.entry(Items.IRON_INGOT, 6.0), Map.entry(Items.RAW_IRON, 5.0),
            Map.entry(Items.COPPER_INGOT, 1.5), Map.entry(Items.LAPIS_LAZULI, 2.0), Map.entry(Items.REDSTONE, 1.0),
            Map.entry(Items.QUARTZ, 1.5), Map.entry(Items.AMETHYST_SHARD, 1.5), Map.entry(Items.COAL, 1.0),
            Map.entry(Items.ENDER_PEARL, 6.0), Map.entry(Items.BLAZE_ROD, 6.0), Map.entry(Items.GHAST_TEAR, 10.0),
            Map.entry(Items.TOTEM_OF_UNDYING, 80.0), Map.entry(Items.NETHER_STAR, 200.0),
            Map.entry(Items.ELYTRA, 150.0), Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 150.0),
            Map.entry(Items.GOLDEN_APPLE, 25.0), Map.entry(Items.EXPERIENCE_BOTTLE, 4.0));

    public static Mood mood(AbstractGirlEntity girl) {
        if (girl.isInDanger()) return Mood.IN_DANGER;
        return girl.getFood() < HUNGRY_BELOW ? Mood.HUNGRY : Mood.CALM;
    }

    /** Takes as much of the item as she can or wants. */
    public static void pickUp(AbstractGirlEntity girl, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        int before = stack.getCount();
        ItemStack rest = equipIfBetter(girl, stack.copy());
        if (!rest.isEmpty()) rest = addToBag(girl, rest);
        if (!rest.isEmpty()) rest = swapForWorse(girl, rest);
        int taken = before - rest.getCount();
        if (taken <= 0) return;
        girl.take(entity, taken); // the pickup animation for everyone around
        stack.shrink(taken);
        if (stack.isEmpty()) entity.discard();
    }

    // ---- Equipment ----

    private static boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem;
    }

    /** Armor, a shield or a weapon put on right away; returns what's left of the stack. */
    private static ItemStack equipIfBetter(AbstractGirlEntity girl, ItemStack stack) {
        Item item = stack.getItem();
        EquipmentSlot slot;
        if (item instanceof ArmorItem) slot = girl.getEquipmentSlotForItem(stack);
        else if (item instanceof ShieldItem) slot = EquipmentSlot.OFFHAND;
        else if (isWeapon(stack)) slot = EquipmentSlot.MAINHAND;
        else return stack;

        ItemStack current = girl.getItemBySlot(slot);
        if (!current.isEmpty()) {
            // in her hands only a weapon or a shield gets replaced: anything else the player put there on purpose
            boolean sameKind = slot.isArmor()
                    || slot == EquipmentSlot.MAINHAND && isWeapon(current)
                    || slot == EquipmentSlot.OFFHAND && current.getItem() instanceof ShieldItem;
            if (!sameKind || !girl.isBetterGear(stack, current)) return stack;
        }
        girl.setItemSlot(slot, stack.split(1));
        if (!current.isEmpty()) {
            ItemStack old = addToBag(girl, current);
            if (!old.isEmpty()) drop(girl, old);
        }
        return stack;
    }

    // ---- Inventory ----

    /** Puts the stack into her open inventory slots (joining equal stacks first); returns what didn't fit. */
    public static ItemStack addToBag(AbstractGirlEntity girl, ItemStack stack) {
        SimpleContainer bag = girl.getBag();
        int open = girl.getBagCapacity();
        for (int i = 0; i < open && !stack.isEmpty(); i++) {
            ItemStack slot = bag.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < open && !stack.isEmpty(); i++) {
            if (bag.getItem(i).isEmpty()) bag.setItem(i, stack.split(stack.getMaxStackSize()));
        }
        bag.setChanged();
        return stack;
    }

    /** Full inventory: drops the least valuable stack if the new one is worth more to her right now. */
    private static ItemStack swapForWorse(AbstractGirlEntity girl, ItemStack stack) {
        Mood mood = mood(girl);
        SimpleContainer bag = girl.getBag();
        int worst = -1;
        double worstValue = Double.MAX_VALUE;
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            double v = value(girl, bag.getItem(i), mood);
            if (v < worstValue) {
                worstValue = v;
                worst = i;
            }
        }
        ItemStack incoming = stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
        if (worst < 0 || value(girl, incoming, mood) <= worstValue) return stack;
        drop(girl, bag.getItem(worst));
        bag.setItem(worst, incoming);
        bag.setChanged();
        stack.shrink(incoming.getCount());
        return stack;
    }

    private static void drop(AbstractGirlEntity girl, ItemStack stack) {
        ItemEntity dropped = girl.spawnAtLocation(stack.copy());
        if (dropped != null) dropped.setPickUpDelay(DROP_PICKUP_DELAY);
    }

    // ---- Worth ----

    /** What a whole stack is worth to her in this mood. */
    public static double value(AbstractGirlEntity girl, ItemStack stack, Mood mood) {
        if (stack.isEmpty()) return 0;
        double each = baseValue(stack);

        FoodProperties food = stack.getFoodProperties(girl);
        if (food != null && food.nutrition() > 0) {
            double filling = food.nutrition() + food.saturation();
            if (GirlNeeds.isHarmful(stack, food)) filling *= 0.2;
            // she always keeps some food over plain blocks; hungry, food comes first
            each += filling * (mood == Mood.HUNGRY ? 6 : 2);
        }
        if (mood == Mood.IN_DANGER && isGear(stack)) each = each * 4 + 20;
        return each * stack.getCount();
    }

    private static boolean isGear(ItemStack stack) {
        Item item = stack.getItem();
        return isWeapon(stack) || item instanceof ArmorItem || item instanceof ShieldItem
                || item instanceof BowItem || item instanceof CrossbowItem || stack.is(Items.TOTEM_OF_UNDYING);
    }

    /** One item, regardless of mood: rare materials, durable gear and enchantments are worth the most. */
    private static double baseValue(ItemStack stack) {
        double value = switch (stack.getRarity()) {
            case COMMON -> 0.25;
            case UNCOMMON -> 4;
            case RARE -> 12;
            case EPIC -> 30;
        };
        Double listed = WORTH.get(stack.getItem());
        if (listed != null) {
            value = Math.max(value, listed);
        } else if (stack.is(Tags.Items.GEMS)) {
            value = Math.max(value, 6);
        } else if (stack.is(Tags.Items.INGOTS)) {
            value = Math.max(value, 5);
        } else if (stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS)) {
            value = Math.max(value, 3);
        } else if (stack.is(Tags.Items.NUGGETS)) {
            value = Math.max(value, 0.6);
        }
        if (stack.isDamageableItem()) {
            // better materials last longer; worn out gear is worth less
            double durability = stack.getMaxDamage();
            value += 3 + durability / 100 * (1 - stack.getDamageValue() / durability);
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            value += (e.getKey().is(EnchantmentTags.CURSE) ? -4 : 8) * e.getIntValue();
        }
        return Math.max(0.05, value);
    }
}
