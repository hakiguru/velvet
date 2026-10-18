package org.velvetlite.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlBehavior;
import org.velvetlite.entity.girl.GirlManager;
import org.velvetlite.registry.VelvetMenus;

/**
 * The NPC editor (opened with the editor wand). It has no slots: it's a menu so the server knows who has it
 * open (she waits meanwhile) and closes it when she's out of reach, and so it can bring along her behavior
 * JSON, which only the server has.
 */
public class GirlEditorMenu extends AbstractContainerMenu {

    @Nullable private final AbstractGirlEntity girl;
    private final String behaviorJson;

    public GirlEditorMenu(int id, Inventory playerInv, @Nullable AbstractGirlEntity girl, String behaviorJson) {
        super(VelvetMenus.GIRL_EDITOR.get(), id);
        this.girl = girl;
        this.behaviorJson = behaviorJson;
        if (girl != null && !playerInv.player.level().isClientSide) girl.addMenuViewer(playerInv.player);
    }

    public static GirlEditorMenu fromNetwork(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        var entity = playerInv.player.level().getEntity(buf.readVarInt());
        String json = buf.readUtf(GirlBehavior.MAX_LENGTH * 2);
        return new GirlEditorMenu(id, playerInv, entity instanceof AbstractGirlEntity g ? g : null, json);
    }

    @Nullable
    public AbstractGirlEntity getGirl() {
        return girl;
    }

    /** Client side: her behavior as it was when the editor opened. */
    public String getBehaviorJson() {
        return behaviorJson;
    }

    @Override
    public boolean stillValid(Player player) {
        return girl != null && girl.isAlive() && !girl.getCurrentAction().isBusy() && GirlManager.canEdit(player, girl);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (girl != null && !player.level().isClientSide) girl.removeMenuViewer(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
