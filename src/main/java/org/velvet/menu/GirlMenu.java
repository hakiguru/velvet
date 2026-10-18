package org.velvet.menu;

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
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;
import org.velvet.registry.VelvetMenus;

/**
 * The girl's menu: her armor and the items in her hands (worn and held for real), her inventory and the
 * player's. Her inventory shows as many slots as her capacity level opens, each row centred; the others
 * aren't there at all. The screen has tabs; slots only show and take clicks on the inventory tab.
 */
public class GirlMenu extends AbstractContainerMenu {

    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
    public static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final ResourceLocation[] HAND_ICONS = {
            ResourceLocation.withDefaultNamespace("item/empty_slot_sword"), InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};

    // slot index ranges
    private static final int ARMOR_START = 0;
    private static final int HANDS_START = ARMOR_START + ARMOR.length;
    private static final int BAG_START = HANDS_START + HANDS.length;
    private static final int PLAYER_START = BAG_START + AbstractGirlEntity.BAG_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;

    // layout, shared with GirlScreen
    public static final int ARMOR_X = 8, ARMOR_Y = 18;
    public static final int HANDS_X = 80, HANDS_Y = 54;
    public static final int BAG_X = 102, BAG_Y = 29, BAG_COLUMNS = 9;
    public static final int WIDTH = BAG_X + BAG_COLUMNS * 18 + 7;
    public static final int PLAYER_X = (WIDTH - 9 * 18) / 2 + 1, PLAYER_Y = 114, HOTBAR_Y = 172;

    @Nullable private final AbstractGirlEntity girl;
    /** Open inventory slots when the menu opened: they're laid out for that many. */
    private final int laidOut;
    /** Client side: whether the screen shows the inventory tab, the only one with slots. */
    private boolean slotsVisible = true;

    public GirlMenu(int id, Inventory playerInv, @Nullable AbstractGirlEntity girl) {
        super(VelvetMenus.GIRL.get(), id);
        this.girl = girl;
        Container bag = girl != null ? girl.getBag() : new SimpleContainer(AbstractGirlEntity.BAG_SIZE);

        for (int i = 0; i < ARMOR.length; i++) {
            addSlot(new EquipmentSlotSlot(girl, ARMOR[i], ARMOR_X, ARMOR_Y + i * 18)
                    .setBackground(InventoryMenu.BLOCK_ATLAS, ARMOR_ICONS[i]));
        }
        for (int i = 0; i < HANDS.length; i++) {
            addSlot(new EquipmentSlotSlot(girl, HANDS[i], HANDS_X, HANDS_Y + i * 18)
                    .setBackground(InventoryMenu.BLOCK_ATLAS, HAND_ICONS[i]));
        }
        laidOut = girl != null ? girl.getBagCapacity() : 0;
        for (int i = 0; i < AbstractGirlEntity.BAG_SIZE; i++) {
            int row = i / BAG_COLUMNS;
            int inRow = Math.clamp(laidOut - row * BAG_COLUMNS, 1, BAG_COLUMNS);
            int x = BAG_X + (BAG_COLUMNS - inRow) * 9 + i % BAG_COLUMNS * 18;
            addSlot(new BagSlot(bag, i, x, BAG_Y + row * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new TabSlot(playerInv, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new TabSlot(playerInv, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
        if (girl != null && !playerInv.player.level().isClientSide) girl.addMenuViewer(playerInv.player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (girl != null && !player.level().isClientSide) girl.removeMenuViewer(player);
    }

    /** Whether her inventory slot {@code index} is open at her current capacity level. */
    public boolean isBagSlotOpen(int index) {
        return girl != null && index < girl.getBagCapacity();
    }

    /** Client side, from the screen: hides the slots on the tabs without them. */
    public void setSlotsVisible(boolean visible) {
        this.slotsVisible = visible;
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

    /** A slot of her inventory: there once her capacity opens it (and the menu has been opened again). */
    public class BagSlot extends TabSlot {
        BagSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        public boolean isOpen() {
            return isBagSlotOpen(getContainerSlot());
        }

        @Override
        public boolean isActive() {
            return super.isActive() && isOpen() && getContainerSlot() < laidOut;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isOpen();
        }
    }

    private class TabSlot extends Slot {
        TabSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    /** A slot that reads and writes one of the girl's equipment slots (armor or hand) directly. */
    private class EquipmentSlotSlot extends TabSlot {
        @Nullable private final AbstractGirlEntity girl;
        private final EquipmentSlot equipment;

        EquipmentSlotSlot(@Nullable AbstractGirlEntity girl, EquipmentSlot equipment, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
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
            // anything goes into her hands, armor only where it's worn
            return girl != null && (equipment.getType() == EquipmentSlot.Type.HAND
                    || girl.getEquipmentSlotForItem(stack) == equipment);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            return stack.isEmpty() || player.isCreative()
                    || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        }

        @Override
        public int getMaxStackSize() {
            return equipment.getType() == EquipmentSlot.Type.HAND ? super.getMaxStackSize() : 1;
        }
    }
}
