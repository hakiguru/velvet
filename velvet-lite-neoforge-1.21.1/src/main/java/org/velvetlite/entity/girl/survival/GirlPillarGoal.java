package org.velvetlite.entity.girl.survival;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import org.velvetlite.entity.girl.AbstractGirlEntity;

import java.util.EnumSet;

/**
 * Pillaring up (PILLAR): three blocks from her inventory under her feet, out of reach of monsters that can
 * only hit up close, and she waits up there until it's safe.
 */
public class GirlPillarGoal extends Goal {

    private static final int HEIGHT = 3;

    private final AbstractGirlEntity girl;
    private final BlockClimber climber;
    private int placed;
    private boolean climbing;

    public GirlPillarGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.climber = new BlockClimber(girl);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.PILLAR;
    }

    @Override
    public boolean canContinueToUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.PILLAR;
    }

    @Override
    public void start() {
        placed = 0;
        climbing = false;
        girl.survival().hidden = true;
    }

    @Override
    public void stop() {
        girl.survival().hidden = false;
        girl.survival().fleeFailures = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (climbing) {
            if (climber.tick()) {
                climbing = false;
                if (climber.timedOut()) {
                    girl.survival().giveBack(climber.block().getBlock());
                    placed = HEIGHT; // something's in the way: stay at this height
                } else {
                    placed++;
                }
            }
            return;
        }
        girl.getNavigation().stop();
        if (placed >= HEIGHT || !girl.onGround()) return;
        Block block = girl.survival().takeBuildingBlock();
        if (block == null) {
            placed = HEIGHT;
            return;
        }
        climber.start(block.defaultBlockState());
        climbing = true;
    }
}
