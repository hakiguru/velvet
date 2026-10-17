package org.velvetlite.entity.girl.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlMode;

import java.util.EnumSet;

/** Follows the owner; repaths when stuck and, as a last resort, teleports to a safe spot next to him. */
public class GirlFollowGoal extends Goal {

    private static final double STOP_DISTANCE = 3.0;
    private static final double RUN_DISTANCE = 8.0;
    private static final double TELEPORT_DISTANCE = 24.0;
    /** Considered stuck after moving less than this many blocks in {@link #STUCK_CHECK_INTERVAL} ticks. */
    private static final double STUCK_MOVEMENT = 0.3;
    private static final int STUCK_CHECK_INTERVAL = 40;
    private static final int STUCK_CHECKS_BEFORE_TELEPORT = 3;

    private final AbstractGirlEntity girl;
    private final double speedModifier;

    private Player owner;
    private int pathCooldown;
    private int stuckTimer;
    private int stuckCount;
    private Vec3 lastCheckPos = Vec3.ZERO;

    public GirlFollowGoal(AbstractGirlEntity girl, double speedModifier) {
        this.girl = girl;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (girl.getOwnerUUID() == null || girl.shouldBeAtTarget()) return false;
        this.owner = girl.level().getPlayerByUUID(girl.getOwnerUUID());
        return owner != null && owner.isAlive() && !owner.isSpectator()
                && girl.distanceToSqr(owner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && !owner.isSpectator() && !girl.shouldBeAtTarget()
                && girl.getOwnerUUID() != null
                && girl.distanceToSqr(owner) > (STOP_DISTANCE - 1) * (STOP_DISTANCE - 1);
    }

    @Override
    public void start() {
        this.pathCooldown = 0;
        this.stuckTimer = 0;
        this.stuckCount = 0;
        this.lastCheckPos = girl.position();
    }

    @Override
    public void stop() {
        this.owner = null;
        girl.getNavigation().stop();
        girl.setMode(GirlMode.WALK);
    }

    @Override
    public void tick() {
        girl.getLookControl().setLookAt(owner, 10.0f, girl.getMaxHeadXRot());
        double dist = girl.distanceTo(owner);

        if (dist > TELEPORT_DISTANCE && tryTeleportToOwner()) return;

        boolean run = owner.isSprinting() || dist > RUN_DISTANCE;
        girl.setMode(run ? GirlMode.RUN : GirlMode.WALK);
        double speed = speedModifier * (run ? 1.5 : 1.0);

        if (--pathCooldown <= 0) {
            pathCooldown = 10;
            girl.getNavigation().moveTo(owner, speed);
        }

        if (++stuckTimer >= STUCK_CHECK_INTERVAL) {
            stuckTimer = 0;
            boolean moving = girl.position().distanceTo(lastCheckPos) > STUCK_MOVEMENT;
            lastCheckPos = girl.position();
            if (moving || !girl.getNavigation().isInProgress()) {
                stuckCount = 0;
            } else if (++stuckCount >= STUCK_CHECKS_BEFORE_TELEPORT) {
                stuckCount = 0;
                tryTeleportToOwner();
            } else {
                // try a fresh path; a jump often frees her from a block edge
                girl.getNavigation().stop();
                girl.getJumpControl().jump();
                pathCooldown = 0;
            }
        }
    }

    /** Teleports next to the owner onto solid, dry, free ground; false if there's no such spot. */
    private boolean tryTeleportToOwner() {
        BlockPos center = owner.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = girl.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = girl.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = center.offset(dx, dy, dz);
                if (isSafeSpot(pos)) {
                    girl.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, girl.getYRot(), girl.getXRot());
                    girl.getNavigation().stop();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSafeSpot(BlockPos pos) {
        PathType type = WalkNodeEvaluator.getPathTypeStatic(girl, pos);
        if (type != PathType.WALKABLE) return false;
        return girl.level().noCollision(girl, girl.getBoundingBox().move(
                pos.getX() + 0.5 - girl.getX(), pos.getY() - girl.getY(), pos.getZ() + 0.5 - girl.getZ()));
    }
}
