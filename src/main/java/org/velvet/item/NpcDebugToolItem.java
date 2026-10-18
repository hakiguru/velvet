package org.velvet.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.menu.GirlEditorMenu;

/**
 * The debug tool, creative only: the NPC editor with the tabs players don't get (instincts, character,
 * starting levels, damage and AI switches). On an NPC it opens on her, anywhere else on the one edited last.
 */
public class NpcDebugToolItem extends Item {

    public NpcDebugToolItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AbstractGirlEntity girl)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && allowed(serverPlayer)) GirlEditorMenu.open(serverPlayer, girl, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && allowed(serverPlayer)) GirlEditorMenu.open(serverPlayer, null, true);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    private static boolean allowed(ServerPlayer player) {
        if (player.isCreative()) return true;
        player.displayClientMessage(Component.translatable("editor.velvet.debug.creative_only").withStyle(ChatFormatting.RED), true);
        return false;
    }
}
