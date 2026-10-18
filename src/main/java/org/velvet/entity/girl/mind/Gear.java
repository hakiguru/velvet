package org.velvet.entity.girl.mind;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlLoot;

import java.util.function.Predicate;

/**
 * What she has in her hands and on her body, handled like a player handles the hotbar: the right thing goes
 * into her hand when she needs it (her best weapon when there's a fight, the right tool for a block, food to
 * eat, a bow to shoot), swapped with what was there, and her best armor and a shield are always put on.
 */
public final class Gear {

    private Gear() {}

    // ---- Judging items ----

    /** Damage of one full hit with it in her main hand (her bare fist is 1). */
    public static double attackDamage(ItemStack stack) {
        double damage = 1;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE)) damage += e.modifier().amount();
        }
        return damage;
    }

    /** Full hits a second with it: 4 bare-handed, 1.6 with a sword, about 1 with an axe. */
    public static double attackSpeed(ItemStack stack) {
        double speed = 4;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_SPEED)) speed += e.modifier().amount();
        }
        return Math.max(0.25, speed);
    }

    /** How much damage a second it does in her hand, enchantments counted roughly. */
    public static double dps(ItemStack stack) {
        double sharp = stack.isEnchanted() ? 1.1 : 1;
        return attackDamage(stack) * Math.min(attackSpeed(stack), 2.5) * sharp;
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        return !stack.isEmpty() && attackDamage(stack) > 2.5;
    }

    public static boolean isShield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem;
    }

    public static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    /** How fast it breaks the block, 1 for a bare hand; 0 if the block wouldn't drop anything with it. */
    public static float mineSpeed(ItemStack stack, BlockState state) {
        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) return 0;
        return Math.max(1, stack.getDestroySpeed(state));
    }

    // ---- Into her hands ----

    /** Her best melee weapon into her main hand, if she has one better than what's there. */
    public static void holdWeapon(AbstractGirlEntity girl) {
        ItemStack current = girl.getMainHandItem();
        int best = bestSlot(girl, s -> isMeleeWeapon(s) && dps(s) > dps(current) + 0.01, Gear::dps);
        if (best >= 0) swapIntoHand(girl, best, EquipmentSlot.MAINHAND);
    }

    /** The best tool she has for this block into her main hand; false if she can't get anything from it. */
    public static boolean holdTool(AbstractGirlEntity girl, BlockState state) {
        float here = mineSpeed(girl.getMainHandItem(), state);
        int best = bestSlot(girl, s -> mineSpeed(s, state) > here, s -> mineSpeed(s, state));
        if (best >= 0) {
            swapIntoHand(girl, best, EquipmentSlot.MAINHAND);
            return true;
        }
        return here > 0;
    }

    /** Something she can eat right now into her main hand. */
    public static boolean holdFood(AbstractGirlEntity girl, Predicate<ItemStack> food) {
        if (food.test(girl.getMainHandItem())) return true;
        int slot = bestSlot(girl, food, s -> 0);
        if (slot < 0) return false;
        swapIntoHand(girl, slot, EquipmentSlot.MAINHAND);
        return true;
    }

    /** A bow into her main hand, if she has one and arrows for it. */
    public static boolean holdBow(AbstractGirlEntity girl) {
        if (!hasArrows(girl)) return false;
        if (isBow(girl.getMainHandItem())) return true;
        int slot = bestSlot(girl, Gear::isBow, s -> s.isEnchanted() ? 1 : 0);
        if (slot < 0) return false;
        swapIntoHand(girl, slot, EquipmentSlot.MAINHAND);
        return true;
    }

    public static boolean hasArrows(AbstractGirlEntity girl) {
        return find(girl, s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)) >= 0;
    }

    public static boolean hasBow(AbstractGirlEntity girl) {
        return isBow(girl.getMainHandItem()) || find(girl, Gear::isBow) >= 0;
    }

    /** Takes one arrow from her inventory to shoot; empty if she has none. */
    public static ItemStack takeArrow(AbstractGirlEntity girl) {
        int slot = find(girl, s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW));
        if (slot < 0) return ItemStack.EMPTY;
        ItemStack arrow = girl.getBag().getItem(slot).split(1);
        girl.getBag().setChanged();
        return arrow;
    }

    /** A shield into her off hand (unless she holds a totem there). */
    public static void holdShield(AbstractGirlEntity girl) {
        ItemStack off = girl.getOffhandItem();
        if (isShield(off) || off.is(Items.TOTEM_OF_UNDYING)) return;
        int slot = find(girl, Gear::isShield);
        if (slot >= 0) swapIntoHand(girl, slot, EquipmentSlot.OFFHAND);
    }

    /** Her best armor on, piece by piece. */
    public static void wearBestArmor(AbstractGirlEntity girl) {
        SimpleContainer bag = girl.getBag();
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack stack = bag.getItem(i);
            if (!(stack.getItem() instanceof ArmorItem)) continue;
            EquipmentSlot slot = girl.getEquipmentSlotForItem(stack);
            ItemStack current = girl.getItemBySlot(slot);
            if (current.isEmpty() || girl.isBetterGear(stack, current)) swapIntoHand(girl, i, slot);
        }
    }

    /** Everything for a fight: armor, weapon, shield. */
    public static void prepareForFight(AbstractGirlEntity girl) {
        wearBestArmor(girl);
        holdWeapon(girl);
        holdShield(girl);
    }

    // ---- Inventory ----

    /** Swaps the stack in bag slot {@code slot} with what's in {@code equipment} (one armor piece at a time). */
    public static void swapIntoHand(AbstractGirlEntity girl, int slot, EquipmentSlot equipment) {
        SimpleContainer bag = girl.getBag();
        ItemStack incoming = bag.getItem(slot);
        ItemStack current = girl.getItemBySlot(equipment);
        if (equipment.isArmor() && incoming.getCount() > 1) {
            girl.setItemSlot(equipment, incoming.split(1));
            if (!current.isEmpty()) {
                ItemStack rest = GirlLoot.addToBag(girl, current);
                if (!rest.isEmpty()) girl.spawnAtLocation(rest);
            }
        } else {
            girl.setItemSlot(equipment, incoming);
            bag.setItem(slot, current);
        }
        bag.setChanged();
    }

    public static int find(AbstractGirlEntity girl, Predicate<ItemStack> test) {
        SimpleContainer bag = girl.getBag();
        for (int i = 0; i < girl.getBagCapacity(); i++) if (!bag.getItem(i).isEmpty() && test.test(bag.getItem(i))) return i;
        return -1;
    }

    private static int bestSlot(AbstractGirlEntity girl, Predicate<ItemStack> test, java.util.function.ToDoubleFunction<ItemStack> score) {
        SimpleContainer bag = girl.getBag();
        int best = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack s = bag.getItem(i);
            if (s.isEmpty() || !test.test(s)) continue;
            double v = score.applyAsDouble(s);
            if (v > bestScore) {
                bestScore = v;
                best = i;
            }
        }
        return best;
    }

    @Nullable
    public static ItemStack firstOf(AbstractGirlEntity girl, Predicate<ItemStack> test) {
        int slot = find(girl, test);
        return slot < 0 ? null : girl.getBag().getItem(slot);
    }
}
