package org.velvetlite.item;

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
import net.minecraft.world.level.Level;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlManager;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.CopyToClipboardPacket;

/**
 * Right click: the NPC editor. On a girl it opens on her (only for her owner, or an operator); anywhere else
 * on the girl edited last, with all of the player's girls as tabs. Sneaking on a girl copies her custom model
 * code, as before.
 */
public class NpcEditorWandItem extends Item {

    public NpcEditorWandItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AbstractGirlEntity girl)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            String code = girl.getCustomModelCode() + "$" + girl.getGirlUUID();
            player.sendSystemMessage(Component.literal(girl.getName().getString() + "'s model-code: ")
                    .append(Component.literal(code).withStyle(ChatFormatting.YELLOW)));
            player.sendSystemMessage(Component.literal("copied to clipboard").withStyle(ChatFormatting.ITALIC));
            NetworkHandler.sendToPlayer(serverPlayer, new CopyToClipboardPacket(code));
        } else if (!GirlManager.canEdit(player, girl)) {
            player.displayClientMessage(Component.translatable("editor.velvet_lite.not_yours", girl.getName())
                    .withStyle(ChatFormatting.RED), true);
        } else {
            girl.openEditor(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) GirlEditorMenu.open(serverPlayer, null);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
