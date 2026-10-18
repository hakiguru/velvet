package org.velvet.entity.girl.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlMode;
import org.velvet.entity.girl.GirlSkill;
import org.velvet.entity.girl.mind.BlockWork;
import org.velvet.entity.girl.mind.Gear;
import org.velvet.entity.girl.survival.SurvivalBrain;

import java.util.EnumSet;

/**
 * Mining while it's quiet, like a player out with a pickaxe: ores she sees (when she has a tool that gets
 * something out of them), and natural ground to build with when she has fewer than {@link #ENOUGH_BLOCKS}
 * blocks (for pillars and shelters). Only close to her owner when she follows him, never with monsters
 * around, and only where the mobGriefing rule allows.
 */
public class GirlMineGoal extends Goal {

    static final int ENOUGH_BLOCKS = 16;
    /** Gathering stops at this many. */
    private static final int GATHER_UP_TO = 32;
    private static final double OWNER_LEASH = 10;
    private static final int GIVE_UP_AFTER = 200;
    private static final int RETRY_DELAY = 60;

    private final AbstractGirlEntity girl;
    private final BlockWork work;
    @Nullable private BlockPos target;
    private boolean ore;
    private int timer;
    private int cooldown;

    public GirlMineGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.work = new BlockWork(girl);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!quiet()) return false;
        target = pick();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && timer < GIVE_UP_AFTER && quiet() && !girl.level().getBlockState(target).isAir();
    }

    /** Nothing after her, nothing she's doing, and her owner (if she follows him) close by. */
    private boolean quiet() {
        if (girl.getCurrentAction().isBusy() || girl.survival().tactic() != SurvivalBrain.Tactic.NONE || girl.isInDanger()
                || girl.isUsingItem() || girl.shouldBeAtTarget() || !BlockWork.mayGrief(girl) || !girl.settings().ai) return false;
        if (!girl.survival().threats().isEmpty()) return false;
        if (girl.isFollowing() && girl.getOwnerUUID() != null) {
            Player owner = girl.level().getPlayerByUUID(girl.getOwnerUUID());
            return owner != null && owner.distanceTo(girl) < OWNER_LEASH * (girl.miningOrder() > 0 ? 2 : 1);
        }
        return true;
    }

    @Nullable
    private BlockPos pick() {
        var settings = girl.settings();
        int order = girl.miningOrder();
        if (settings.mineOres || order == 1) {
            for (BlockPos p : girl.survival().senses().ores()) {
                if (worthIt(p)) {
                    ore = true;
                    return p;
                }
            }
        }
        int enough = order == 2 ? GATHER_UP_TO : ENOUGH_BLOCKS;
        if ((settings.gatherBlocks || order == 2) && girl.survival().countBuildingBlocks() < enough) {
            for (BlockPos p : girl.survival().senses().material()) {
                if (Vec3.atCenterOf(p).distanceTo(girl.getEyePosition()) > BlockWork.REACH + 2) continue;
                BlockState state = girl.level().getBlockState(p);
                if (!BlockWork.isBuildingMaterial(girl.level(), p, state)) continue;
                // by hand only what breaks quickly; stone needs a pickaxe
                if (!hasToolFor(state) && state.getDestroySpeed(girl.level(), p) > 1) continue;
                ore = false;
                return p;
            }
        }
        return null;
    }

    /** She has something that gets the ore out, and it isn't further than her owner lets her go. */
    private boolean worthIt(BlockPos p) {
        BlockState state = girl.level().getBlockState(p);
        if (!hasToolFor(state)) return false;
        if (girl.isFollowing() && girl.getOwnerUUID() != null) {
            Player owner = girl.level().getPlayerByUUID(girl.getOwnerUUID());
            double leash = OWNER_LEASH * (girl.miningOrder() > 0 ? 2 : 1) + 2;
            if (owner == null || owner.position().distanceTo(Vec3.atCenterOf(p)) > leash) return false;
        }
        return BlockWork.safeToRemove(girl.level(), p);
    }

    private boolean hasToolFor(BlockState state) {
        if (Gear.mineSpeed(girl.getMainHandItem(), state) > 1) return true;
        return Gear.find(girl, s -> Gear.mineSpeed(s, state) > 1) >= 0;
    }

    @Override
    public void start() {
        timer = 0;
        girl.setMode(GirlMode.WALK);
        walk();
    }

    @Override
    public void stop() {
        work.reset();
        if (target != null && !girl.level().getBlockState(target).isAir() && timer >= GIVE_UP_AFTER) cooldown = RETRY_DELAY;
        target = null;
        girl.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        timer++;
        if (target == null) return;
        if (!BlockWork.inReach(girl, target)) {
            if (timer % 20 == 0 || girl.getNavigation().isDone()) walk();
            return;
        }
        girl.getNavigation().stop();
        BlockState state = girl.level().getBlockState(target);
        if (!Gear.holdTool(girl, state) && state.requiresCorrectToolForDrops()) {
            target = null;
            return;
        }
        if (work.breakStep(target)) {
            if (ore) girl.addSkillXp(GirlSkill.TOOLS, 2);
            // the next one right away, while she's at it
            target = null;
            if (quiet()) target = pick();
            timer = 0;
            if (target == null) cooldown = 10;
            if (!ore && girl.survival().countBuildingBlocks() >= GATHER_UP_TO) target = null;
        }
    }

    private void walk() {
        if (target == null) return;
        girl.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }
}
