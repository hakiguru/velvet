package org.velvetlite.entity.girl.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;

/**
 * Where she can dig herself in: standing at {@code top}, the two blocks under her are soft (dirt, sand, gravel,
 * leaves... anything about as quick to break by hand), there's solid ground under those, and no water or lava
 * next to the hole that could pour in.
 */
public final class DigSpot {

    private DigSpot() {}

    /** Softest blocks she digs: dirt is 0.5, gravel 0.6, clay 0.6; stone (1.5) is too hard by hand. */
    public static final float MAX_HARDNESS = 0.8F;
    public static final int DEPTH = 2;

    /** The best spot within {@code radius} (her own first, then the closest), or null. */
    @Nullable
    public static BlockPos find(AbstractGirlEntity girl, int radius) {
        BlockPos here = girl.blockPosition();
        if (isGood(girl, here)) return here;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(here.offset(-radius, -2, -radius), here.offset(radius, 2, radius))) {
            double d = p.distSqr(here);
            if (d < bestDist && isGood(girl, p)) {
                bestDist = d;
                best = p.immutable();
            }
        }
        return best;
    }

    public static boolean isGood(AbstractGirlEntity girl, BlockPos top) {
        Level level = girl.level();
        if (WalkNodeEvaluator.getPathTypeStatic(girl, top) != PathType.WALKABLE) return false;
        // room to stand there and a free block above for the cover to go under her head... and the cover itself
        if (!level.getBlockState(top).getCollisionShape(level, top).isEmpty()) return false;
        for (int d = 1; d <= DEPTH; d++) {
            BlockPos p = top.below(d);
            if (!isSoft(level, p)) return false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!level.getFluidState(p.relative(side)).isEmpty()) return false;
            }
        }
        BlockPos floor = top.below(DEPTH + 1);
        BlockState below = level.getBlockState(floor);
        return below.isFaceSturdy(level, floor, Direction.UP) && level.getFluidState(floor).isEmpty()
                && level.getFluidState(top).isEmpty() && level.getFluidState(top.above()).isEmpty();
    }

    public static boolean isSoft(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (state.is(BlockTags.FEATURES_CANNOT_REPLACE)) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= MAX_HARDNESS && state.isCollisionShapeFullBlock(level, pos);
    }
}
