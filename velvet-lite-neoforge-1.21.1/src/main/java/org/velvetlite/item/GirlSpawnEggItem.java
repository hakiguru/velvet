package org.velvetlite.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.velvetlite.entity.girl.AbstractGirlEntity;

import java.util.function.Supplier;

/** A spawn egg whose girl belongs to the player who used it. */
public class GirlSpawnEggItem extends DeferredSpawnEggItem {

    public GirlSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight, Properties props) {
        super(type, background, highlight, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        AbstractGirlEntity.SPAWNING_PLAYER.set(context.getPlayer());
        try {
            return super.useOn(context);
        } finally {
            AbstractGirlEntity.SPAWNING_PLAYER.remove();
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        AbstractGirlEntity.SPAWNING_PLAYER.set(player);
        try {
            return super.use(level, player, hand);
        } finally {
            AbstractGirlEntity.SPAWNING_PLAYER.remove();
        }
    }
}
