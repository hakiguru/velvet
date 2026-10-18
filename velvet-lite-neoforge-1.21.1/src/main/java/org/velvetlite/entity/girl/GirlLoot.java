package org.velvetlite.entity.girl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.velvetlite.entity.girl.loot.ItemCaste;
import org.velvetlite.entity.girl.loot.ItemCastes;
import org.velvetlite.entity.girl.loot.LootContext;

/**
 * Picking up items: anything thrown to her (or lying where she walks) goes into her inventory. Armor, a shield
 * and weapons go straight into her equipment slots when those are free or hold something worse, so nothing
 * in the inventory has to make room for them.
 * <p>
 * With a full inventory she sorts by {@link ItemCaste}: for an item of a higher caste than the lowest one she
 * carries, she throws out the least worth of those lowest stacks; anything else stays on the ground. So with a
 * full inventory and a diamond, a smithing template and rotten flesh on the ground, she drops two of her
 * cheapest stacks for the diamond and the template and leaves the flesh.
 */
public final class GirlLoot {

    private GirlLoot() {}

    /** Thrown away stacks can't be picked up again for this long, as when a player drops them. */
    private static final int DROP_PICKUP_DELAY = 40;

    /** Takes as much of the item as she can or wants. */
    public static void pickUp(AbstractGirlEntity girl, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        int before = stack.getCount();
        ItemStack rest = equipIfBetter(girl, stack.copy());
        if (!rest.isEmpty()) rest = addToBag(girl, rest);
        if (!rest.isEmpty()) rest = swapForLowerCaste(girl, rest, LootContext.of(girl));
        int taken = before - rest.getCount();
        if (taken <= 0) return;
        girl.take(entity, taken); // the pickup animation for everyone around
        stack.shrink(taken);
        if (stack.isEmpty()) entity.discard();
    }

    /** Whether she'd take any of this stack now: it fits, it's better gear, or it outranks something she carries. */
    public static boolean wants(AbstractGirlEntity girl, ItemStack stack, LootContext context) {
        if (stack.isEmpty()) return false;
        if (gearSlot(girl, stack) != null) return true;
        if (fitsInBag(girl, stack)) return true;
        int worst = lowestSlot(girl, context);
        return worst >= 0 && ItemCastes.caste(context, stack).above(ItemCastes.caste(context, girl.getBag().getItem(worst)));
    }

    // ---- Equipment ----

    public static boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem;
    }

    /** The slot this stack would go to right away, or null if it's no better than what she wears or holds. */
    private static EquipmentSlot gearSlot(AbstractGirlEntity girl, ItemStack stack) {
        Item item = stack.getItem();
        EquipmentSlot slot;
        if (item instanceof ArmorItem) slot = girl.getEquipmentSlotForItem(stack);
        else if (item instanceof ShieldItem) slot = EquipmentSlot.OFFHAND;
        else if (isWeapon(stack)) slot = EquipmentSlot.MAINHAND;
        else return null;

        ItemStack current = girl.getItemBySlot(slot);
        if (current.isEmpty()) return slot;
        // in her hands only a weapon or a shield gets replaced: anything else the player put there on purpose
        boolean sameKind = slot.isArmor()
                || slot == EquipmentSlot.MAINHAND && isWeapon(current)
                || slot == EquipmentSlot.OFFHAND && current.getItem() instanceof ShieldItem;
        return sameKind && girl.isBetterGear(stack, current) ? slot : null;
    }

    /** Armor, a shield or a weapon put on right away; returns what's left of the stack. */
    private static ItemStack equipIfBetter(AbstractGirlEntity girl, ItemStack stack) {
        EquipmentSlot slot = gearSlot(girl, stack);
        if (slot == null) return stack;
        ItemStack current = girl.getItemBySlot(slot);
        girl.setItemSlot(slot, stack.split(1));
        if (!current.isEmpty()) {
            ItemStack old = addToBag(girl, current);
            if (!old.isEmpty()) drop(girl, old);
        }
        return stack;
    }

    // ---- Inventory ----

    private static boolean fitsInBag(AbstractGirlEntity girl, ItemStack stack) {
        SimpleContainer bag = girl.getBag();
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack slot = bag.getItem(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) return true;
        }
        return false;
    }

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

    /** Her open slot with the lowest caste, and of those the stack worth least; -1 if nothing's open. */
    private static int lowestSlot(AbstractGirlEntity girl, LootContext context) {
        SimpleContainer bag = girl.getBag();
        int worst = -1;
        ItemCaste worstCaste = null;
        double worstValue = Double.MAX_VALUE;
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack stack = bag.getItem(i);
            ItemCaste caste = ItemCastes.caste(context, stack);
            double value = worth(stack);
            if (worstCaste == null || worstCaste.above(caste) || caste == worstCaste && value < worstValue) {
                worst = i;
                worstCaste = caste;
                worstValue = value;
            }
        }
        return worst;
    }

    /** Full inventory: throws out the lowest stack for one of a higher caste; returns what's left of the stack. */
    private static ItemStack swapForLowerCaste(AbstractGirlEntity girl, ItemStack stack, LootContext context) {
        ItemCaste incoming = ItemCastes.caste(context, stack);
        SimpleContainer bag = girl.getBag();
        while (!stack.isEmpty()) {
            int worst = lowestSlot(girl, context);
            if (worst < 0 || !incoming.above(ItemCastes.caste(context, bag.getItem(worst)))) break;
            drop(girl, bag.getItem(worst));
            bag.setItem(worst, stack.split(stack.getMaxStackSize()));
        }
        bag.setChanged();
        return stack;
    }

    private static void drop(AbstractGirlEntity girl, ItemStack stack) {
        ItemEntity dropped = girl.spawnAtLocation(stack.copy());
        if (dropped != null) dropped.setPickUpDelay(DROP_PICKUP_DELAY);
    }

    // ---- Worth within a caste ----

    /**
     * What a whole stack is worth next to others of its caste, to pick which one to throw out: more items,
     * durable and enchanted gear and rarer items are worth more.
     */
    public static double worth(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        double each = switch (stack.getRarity()) {
            case COMMON -> 1;
            case UNCOMMON -> 4;
            case RARE -> 12;
            case EPIC -> 30;
        };
        if (stack.isDamageableItem()) {
            double durability = stack.getMaxDamage();
            each += 3 + durability / 100 * (1 - stack.getDamageValue() / durability);
        }
        for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            each += (e.getKey().is(EnchantmentTags.CURSE) ? -4 : 8) * e.getIntValue();
        }
        // a full stack of something is worth more than one of it, but not 64 times as much
        return Math.max(0.05, each) * (1 + Math.sqrt(stack.getCount() - 1));
    }
}
