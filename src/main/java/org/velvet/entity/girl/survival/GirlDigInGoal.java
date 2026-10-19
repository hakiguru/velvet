package org.velvet.entity.girl.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlMode;

import java.util.EnumSet;

/**
 * Digging in (DIG_IN): to the nearest soft ground, two blocks down, and the first block she dug goes back over
 * her head. She waits there until nothing has been after her for a while and she's healed up (or her owner
 * comes to get her), then climbs out on the blocks she dug and the ground is as it was. Her name shows
 * through the ground, so her owner can find her.
 */
public class GirlDigInGoal extends Goal {

    private enum Stage { WALK, DIG, COVER, HIDDEN, OPEN, CLIMB }

    private static final float OUT_AT_HEALTH = 0.6F;
    private static final double OWNER_RESCUE = 3;

    private final AbstractGirlEntity girl;
    private final BlockClimber climber;
    private Stage stage = Stage.WALK;
    private BlockPos top = BlockPos.ZERO;
    private int depth;
    private float progress;
    private int timer;
    private int walkTries;

    public GirlDigInGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.climber = new BlockClimber(girl);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    private SurvivalBrain brain() {
        return girl.survival();
    }

    @Override
    public boolean canUse() {
        if (brain().tactic() != SurvivalBrain.Tactic.DIG_IN) return false;
        if (brain().hole != null) return true; // back from a reload while dug in
        BlockPos spot = DigSpot.find(girl, 4);
        if (spot == null) return false;
        top = spot;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return brain().hidden || brain().tactic() == SurvivalBrain.Tactic.DIG_IN;
    }

    @Override
    public void start() {
        if (brain().hole != null) {
            top = brain().hole;
            stage = brain().covered ? Stage.HIDDEN : Stage.DIG;
            depth = Math.clamp(brain().pocket.size(), 0, DigSpot.DEPTH);
        } else {
            stage = Stage.WALK;
            depth = 0;
            walkTries = 0;
            girl.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 1.4);
        }
        progress = 0;
        timer = 0;
    }

    @Override
    public void stop() {
        girl.getNavigation().stop();
        if (brain().hole == null) brain().hidden = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        timer++;
        // something got in with her (a baby zombie fits through a one-block hole): fight it, or get out
        if (stage == Stage.DIG || stage == Stage.COVER || stage == Stage.HIDDEN) {
            net.minecraft.world.entity.Mob intruder = intruder();
            if (intruder != null) {
                if (org.velvet.entity.girl.mind.Gear.isMeleeWeapon(girl.getMainHandItem())) {
                    girl.getLookControl().setLookAt(intruder);
                    if (girl.getAttackAnim(0) == 0 && timer % 12 == 0) girl.strike(intruder, false);
                } else if (stage != Stage.DIG) {
                    stage = Stage.OPEN;
                    progress = 0;
                    brain().digFailed();
                } else {
                    stage = Stage.COVER;
                    timer = 61;
                }
                return;
            }
        }
        // over the hole, she stays right in the middle of it: on its edge she'd stand on the next block
        if (stage != Stage.WALK) {
            girl.setPos(top.getX() + 0.5, girl.getY(), top.getZ() + 0.5);
            girl.setDeltaMovement(0, girl.getDeltaMovement().y, 0);
        }
        switch (stage) {
            case WALK -> {
                girl.setMode(GirlMode.RUN);
                if (girl.blockPosition().equals(top) || girl.position().distanceToSqr(top.getX() + 0.5, top.getY(), top.getZ() + 0.5) < 0.6) {
                    if (!DigSpot.isGood(girl, top)) {
                        // the spot changed (or she isn't standing on it): another one, or run instead
                        BlockPos spot = DigSpot.find(girl, 4);
                        if (spot == null || spot.equals(top)) {
                            brain().digFailed();
                            return;
                        }
                        top = spot;
                        timer = 0;
                        girl.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 1.4);
                        return;
                    }
                    brain().hidden = true;
                    brain().hole = top;
                    stage = Stage.DIG;
                    timer = 0;
                    girl.setMode(GirlMode.WALK);
                    girl.getNavigation().stop();
                } else if (girl.getNavigation().isDone() || timer % 100 == 0) {
                    // can't get there: a few more tries, then she runs instead
                    BlockPos spot = DigSpot.find(girl, 4);
                    if (spot == null || ++walkTries > 3) {
                        brain().digFailed();
                        return;
                    }
                    top = spot;
                    timer = 1;
                    girl.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 1.4);
                }
            }
            case DIG -> dig();
            case COVER -> {
                // once she's down there, the first block dug goes over her head
                if (girl.onGround() && girl.getY() < top.getY() - DigSpot.DEPTH + 0.5) {
                    if (girl.level().getBlockState(top).canBeReplaced() && !brain().pocket.isEmpty()) {
                        BlockState cover = brain().pocket.removeFirst();
                        girl.level().setBlock(top, cover, 3);
                        girl.level().gameEvent(girl, GameEvent.BLOCK_PLACE, top);
                        girl.swing(InteractionHand.MAIN_HAND);
                    }
                    brain().covered = true;
                    brain().announceHidden();
                    stage = Stage.HIDDEN;
                } else if (timer > 60) {
                    stage = Stage.HIDDEN; // stuck on something: stay in the hole uncovered
                    brain().covered = true;
                }
            }
            case HIDDEN -> {
                girl.getNavigation().stop();
                girl.setDeltaMovement(0, girl.getDeltaMovement().y, 0);
                if (timer % 20 == 0 && readyToLeave()) {
                    stage = Stage.OPEN;
                    progress = 0;
                }
            }
            case OPEN -> {
                // the cover comes off the same way it went on
                BlockState state = girl.level().getBlockState(top);
                if (!state.isAir() && DigSpot.isSoft(girl.level(), top)) {
                    if (breakStep(top)) {
                        brain().pocket.addFirst(state);
                        girl.level().setBlock(top, Blocks.AIR.defaultBlockState(), 3);
                        startClimb();
                    }
                } else {
                    startClimb();
                }
            }
            case CLIMB -> {
                if (climber.tick()) {
                    if (climber.timedOut()) brain().pocket.addFirst(climber.block());
                    if (brain().pocket.isEmpty() || girl.getY() >= top.getY() - 0.1) finish();
                    else climber.start(brain().pocket.removeLast());
                }
            }
        }
    }

    private void dig() {
        if (depth >= DigSpot.DEPTH) {
            stage = Stage.COVER;
            timer = 0;
            return;
        }
        BlockPos pos = top.below(depth + 1);
        girl.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockState state = girl.level().getBlockState(pos);
        if (state.isAir()) {
            depth++;
            return;
        }
        if (!DigSpot.isSoft(girl.level(), pos)) {
            // something hard turned up (a block was placed): stay in what there is
            stage = Stage.COVER;
            timer = 0;
            return;
        }
        if (breakStep(pos)) {
            brain().pocket.addLast(state);
            girl.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            depth++;
        }
    }

    /** One tick of breaking the block; true when it's broken. Takes about as long as for a player by hand. */
    private boolean breakStep(BlockPos pos) {
        BlockState state = girl.level().getBlockState(pos);
        float hardness = state.getDestroySpeed(girl.level(), pos);
        float toolSpeed = Math.max(1, girl.getMainHandItem().getDestroySpeed(state));
        progress += toolSpeed / Math.max(0.05F, hardness) / 30;
        if (timer % 4 == 0) girl.swing(InteractionHand.MAIN_HAND);
        girl.level().destroyBlockProgress(girl.getId(), pos, (int) (progress * 10) - 1);
        if (progress < 1) return false;
        progress = 0;
        girl.level().destroyBlockProgress(girl.getId(), pos, -1);
        girl.level().levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state)); // break particles and sound
        girl.level().gameEvent(girl, GameEvent.BLOCK_DESTROY, pos);
        return true;
    }

    /** A monster in the hole with her (or right at its top). */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.entity.Mob intruder() {
        var box = new net.minecraft.world.phys.AABB(top).expandTowards(0, -DigSpot.DEPTH, 0).inflate(0.6, 0.5, 0.6);
        for (var m : girl.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                m -> m != girl && m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy)) return m;
        return null;
    }

    private boolean readyToLeave() {
        Player owner = girl.getOwnerUUID() == null ? null : girl.level().getPlayerByUUID(girl.getOwnerUUID());
        if (owner != null && owner.distanceTo(girl) < OWNER_RESCUE) return true;
        return brain().safeLongEnough() && (girl.getHealth() >= girl.getMaxHealth() * OUT_AT_HEALTH
                || girl.level().getGameTime() - brain().lastThreatTime() > SurvivalBrain.SAFE_AFTER * 3);
    }

    private void startClimb() {
        if (brain().pocket.isEmpty()) {
            finish();
            return;
        }
        stage = Stage.CLIMB;
        climber.start(brain().pocket.removeLast());
    }

    private void finish() {
        brain().hidden = false;
        brain().covered = false;
        brain().hole = null;
        brain().pocket.clear();
        brain().fleeFailures = 0;
    }
}
