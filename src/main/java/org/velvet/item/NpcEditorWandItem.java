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
import net.minecraft.world.level.Level;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;
import org.velvet.menu.GirlEditorMenu;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.CopyToClipboardPacket;

/**
 * The NPC Editor: used on an NPC (hers or, for an operator, anyone's) it opens the editor on her, and only her,
 * and breaks right away; it's good for one go. Sneaking on her copies her custom model code instead, which
 * doesn't use it up. Every NPC at once, and more, is the debug tool's editor.
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
            player.displayClientMessage(Component.translatable("editor.velvet.not_yours", girl.getName())
                    .withStyle(ChatFormatting.RED), true);
        } else if (GirlEditorMenu.open(serverPlayer, girl, false)) {
            // one use: it breaks in his hand
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, stack),
                        player.getX(), player.getEyeY() - 0.2, player.getZ(), 10, 0.15, 0.1, 0.15, 0.05);
            }
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("editor.velvet.wand.use_on_npc").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.pass(stack);
    }
}
