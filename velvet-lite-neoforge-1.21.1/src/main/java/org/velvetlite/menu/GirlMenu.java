package org.velvetlite.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlManager;
import org.velvetlite.registry.VelvetMenus;

/** The girl's menu: her armor (worn for real), her 9-slot bag and the player's inventory. */
public class GirlMenu extends AbstractContainerMenu {

    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    // slot index ranges
    private static final int ARMOR_START = 0;
    private static final int BAG_START = ARMOR_START + ARMOR.length;
    private static final int PLAYER_START = BAG_START + AbstractGirlEntity.BAG_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;

    // layout, shared with GirlScreen
    public static final int ARMOR_X = 8, ARMOR_Y = 18;
    public static final int BAG_X = 8, BAG_Y = 118;
    public static final int PLAYER_X = 8, PLAYER_Y = 150, HOTBAR_Y = 208;

    @Nullable private final AbstractGirlEntity girl;

    public GirlMenu(int id, Inventory playerInv, @Nullable AbstractGirlEntity girl) {
        super(VelvetMenus.GIRL.get(), id);
        this.girl = girl;
        Container bag = girl != null ? girl.getBag() : new SimpleContainer(AbstractGirlEntity.BAG_SIZE);

        for (int i = 0; i < ARMOR.length; i++) {
            addSlot(new ArmorSlot(girl, ARMOR[i], i, ARMOR_X, ARMOR_Y + i * 18)
                    .setBackground(InventoryMenu.BLOCK_ATLAS, ARMOR_ICONS[i]));
        }
        for (int i = 0; i < AbstractGirlEntity.BAG_SIZE; i++) {
            addSlot(new Slot(bag, i, BAG_X + i * 18, BAG_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    /** Client side: the server sends the girl's entity id when opening the menu. */
    public static GirlMenu fromNetwork(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        var entity = playerInv.player.level().getEntity(buf.readVarInt());
        return new GirlMenu(id, playerInv, entity instanceof AbstractGirlEntity g ? g : null);
    }

    @Nullable
    public AbstractGirlEntity getGirl() {
        return girl;
    }

    @Override
    public boolean stillValid(Player player) {
        return girl != null && girl.isAlive() && !girl.getCurrentAction().isBusy()
                && GirlManager.canCommand(player, girl);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_START) {
            // from the girl to the player
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else {
            // armor goes straight onto the girl, everything else into her bag
            boolean moved = false;
            for (int i = 0; i < ARMOR.length && !moved; i++) {
                Slot armor = slots.get(ARMOR_START + i);
                if (!armor.hasItem() && armor.mayPlace(stack)) {
                    moved = moveItemStackTo(stack, ARMOR_START + i, ARMOR_START + i + 1, false);
                }
            }
            if (!moved && !moveItemStackTo(stack, BAG_START, PLAYER_START, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    /** A slot that reads and writes the girl's equipment directly. */
    private static class ArmorSlot extends Slot {
        @Nullable private final AbstractGirlEntity girl;
        private final EquipmentSlot equipment;

        ArmorSlot(@Nullable AbstractGirlEntity girl, EquipmentSlot equipment, int index, int x, int y) {
            super(new SimpleContainer(ARMOR.length), index, x, y);
            this.girl = girl;
            this.equipment = equipment;
        }

        @Override
        public ItemStack getItem() {
            return girl != null ? girl.getItemBySlot(equipment) : ItemStack.EMPTY;
        }

        @Override
        public void set(ItemStack stack) {
            if (girl != null) girl.setItemSlot(equipment, stack);
            setChanged();
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack current = getItem();
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack taken = current.split(amount);
            set(current);
            return taken;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return girl != null && girl.getEquipmentSlotForItem(stack) == equipment;
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            return stack.isEmpty() || player.isCreative()
                    || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
