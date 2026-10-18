package org.velvet.entity.girl.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.mind.BlockWork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Walling herself in (SHELTER), the way players do when it gets too much: blocks from her inventory around
 * her feet and head, the side the monsters come from first, then one over her head against arrows. She
 * waits inside until nothing has been after her for a while and she has healed (or her owner comes), then
 * takes the walls down again, blocks back into her inventory.
 */
public class GirlShelterGoal extends Goal {

    private enum Stage { BUILD, INSIDE, OPEN }

    private static final int PLACE_INTERVAL = 3;
    private static final int BUILD_TIMEOUT = 80;
    private static final float OUT_AT_HEALTH = 0.6F;

    private final AbstractGirlEntity girl;
    private final BlockWork work;
    private Stage stage = Stage.BUILD;
    private BlockPos feet = BlockPos.ZERO;
    private final List<BlockPos> todo = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    private int timer;

    public GirlShelterGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.work = new BlockWork(girl);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    /** On the ground with room for her and a roof, where she may place blocks. */
    public static boolean canShelterHere(AbstractGirlEntity girl) {
        if (!girl.onGround() || girl.isInWater() || !BlockWork.mayGrief(girl)) return false;
        Level level = girl.level();
        BlockPos p = girl.blockPosition();
        BlockPos roof = p.above(2);
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && (level.getBlockState(roof).canBeReplaced() || level.getBlockState(roof).isCollisionShapeFullBlock(level, roof));
    }

    @Override
    public boolean canUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.SHELTER;
    }

    @Override
    public boolean canContinueToUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.SHELTER && (stage != Stage.OPEN || !placed.isEmpty());
    }

    @Override
    public void start() {
        stage = Stage.BUILD;
        timer = 0;
        feet = girl.blockPosition();
        girl.getNavigation().stop();
        girl.setPos(feet.getX() + 0.5, girl.getY(), feet.getZ() + 0.5);
        placed.clear();
        todo.clear();
        // the side they come from first
        Threats.Threat worst = girl.survival().threats().nearest();
        Vec3 from = worst != null ? worst.lastSeenAt : girl.position().add(Vec3.directionFromRotation(0, girl.getYRot()));
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Vec3 centre = Vec3.atBottomCenterOf(feet);
        sides.sort(Comparator.comparingDouble(d -> centre.add(d.getStepX(), 0, d.getStepZ()).distanceToSqr(from)));
        for (Direction d : sides) {
            todo.add(feet.relative(d));
            todo.add(feet.relative(d).above());
        }
        todo.add(feet.above(2));
        girl.survival().hidden = true;
    }

    @Override
    public void stop() {
        work.reset();
        if (stage != Stage.INSIDE) girl.survival().hidden = false;
        girl.survival().fleeFailures = 0;
        // anything still up when she's pulled away stays; it's ordinary ground then
        placed.clear();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        timer++;
        girl.getNavigation().stop();
        girl.setDeltaMovement(0, girl.getDeltaMovement().y, 0);
        switch (stage) {
            case BUILD -> build();
            case INSIDE -> {
                if (timer % 20 == 0 && readyToLeave()) {
                    stage = Stage.OPEN;
                    planExit();
                }
            }
            case OPEN -> open();
        }
    }

    private void build() {
        if (timer > BUILD_TIMEOUT) {
            // couldn't close it (monsters in the way, out of blocks): let her think again
            girl.survival().hidden = false;
            girl.survival().fleeFailures = 0;
            stage = Stage.INSIDE;
            return;
        }
        if (timer % PLACE_INTERVAL != 0) return;
        Level level = girl.level();
        while (!todo.isEmpty()) {
            BlockPos p = todo.getFirst();
            if (level.getBlockState(p).isCollisionShapeFullBlock(level, p)) {
                todo.removeFirst(); // already a wall there
                continue;
            }
            Block block = girl.survival().takeBuildingBlock();
            if (block == null) {
                todo.clear();
                break;
            }
            if (work.place(p, block)) {
                placed.add(p);
                todo.removeFirst();
            } else {
                // something stands there: the next spot first, this one again later
                girl.survival().giveBack(block);
                todo.add(todo.removeFirst());
            }
            return;
        }
        stage = Stage.INSIDE;
        timer = 0;
        girl.survival().announceHidden();
    }

    /**
     * What comes down again: everything, roof first, if she can mine it and get it back; without a tool for
     * it (cobblestone by hand takes ages and gives nothing) only a way out, towards her owner.
     */
    private void planExit() {
        boolean canTakeBack = placed.stream().allMatch(p ->
                org.velvet.entity.girl.mind.Gear.mineSpeed(girl.getMainHandItem(), girl.level().getBlockState(p)) > 0
                || org.velvet.entity.girl.mind.Gear.find(girl, st -> org.velvet.entity.girl.mind.Gear.mineSpeed(st, girl.level().getBlockState(p)) > 1) >= 0);
        if (canTakeBack) {
            placed.sort(Comparator.comparingInt(p -> -p.getY()));
            return;
        }
        Player owner = girl.getOwnerUUID() == null ? null : girl.level().getPlayerByUUID(girl.getOwnerUUID());
        Vec3 towards = owner != null ? owner.position() : girl.position().add(Vec3.directionFromRotation(0, girl.getYRot()));
        Direction side = Direction.getNearest(towards.x - girl.getX(), 0, towards.z - girl.getZ());
        if (side.getAxis().isVertical()) side = Direction.NORTH;
        BlockPos door = feet.relative(side);
        placed.removeIf(p -> !p.equals(door) && !p.equals(door.above()));
        placed.sort(Comparator.comparingInt(p -> -p.getY()));
    }

    private boolean readyToLeave() {
        Player owner = girl.getOwnerUUID() == null ? null : girl.level().getPlayerByUUID(girl.getOwnerUUID());
        if (owner != null && owner.distanceTo(girl) < 3) return true;
        return girl.survival().safeLongEnough() && (girl.getHealth() >= girl.getMaxHealth() * OUT_AT_HEALTH
                || girl.level().getGameTime() - girl.survival().lastThreatTime() > SurvivalBrain.SAFE_AFTER * 3);
    }

    private void open() {
        if (placed.isEmpty()) {
            girl.survival().hidden = false;
            return;
        }
        BlockPos p = placed.getFirst();
        if (girl.level().getBlockState(p).isAir()) {
            placed.removeFirst();
            return;
        }
        org.velvet.entity.girl.mind.Gear.holdTool(girl, girl.level().getBlockState(p));
        if (work.breakStep(p)) placed.removeFirst();
    }
}
