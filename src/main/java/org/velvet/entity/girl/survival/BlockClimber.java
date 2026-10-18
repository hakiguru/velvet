package org.velvet.entity.girl.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.velvet.entity.girl.AbstractGirlEntity;

/**
 * Climbing up one block the way players pillar: jump, and at the top of the jump put a block where her feet
 * were. One climb per call of {@link #start}; {@link #tick} returns true once the block is in place.
 */
public final class BlockClimber {

    private static final int TIMEOUT = 40;

    private final AbstractGirlEntity girl;
    private BlockPos target = BlockPos.ZERO;
    private BlockState block;
    private int ticks;
    private boolean jumped;

    public BlockClimber(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public void start(BlockState block) {
        this.block = block;
        this.target = girl.blockPosition();
        this.ticks = 0;
        this.jumped = false;
        // straight up from the middle of the block
        girl.setPos(target.getX() + 0.5, girl.getY(), target.getZ() + 0.5);
        girl.getNavigation().stop();
    }

    /** True when the block is placed; false while still jumping. Gives up (returns true) after a while. */
    public boolean tick() {
        ticks++;
        girl.setDeltaMovement(0, girl.getDeltaMovement().y, 0);
        if (!jumped && girl.onGround()) {
            girl.getJumpControl().jump();
            jumped = true;
            return false;
        }
        if (jumped && girl.getY() >= target.getY() + 1.0 && placeable()) {
            girl.level().setBlock(target, block, 3);
            girl.level().gameEvent(girl, GameEvent.BLOCK_PLACE, target);
            var sound = block.getSoundType(girl.level(), target, girl);
            girl.level().playSound(null, target, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
            girl.swing(InteractionHand.MAIN_HAND);
            girl.addSkillXp(org.velvet.entity.girl.GirlSkill.BUILDING, 1);
            return true;
        }
        if (jumped && girl.onGround() && ticks > 4) jumped = false; // landed without placing: jump again
        return ticks > TIMEOUT;
    }

    public boolean timedOut() {
        return ticks > TIMEOUT;
    }

    private boolean placeable() {
        if (!girl.level().getBlockState(target).canBeReplaced()) return false;
        return girl.level().getEntities(girl, new AABB(target)).stream().noneMatch(e -> e.blocksBuilding);
    }

    public BlockState block() {
        return block;
    }
}
