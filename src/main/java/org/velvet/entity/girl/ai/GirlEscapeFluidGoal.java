package org.velvet.entity.girl.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlMode;

import java.util.EnumSet;

/**
 * Out of lava at once, and out of water when she's been stuck in it: to the closest dry ground she can stand
 * on, swimming and jumping straight at it like a player would (the path finder gives up in deep water and
 * can't plan through lava at all).
 */
public class GirlEscapeFluidGoal extends Goal {

    private static final int RADIUS = 10;
    /** In water this long without getting anywhere: she's stuck. */
    private static final int STUCK_IN_WATER = 60;
    private static final int GIVE_UP = 200;

    private final AbstractGirlEntity girl;
    @Nullable private BlockPos shore;
    private int inWater;
    private Vec3 lastPos = Vec3.ZERO;
    private int timer;

    public GirlEscapeFluidGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (girl.getCurrentAction().isBusy()) return false;
        boolean lava = girl.isInLava();
        if (girl.isInWater() && !lava) {
            // moving on her own (swimming along a path)? then she isn't stuck
            if (girl.tickCount % 20 == 0) {
                inWater = girl.position().distanceTo(lastPos) < 0.8 ? inWater + 20 : 0;
                lastPos = girl.position();
            }
        } else {
            inWater = 0;
        }
        if (!lava && inWater < STUCK_IN_WATER) return false;
        shore = findShore();
        return shore != null;
    }

    @Override
    public boolean canContinueToUse() {
        return shore != null && timer < GIVE_UP && (girl.isInLava() || girl.isInWater() || !girl.onGround());
    }

    @Override
    public void start() {
        timer = 0;
        girl.setMode(GirlMode.RUN);
        girl.getNavigation().stop();
    }

    @Override
    public void stop() {
        shore = null;
        inWater = 0;
        girl.setMode(GirlMode.WALK);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        timer++;
        if (shore == null) return;
        Vec3 target = Vec3.atBottomCenterOf(shore);
        girl.getLookControl().setLookAt(target.x, target.y + 1, target.z);
        girl.getMoveControl().setWantedPosition(target.x, target.y, target.z, girl.isInLava() ? 2.0 : 1.5);
        // up the bank: out of the fluid you swim and jump
        if (girl.isInWater() || girl.isInLava() || girl.horizontalCollision) girl.getJumpControl().jump();
        if (girl.isInLava()) {
            // lava holds anyone back to a crawl; a player who knows what he's doing swims straight out at
            // full strength, and so does she
            Vec3 to = target.subtract(girl.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > 1.0E-4) flat = flat.normalize().scale(0.16);
            girl.setDeltaMovement(flat.x, Math.max(girl.getDeltaMovement().y, to.y > -0.5 ? 0.12 : 0.04), flat.z);
        }
        if (timer % 40 == 0) {
            BlockPos again = findShore();
            if (again != null) shore = again;
        }
    }

    /** The closest block next to the fluid she can stand on, dry, with room for her. */
    @Nullable
    private BlockPos findShore() {
        BlockPos at = girl.blockPosition();
        var level = girl.level();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    double d = p.distSqr(at) + Math.max(0, dy) * 2;
                    if (d >= bestDist || !standable(p)) continue;
                    best = p.immutable();
                    bestDist = d;
                }
            }
        }
        return best;
    }

    private boolean standable(BlockPos p) {
        var level = girl.level();
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && !level.getFluidState(below).is(FluidTags.LAVA)
                && level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty()
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FIRE);
    }
}
