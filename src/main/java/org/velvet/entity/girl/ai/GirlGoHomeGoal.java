package org.velvet.entity.girl.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.EnumSet;

/** Walks to the target set by "Go home" (shouldBeAtTarget + targetVec) and clears the flag on arrival. */
public class GirlGoHomeGoal extends Goal {

    private static final double ARRIVE_DISTANCE_SQR = 2.0 * 2.0;

    private final AbstractGirlEntity girl;
    private final double speedModifier;
    private int pathCooldown;

    public GirlGoHomeGoal(AbstractGirlEntity girl, double speedModifier) {
        this.girl = girl;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return girl.shouldBeAtTarget();
    }

    @Override
    public void start() {
        this.pathCooldown = 0;
    }

    @Override
    public void stop() {
        girl.getNavigation().stop();
    }

    @Override
    public void tick() {
        Vec3 target = girl.getTargetVec();
        if (girl.distanceToSqr(target) <= ARRIVE_DISTANCE_SQR) {
            girl.setShouldBeAtTarget(false);
            return;
        }
        if (--this.pathCooldown <= 0) {
            this.pathCooldown = 20;
            girl.getNavigation().moveTo(target.x, target.y, target.z, this.speedModifier);
        }
    }
}
