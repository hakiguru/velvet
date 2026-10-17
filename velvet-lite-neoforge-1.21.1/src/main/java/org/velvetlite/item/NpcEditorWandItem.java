package org.velvetlite.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.velvetlite.entity.girl.AbstractGirlEntity;

public class NpcEditorWandItem extends Item {

    public NpcEditorWandItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AbstractGirlEntity girl)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;

        String code = girl.getCustomModelCode() + "$" + girl.getGirlUUID();
        player.sendSystemMessage(Component.literal(girl.getName().getString() + "'s model-code: ")
                .append(Component.literal(code).withStyle(ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("copied to clipboard").withStyle(ChatFormatting.ITALIC));

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            org.velvetlite.network.NetworkHandler.sendToPlayer(sp,
                    new org.velvetlite.network.packet.CopyToClipboardPacket(code));
        }
        return InteractionResult.SUCCESS;
    }
}