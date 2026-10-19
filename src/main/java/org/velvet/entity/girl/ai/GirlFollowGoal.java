package org.velvet.entity.girl.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlMode;

import java.util.EnumSet;

/**
 * Follows the owner while told to; runs to catch up unless she's out of breath. When she gets stuck she
 * gets herself out like a player: a block under her feet to climb up to him, digging through natural ground
 * that's in the way (with the right tool), a jump and a new path; only as a last resort she teleports to a
 * safe spot next to him. The distances come from her behavior settings.
 */
public class GirlFollowGoal extends Goal {

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
    private final org.velvet.entity.girl.mind.BlockWork work;
    private final org.velvet.entity.girl.survival.BlockClimber climber;
    @org.jetbrains.annotations.Nullable private BlockPos digging;
    private boolean climbing;

    public GirlFollowGoal(AbstractGirlEntity girl, double speedModifier) {
        this.girl = girl;
        this.speedModifier = speedModifier;
        this.work = new org.velvet.entity.girl.mind.BlockWork(girl);
        this.climber = new org.velvet.entity.girl.survival.BlockClimber(girl);
        this.parkour = new Parkour(girl);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!girl.isFollowing() || girl.getOwnerUUID() == null || girl.shouldBeAtTarget()) return false;
        this.owner = girl.level().getPlayerByUUID(girl.getOwnerUUID());
        double stop = girl.behavior().followStopDistance;
        return owner != null && owner.isAlive() && !owner.isSpectator() && girl.distanceToSqr(owner) > stop * stop;
    }

    @Override
    public boolean canContinueToUse() {
        double stop = girl.behavior().followStopDistance - 1;
        return owner != null && owner.isAlive() && !owner.isSpectator() && !girl.shouldBeAtTarget()
                && girl.isFollowing() && girl.distanceToSqr(owner) > stop * stop;
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
        work.reset();
        digging = null;
        if (climbing) {
            climbing = false;
            girl.survival().giveBack(climber.block().getBlock());
        }
        girl.getNavigation().stop();
        girl.setMode(GirlMode.WALK);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return digging != null || climbing || parkour.active();
    }

    private final Parkour parkour;

    @Override
    public void tick() {
        if (parkour.active()) {
            parkour.tick();
            return;
        }
        if (climbing) {
            if (climber.tick()) {
                climbing = false;
                if (climber.timedOut()) girl.survival().giveBack(climber.block().getBlock());
                pathCooldown = 0;
            }
            return;
        }
        if (digging != null) {
            org.velvet.entity.girl.mind.Gear.holdTool(girl, girl.level().getBlockState(digging));
            if (!org.velvet.entity.girl.mind.BlockWork.inReach(girl, digging) || work.breakStep(digging)) {
                digging = null;
                pathCooldown = 0;
            }
            return;
        }
        girl.getLookControl().setLookAt(owner, 10.0f, girl.getMaxHeadXRot());
        double dist = girl.distanceTo(owner);

        if (dist > girl.behavior().followTeleportDistance && tryTeleportToOwner()) return;

        boolean run = (owner.isSprinting() || dist > girl.behavior().followRunDistance) && girl.needs().canRun();
        girl.setMode(run ? GirlMode.RUN : GirlMode.WALK);
        double speed = speedModifier * (run ? 1.5 : 1.0);

        if (--pathCooldown <= 0) {
            pathCooldown = 10;
            girl.getNavigation().moveTo(owner, speed);
        }
        // no way round to him, and a gap right in front of her: she may jump it
        var way = girl.getNavigation().getPath();
        if (girl.tickCount % 5 == 0 && dist > 3 && (way == null || !way.canReach() || way.isDone())
                && parkour.tryStart(owner.position())) {
            return;
        }

        if (++stuckTimer >= STUCK_CHECK_INTERVAL) {
            stuckTimer = 0;
            boolean moving = girl.position().distanceTo(lastCheckPos) > STUCK_MOVEMENT;
            lastCheckPos = girl.position();
            // no way there at all counts as stuck too (he's up on a ledge, behind a wall)
            var path = girl.getNavigation().getPath();
            boolean noWay = path == null || !path.canReach() && path.getEndNode() != null
                    && path.getEndNode().asBlockPos().distSqr(owner.blockPosition()) > 4;
            if (moving && !noWay) {
                stuckCount = 0;
            } else if (noWay && parkour.tryStart(owner.position())) {
                stuckCount = 0;
            } else if (unstick()) {
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

    /**
     * Stuck on the way: up a block if he's above her (with a block from her inventory), or through the
     * ground in the way if it's natural and safe to take. False if neither helps.
     */
    private boolean unstick() {
        if (!girl.onGround() || !org.velvet.entity.girl.mind.BlockWork.mayGrief(girl)) return false;
        BlockPos at = girl.blockPosition();
        var level = girl.level();
        if (owner.getY() > girl.getY() + 1.5 && level.getBlockState(at.above(2)).getCollisionShape(level, at.above(2)).isEmpty()
                && girl.survival().countBuildingBlocks() > 0) {
            var block = girl.survival().takeBuildingBlock();
            if (block != null) {
                climber.start(block.defaultBlockState());
                climbing = true;
                return true;
            }
        }
        Vec3 to = owner.position().subtract(girl.position());
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.getNearest(to.x, 0, to.z);
        BlockPos front = at.relative(facing);
        BlockPos[] dig;
        if (owner.getY() > girl.getY() + 1.5 && !level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            // he's up a wall of natural ground and she has nothing to build with: steps into it, like a player
            // would cut them: room to jump (over her head), and the two blocks over the step in front of her
            dig = new BlockPos[]{at.above(2), front.above(), front.above(2)};
        } else {
            // the block in front of her, towards him, at her feet or head
            dig = new BlockPos[]{front.above(), front, owner.getY() < girl.getY() - 1.5 ? at.below() : at.above(2)};
        }
        for (BlockPos p : dig) {
            var state = level.getBlockState(p);
            if (state.isAir() || !org.velvet.entity.girl.mind.BlockWork.isBuildingMaterial(level, p, state)) continue;
            if (!org.velvet.entity.girl.mind.BlockWork.inReach(girl, p)) continue;
            girl.getNavigation().stop();
            digging = p.immutable();
            return true;
        }
        return false;
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
