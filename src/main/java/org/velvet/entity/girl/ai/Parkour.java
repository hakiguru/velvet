package org.velvet.entity.girl.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.psyche.Emotion;
import org.velvet.entity.girl.psyche.Trait;

/**
 * Jumping over gaps like a player (for now only on the way to her owner when there's no path): a run-up and a
 * sprint jump to a block two to four blocks away, level or one lower. Whether she dares depends on her: brave,
 * impulsive and playful girls jump, timid ones look for another way; glad she's bolder, scared she isn't.
 */
public final class Parkour {

    private final AbstractGirlEntity girl;
    @Nullable private Vec3 landing;
    private Vec3 takeOff = Vec3.ZERO;
    private int ticks;
    private boolean jumped;

    public Parkour(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public boolean active() {
        return landing != null;
    }

    /** How willing she is to jump right now, 0 to 1. */
    public float daring() {
        var p = girl.psyche().personality();
        float d = 0.45F * p.get(Trait.COURAGE) + 0.25F * p.get(Trait.IMPULSIVENESS) + 0.2F * p.get(Trait.PLAYFULNESS)
                + 0.1F * (1 - p.get(Trait.ANXIOUSNESS));
        d += 0.25F * girl.getEmotion(Emotion.JOY) - 0.4F * girl.getEmotion(Emotion.FEAR) - 0.2F * girl.getEmotion(Emotion.ANXIETY);
        return Mth.clamp(d, 0, 1);
    }

    /** Looks for a jump towards {@code to}; true if she's going for it. */
    public boolean tryStart(Vec3 to) {
        if (!girl.onGround() || girl.isInWater()) return false;
        Vec3 d = to.subtract(girl.position());
        Direction dir = Direction.getNearest(d.x, 0, d.z);
        if (dir.getAxis().isVertical()) return false;
        BlockPos at = girl.blockPosition();
        var level = girl.level();
        // a gap right ahead: no floor under the next block
        BlockPos next = at.relative(dir);
        if (!level.getBlockState(next.below()).getCollisionShape(level, next.below()).isEmpty()) return false;
        for (int dist = 2; dist <= 4; dist++) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos land = at.relative(dir, dist).offset(0, dy, 0);
                if (!standable(land) || !clearAbove(at, dir, dist)) continue;
                // the longer the jump, the more she has to dare
                float need = 0.25F + 0.12F * (dist - 2) + (dy == 0 ? 0.05F : 0);
                if (daring() < need) return false;
                landing = Vec3.atBottomCenterOf(land);
                takeOff = girl.position();
                ticks = 0;
                jumped = false;
                return true;
            }
        }
        return false;
    }

    /** One tick of the jump; false once it's over (landed, or gave up). */
    public boolean tick() {
        if (landing == null) return false;
        ticks++;
        Vec3 to = landing.subtract(girl.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        girl.getNavigation().stop();
        girl.getLookControl().setLookAt(landing.x, landing.y + 1, landing.z);
        girl.setYRot((float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90);
        girl.setSprinting(true);
        if (!jumped) {
            // a sprinting player's take-off: up and forward at once
            Vec3 push = flat.normalize().scale(0.42);
            girl.setDeltaMovement(push.x, 0.42, push.z);
            girl.hasImpulse = true;
            jumped = true;
        } else if (!girl.onGround()) {
            // keep the momentum in the air, as a sprint jump does
            Vec3 v = girl.getDeltaMovement();
            Vec3 want = flat.normalize().scale(Math.min(0.33, flat.length()));
            girl.setDeltaMovement(want.x, v.y, want.z);
        }
        boolean landed = jumped && girl.onGround() && ticks > 3;
        if (landed || ticks > 40 || girl.isInWater() || girl.isInLava()) {
            girl.setSprinting(false);
            landing = null;
            return false;
        }
        return true;
    }

    private boolean standable(BlockPos p) {
        var level = girl.level();
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getFluidState(below).isEmpty();
    }

    /** Room for her head all the way. */
    private boolean clearAbove(BlockPos at, Direction dir, int dist) {
        var level = girl.level();
        for (int i = 1; i < dist; i++) {
            BlockPos p = at.relative(dir, i);
            for (int h = 0; h <= 2; h++) {
                if (!level.getBlockState(p.above(h)).getCollisionShape(level, p.above(h)).isEmpty()) return false;
            }
        }
        return true;
    }
}
