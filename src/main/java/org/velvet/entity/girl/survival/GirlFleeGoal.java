package org.velvet.entity.girl.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlMode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Running away (FLEE), or to water when she burns (EXTINGUISH). Instead of a random spot away from the nearest
 * monster, she looks at places all around her and picks the one that's furthest from every threat, can be
 * reached without running past any of them, and ideally is out of their sight or near her owner. She plans
 * again when she gets there, when the route gets cut off and every couple of seconds, and keeps going for as
 * long as they're after her (the brain decides that), sprinting while she has the breath.
 */
public class GirlFleeGoal extends Goal {

    private static final int DIRECTIONS = 24;
    private static final int[] RADII = {8, 14, 20};
    /** From a warden: much further, it's fast and its sonic boom reaches far. */
    private static final int[] FAR_RADII = {14, 22, 30};
    private static final int PATHS_PER_PLAN = 8;
    private static final int REPLAN_INTERVAL = 40;
    /** A route closer than this to a threat is out; closer than {@link #UNCOMFORTABLE} costs points. */
    private static final double TOO_CLOSE = 2.5, UNCOMFORTABLE = 6;
    private static final double SPRINT = 1.5, HURRY = 1.2;

    private final AbstractGirlEntity girl;
    private int replanTimer;
    @Nullable private Vec3 goal;
    /** The way she ran last time: keeping to it beats turning back and forth. */
    private Vec3 heading = Vec3.ZERO;

    public GirlFleeGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    private SurvivalBrain.Tactic tactic() {
        return girl.survival().tactic();
    }

    @Override
    public boolean canUse() {
        return tactic() == SurvivalBrain.Tactic.FLEE || tactic() == SurvivalBrain.Tactic.EXTINGUISH;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        replanTimer = 0;
    }

    @Override
    public void stop() {
        girl.getNavigation().stop();
        girl.setMode(GirlMode.WALK);
        goal = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Threats threats = girl.survival().threats();
        Threats.Threat nearest = threats.nearest();
        boolean close = nearest != null && nearest.distanceTo(girl.position()) < 12;
        boolean sprint = (close || tactic() == SurvivalBrain.Tactic.EXTINGUISH) && girl.needs().canRun();
        girl.setMode(sprint ? GirlMode.RUN : GirlMode.WALK);
        double speed = sprint ? SPRINT : HURRY;
        girl.getNavigation().setSpeedModifier(speed);

        boolean arrived = girl.getNavigation().isDone();
        boolean cutOff = nearest != null && nearest.distanceTo(girl.position()) < 4 && goal != null
                && nearest.lastSeenAt.distanceTo(goal) < girl.position().distanceTo(goal);
        if (--replanTimer <= 0 || arrived || cutOff) {
            replanTimer = REPLAN_INTERVAL;
            if (tactic() == SurvivalBrain.Tactic.EXTINGUISH) planToWater(speed);
            else plan(speed);
        }
    }

    // ---- Escape routes ----

    private record Candidate(BlockPos pos, double estimate) {}

    private void plan(double speed) {
        List<Threats.Threat> threats = girl.survival().threats().within(40);
        if (threats.isEmpty()) return;
        Vec3 from = girl.position();
        // the average direction away from them, for a first sorting
        Vec3 away = Vec3.ZERO;
        for (Threats.Threat t : threats) {
            Vec3 d = from.subtract(t.lastSeenAt).multiply(1, 0, 1);
            double len = Math.max(1, d.length());
            away = away.add(d.scale(1 / (len * len)));
        }
        away = away.lengthSqr() < 1.0E-6 ? Vec3.ZERO : away.normalize();

        boolean apex = threats.stream().anyMatch(t -> t.kind == Threats.Kind.APEX);
        List<Candidate> candidates = new ArrayList<>();
        for (int r : apex ? FAR_RADII : RADII) {
            for (int i = 0; i < DIRECTIONS; i++) {
                double angle = Math.PI * 2 * i / DIRECTIONS + r * 0.37;
                Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                BlockPos pos = standableNear(from.add(dir.scale(r)));
                if (pos == null) continue;
                double estimate = minDistance(threats, Vec3.atBottomCenterOf(pos)) + dir.dot(away) * 6 - r * 0.1
                        + dir.dot(heading) * 2;
                candidates.add(new Candidate(pos, estimate));
            }
        }
        candidates.sort((a, b) -> Double.compare(b.estimate, a.estimate));

        Path best = null;
        double bestScore = -Double.MAX_VALUE;
        Player owner = girl.getOwnerUUID() == null ? null : girl.level().getPlayerByUUID(girl.getOwnerUUID());
        int tried = 0;
        int budget = PATHS_PER_PLAN + Math.min(8, girl.survival().fleeFailures * 4);
        for (Candidate c : candidates) {
            if (tried >= budget) break;
            tried++;
            Path path = girl.getNavigation().createPath(c.pos, 1);
            if (path == null || path.getNodeCount() == 0) continue;
            Node end = path.getEndNode();
            if (end == null || end.asBlockPos().distSqr(c.pos) > 9) continue;
            double along = Double.MAX_VALUE;
            for (int i = 0; i < path.getNodeCount(); i++) {
                Node n = path.getNode(i);
                along = Math.min(along, minDistance(threats, new Vec3(n.x + 0.5, n.y, n.z + 0.5)));
            }
            // the first steps can't help being near the threat she's running from
            if (along < TOO_CLOSE && path.getNodeCount() > 3) continue;
            Vec3 endPos = Vec3.atBottomCenterOf(end.asBlockPos());
            double score = minDistance(threats, endPos) - path.getNodeCount() * 0.15;
            if (along < UNCOMFORTABLE) score -= (UNCOMFORTABLE - along) * 3;
            if (outOfSight(threats, endPos)) score += 6;
            if (owner != null && owner.position().distanceTo(endPos) < 10) score += 4;
            if (girl.level().getFluidState(end.asBlockPos()).is(FluidTags.WATER)) score -= 5;
            // a dead end is a trap: ways on from there count
            score += openness(end.asBlockPos()) * 0.75;
            if (score > bestScore) {
                bestScore = score;
                best = path;
            }
        }
        if (best != null) {
            girl.survival().fleeFailures = 0;
            goal = Vec3.atBottomCenterOf(best.getTarget());
            Vec3 d = goal.subtract(from).multiply(1, 0, 1);
            heading = d.lengthSqr() < 1.0E-4 ? Vec3.ZERO : d.normalize();
            girl.getNavigation().moveTo(best, speed);
            return;
        }
        girl.survival().fleeFailures++;
        // nothing good: vanilla's random spot away from the worst one
        Threats.Threat worst = girl.survival().threats().worst();
        Vec3 fallback = worst == null ? null : DefaultRandomPos.getPosAway(girl, 16, 7, worst.lastSeenAt);
        if (fallback != null) {
            goal = fallback;
            girl.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, speed);
        }
    }

    /** The nearest water she can reach within 12 blocks. */
    private void planToWater(double speed) {
        BlockPos here = girl.blockPosition();
        if (girl.isInWaterRainOrBubble()) return;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(here.offset(-12, -4, -12), here.offset(12, 3, 12))) {
            if (!girl.level().getFluidState(p).is(FluidTags.WATER)) continue;
            double d = p.distSqr(here);
            if (d < bestDist) {
                bestDist = d;
                best = p.immutable();
            }
        }
        if (best == null) {
            Vec3 random = DefaultRandomPos.getPos(girl, 8, 4);
            if (random != null) girl.getNavigation().moveTo(random.x, random.y, random.z, speed);
            return;
        }
        goal = Vec3.atCenterOf(best);
        girl.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, speed);
    }

    /** A spot near {@code target} where she can stand (scanning a few blocks up and down), or null. */
    @Nullable
    private BlockPos standableNear(Vec3 target) {
        Level level = girl.level();
        BlockPos column = BlockPos.containing(target.x, girl.getY(), target.z);
        if (!level.isLoaded(column)) return null;
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, -5, -6}) {
            BlockPos p = column.above(dy);
            PathType type = WalkNodeEvaluator.getPathTypeStatic(girl, p);
            if (type == PathType.WALKABLE || type == PathType.DOOR_OPEN || type == PathType.DOOR_WOOD_CLOSED) {
                BlockPos below = p.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return p;
            }
        }
        return null;
    }

    /** How many of eight directions she could go on in from there (0..8). */
    private int openness(BlockPos pos) {
        int open = 0;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            if (standableNear(Vec3.atBottomCenterOf(pos).add(Math.cos(a) * 4, 0, Math.sin(a) * 4)) != null) open++;
        }
        return open;
    }

    private static double minDistance(List<Threats.Threat> threats, Vec3 pos) {
        double min = Double.MAX_VALUE;
        for (Threats.Threat t : threats) min = Math.min(min, t.lastSeenAt.distanceTo(pos));
        return min;
    }

    private boolean outOfSight(List<Threats.Threat> threats, Vec3 pos) {
        Vec3 eye = pos.add(0, girl.getEyeHeight(), 0);
        for (Threats.Threat t : threats) {
            Vec3 from = t.lastSeenAt.add(0, t.entity.getEyeHeight(), 0);
            if (from.distanceTo(eye) > 48) continue;
            var hit = girl.level().clip(new ClipContext(from, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, t.entity));
            if (hit.getType() == HitResult.Type.MISS) return false;
        }
        return true;
    }

    static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90;
    }
}
