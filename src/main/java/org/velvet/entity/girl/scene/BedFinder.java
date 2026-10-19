package org.velvet.entity.girl.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Finds the closest free bed the girl can actually walk to, and the spot next to it she climbs on from.
 * <p>
 * Uses the POI index (every bed head is registered as {@code minecraft:home}), so no block scanning is
 * needed; candidates come closest first and each is checked with a real path, not straight-line distance.
 * Any side of the bed will do: the spot is the closest free floor cell next to either half of it.
 */
public final class BedFinder {

    public static final int SEARCH_RADIUS = 24;
    private static final int MAX_HEIGHT_DIFFERENCE = 4;
    /** Path building is the expensive part, so only this many nearest spots are tried. */
    private static final int MAX_PATH_CHECKS = 8;

    private BedFinder() {}

    /**
     * A bed and where the girl climbs onto it: she stands on {@code spot} facing {@code toBed}, and that's
     * also where the partner has to stand.
     */
    public record Bed(BlockPos head, BlockPos foot, BlockPos spot, Direction toBed) {}

    @Nullable
    public static Bed find(AbstractGirlEntity girl) {
        return find(girl, SceneType.Spot.SIDE);
    }

    /**
     * The closest free bed she can walk to, with a spot of the given kind: SIDE is any free floor cell next
     * to either half, FOOT only the cell beyond the end of its foot, in line with it (for scenes where the
     * partner lies along the bed), LEFT the cell left of the foot half (seen from its foot end).
     */
    @Nullable
    public static Bed find(AbstractGirlEntity girl, SceneType.Spot kind) {
        if (!(girl.level() instanceof ServerLevel level)) return null;
        BlockPos origin = girl.blockPosition();

        Iterator<BlockPos> heads = level.getPoiManager()
                .findAllClosestFirstWithType(type -> type.is(PoiTypes.HOME), pos -> true,
                        origin, SEARCH_RADIUS, PoiManager.Occupancy.ANY)
                .map(pair -> pair.getSecond())
                .filter(pos -> Math.abs(pos.getY() - origin.getY()) <= MAX_HEIGHT_DIFFERENCE)
                .iterator();

        int checked = 0;
        while (heads.hasNext() && checked < MAX_PATH_CHECKS) {
            BlockPos head = heads.next();
            if (bedAt(level, head) == null) continue;
            for (Bed bed : spotsAround(level, head, girl.blockPosition(), kind)) {
                if (checked++ >= MAX_PATH_CHECKS) break;
                Path path = girl.getNavigation().createPath(bed.spot(), 0);
                if (path != null && path.canReach()) return bed;
            }
        }
        return null;
    }

    /** Free spots next to the bed with head {@code head}, closest to {@code from} first. */
    private static List<Bed> spotsAround(ServerLevel level, BlockPos head, BlockPos from, SceneType.Spot kind) {
        Direction facing = level.getBlockState(head).getValue(BedBlock.FACING);
        BlockPos foot = head.relative(facing.getOpposite());
        List<Bed> spots = new ArrayList<>();
        if (kind == SceneType.Spot.FOOT) {
            BlockPos spot = foot.relative(facing.getOpposite());
            if (canStandAt(level, spot)) spots.add(new Bed(head, foot, spot, facing));
            return spots;
        }
        if (kind == SceneType.Spot.LEFT) {
            // left of the foot half, as seen lying in the bed head up
            BlockPos spot = foot.relative(facing.getCounterClockWise());
            if (canStandAt(level, spot)) spots.add(new Bed(head, foot, spot, facing.getClockWise()));
            return spots;
        }
        for (BlockPos part : new BlockPos[]{foot, head}) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos spot = part.relative(side);
                if (spot.equals(foot) || spot.equals(head) || !canStandAt(level, spot)) continue;
                spots.add(new Bed(head, foot, spot, side.getOpposite()));
            }
        }
        spots.sort(Comparator.comparingDouble(bed -> bed.spot().distSqr(from)));
        return spots;
    }

    /** Solid floor below and room for a player (two blocks) above. */
    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    /** Whether the bed is still there and nobody is sleeping in it. */
    public static boolean stillFree(ServerLevel level, Bed bed) {
        return bedAt(level, bed.head()) != null && canStandAt(level, bed.spot());
    }

    /** Null unless {@code headPos} is the head of a bed nobody is sleeping in; returns the head. */
    @Nullable
    private static BlockPos bedAt(ServerLevel level, BlockPos headPos) {
        BlockState state = level.getBlockState(headPos);
        if (!(state.getBlock() instanceof BedBlock)
                || state.getValue(BedBlock.PART) != BedPart.HEAD
                || state.getValue(BedBlock.OCCUPIED)) {
            return null;
        }
        return headPos;
    }
}
