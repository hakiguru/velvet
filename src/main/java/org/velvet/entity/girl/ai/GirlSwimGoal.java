package org.velvet.entity.girl.ai;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.EnumSet;

/**
 * Swimming like a player, so following underwater works: when she's in water with her owner under the surface
 * (or already under it herself, following him down), she swims straight towards him in three dimensions at a
 * player's swimming speed, overriding the current instead of being swept by it. Her air, and anything that
 * helps it (a turtle shell, water breathing), works on her exactly as on a player — she's an ordinary
 * {@code LivingEntity}. Out of water, or with no one to follow under it, this steps aside for the float and
 * escape goals.
 */
public class GirlSwimGoal extends net.minecraft.world.entity.ai.goal.Goal {

    /** A player crawling/swimming moves at about this, blocks per tick; sprinting in water is faster. */
    private static final double SWIM_SPEED = 0.11;
    private static final double SPRINT_SWIM_SPEED = 0.14;

    private final AbstractGirlEntity girl;
    private Player owner;

    public GirlSwimGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (girl.getCurrentAction().isBusy() || girl.isInLava() || !girl.isInWater() || !girl.isFollowing()) return false;
        if (girl.getOwnerUUID() == null) return false;
        owner = girl.level().getPlayerByUUID(girl.getOwnerUUID());
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return false;
        double stop = girl.behavior().followStopDistance;
        if (girl.distanceToSqr(owner) <= stop * stop) return false;
        // only when the swim is actually underwater: he's under the surface, or she is and he's near the water
        return owner.isUnderWater() || (girl.isUnderWater() && owner.distanceToSqr(girl) < 20 * 20);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        girl.getNavigation().stop();
    }

    @Override
    public void stop() {
        girl.setSprinting(false);
        if (girl.getPose() == Pose.SWIMMING && !girl.isVisuallySwimming()) girl.setPose(Pose.STANDING);
        owner = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // aim a little above his feet, where the body is
        Vec3 target = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        Vec3 to = target.subtract(girl.position().add(0, girl.getBbHeight() * 0.5, 0));
        double dist = to.length();
        girl.getLookControl().setLookAt(target.x, target.y, target.z);
        if (dist < 1.0E-3) return;
        Vec3 dir = to.scale(1.0 / dist);
        // close in fast, ease off right next to him so she doesn't shove him around
        boolean sprint = dist > 3.0;
        double speed = (sprint ? SPRINT_SWIM_SPEED : SWIM_SPEED) * Math.min(1.0, dist / 1.5);
        girl.setSprinting(sprint);
        // the swimming pose (flat, arms forward) once she's actually under
        if (girl.isUnderWater()) girl.setPose(Pose.SWIMMING);
        // steer straight to him, overriding the flow rather than adding to it
        Vec3 now = girl.getDeltaMovement();
        Vec3 wanted = dir.scale(speed);
        girl.setDeltaMovement(now.scale(0.4).add(wanted.scale(0.6)));
    }
}
