package org.velvet.entity.girl.survival;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlMode;
import org.velvet.entity.girl.GirlSkill;
import org.velvet.entity.girl.mind.Gear;

import java.util.EnumSet;

/**
 * Fighting back (FIGHT), the way a player fights:
 * <ul>
 *   <li>Her best weapon in hand, armor on, shield in the other hand.</li>
 *   <li>She only strikes with her weapon fully charged (a sword once every 0.6 seconds), and jumps for a
 *       critical hit when there's room above her.</li>
 *   <li>Between her hits she keeps just out of reach and circles; when a monster is about to strike, or an
 *       archer draws at her, or an arrow comes, she raises her shield (or steps back without one), and goes
 *       in right after it has struck or shot, or when it's busy with someone else.</li>
 *   <li>Far, flying or unreachable ones she shoots with a bow, if she has one and arrows.</li>
 *   <li>Creepers: hit and step back, and away when one starts to hiss.</li>
 * </ul>
 */
public class GirlFightGoal extends Goal {

    /** A player's reach for hitting. */
    private static final double REACH = 3.0;
    /** Waiting between hits, this far from the target: out of a zombie's reach, a step from her own. */
    private static final double HOLD_DISTANCE = 3.6;
    /** A melee monster that struck this long ago may strike again any moment. */
    private static final int ENEMY_READY = 14;
    private static final int BOW_CHARGE = 20;
    private static final double BOW_FROM = 8;

    private final AbstractGirlEntity girl;
    @Nullable private LivingEntity target;
    private int sinceAttack = 100;
    private int pathTimer;
    private int strafeTimer;
    private boolean clockwise;
    private boolean blocking;
    private int jumpTicks = -1;
    private int bowTicks = -1;
    private int unreachable;

    public GirlFightGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (girl.survival().tactic() != SurvivalBrain.Tactic.FIGHT) return false;
        target = pickTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.FIGHT && target != null && target.isAlive()
                && girl.distanceToSqr(target) < 32 * 32;
    }

    @Override
    public void start() {
        pathTimer = 0;
        unreachable = 0;
        girl.setAggressive(true);
        Gear.prepareForFight(girl);
    }

    @Override
    public void stop() {
        girl.setAggressive(false);
        if (girl.isUsingItem() && !girl.needs().isEating()) girl.stopUsingItem();
        blocking = false;
        bowTicks = -1;
        jumpTicks = -1;
        girl.getNavigation().stop();
        girl.setMode(GirlMode.WALK);
        if (Gear.isBow(girl.getMainHandItem())) Gear.holdWeapon(girl);
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** The most dangerous one that's after her; a creeper about to blow first of all. */
    @Nullable
    private LivingEntity pickTarget() {
        Threats.Threat best = null;
        double bestScore = -1;
        for (Threats.Threat t : girl.survival().threats().within(20)) {
            if (!t.entity.isAlive() || t.kind == Threats.Kind.APEX) continue;
            double score = t.danger * (t.hunting ? 2 : 1);
            if (t.entity instanceof Creeper c && c.getSwellDir() > 0) score *= 3;
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best == null ? null : best.entity;
    }

    @Override
    public void tick() {
        if (target == null) return;
        if (girl.tickCount % 10 == 0) {
            LivingEntity better = pickTarget();
            if (better != null) target = better;
        }
        sinceAttack++;
        if (jumpTicks > 12 && girl.onGround()) jumpTicks = -1;
        girl.getLookControl().setLookAt(target, 30, 30);
        if (girl.needs().isEating()) {
            backOff(1.0); // finishing that bite first
            return;
        }

        Threats.Threat info = threatOf(target);
        double dist = girl.distanceTo(target);
        if (useBow(dist)) {
            tickBow(dist);
            return;
        }
        if (Gear.isBow(girl.getMainHandItem())) Gear.holdWeapon(girl);
        bowTicks = -1;

        double cooldown = 20 / Gear.attackSpeed(girl.getMainHandItem());
        boolean ready = sinceAttack >= cooldown;
        long now = girl.level().getGameTime();
        // its own attack: just struck or shot, busy with someone else, or ready to hit her
        boolean distracted = target instanceof Mob m && m.getTarget() != girl;
        boolean opening = distracted || info != null && Threats.sinceAttack(info, now) < ENEMY_READY;
        boolean aboutToStrike = !distracted && info != null && info.kind == Threats.Kind.MELEE && dist < HOLD_DISTANCE
                && Threats.sinceAttack(info, now) >= ENEMY_READY;

        if (target instanceof Creeper creeper) {
            tickCreeper(creeper, dist, ready);
            return;
        }
        guard(ready && (opening || !aboutToStrike) && dist <= REACH + 0.5, aboutToStrike);

        if (ready && (opening || !aboutToStrike || !hasShield())) {
            // in for the hit
            approach(dist);
            if (dist <= REACH && girl.hasLineOfSight(target)) tryStrike();
        } else if (aboutToStrike && !blocking) {
            backOff(1.2);
        } else {
            hold(dist);
        }
    }

    // ---- Melee ----

    private void approach(double dist) {
        if (dist > REACH - 0.6) {
            if (--pathTimer <= 0) {
                pathTimer = 8;
                boolean found = girl.getNavigation().moveTo(target, dist > 6 ? 1.35 : 1.2);
                unreachable = found ? 0 : unreachable + 1;
            }
            girl.setMode(dist > 6 && girl.needs().canRun() ? GirlMode.RUN : GirlMode.WALK);
        } else {
            girl.getNavigation().stop();
        }
    }

    /** Hits now, or jumps first for a critical hit and hits on the way down. */
    private void tryStrike() {
        boolean roomAbove = girl.level().noCollision(girl, girl.getBoundingBox().move(0, 1.2, 0));
        if (jumpTicks < 0 && girl.onGround() && roomAbove && !girl.isInWater() && girl.getRandom().nextFloat() < 0.7F) {
            girl.getJumpControl().jump();
            jumpTicks = 0;
            return;
        }
        if (jumpTicks >= 0) {
            jumpTicks++;
            boolean falling = !girl.onGround() && girl.getDeltaMovement().y < 0;
            if (!falling && jumpTicks < 10) return;
        }
        boolean critical = jumpTicks >= 0 && !girl.onGround() && girl.getDeltaMovement().y < 0;
        jumpTicks = -1;
        if (blocking) {
            // the shield goes down for the hit; players can't strike from behind it either
            girl.stopUsingItem();
            blocking = false;
        }
        if (girl.strike(target, critical)) girl.addSkillXp(GirlSkill.WEAPONS, critical ? 2 : 1);
        sinceAttack = 0;
    }

    /** Between hits: just out of its reach, circling it so others' arrows miss. */
    private void hold(double dist) {
        girl.getNavigation().stop();
        if (--strafeTimer <= 0) {
            strafeTimer = 20 + girl.getRandom().nextInt(30);
            clockwise = !clockwise;
        }
        float forward = dist > HOLD_DISTANCE + 1.5 ? 0.6F : dist < HOLD_DISTANCE - 0.4 ? -0.5F : 0;
        girl.getMoveControl().strafe(forward, clockwise ? 0.4F : -0.4F);
        girl.lookAt(target, 30, 30);
    }

    private void backOff(double speed) {
        girl.getNavigation().stop();
        girl.getMoveControl().strafe((float) -speed * 0.6F, clockwise ? 0.25F : -0.25F);
        if (target != null) girl.lookAt(target, 30, 30);
    }

    /** Shield up against what's coming, down when she's about to hit. */
    private void guard(boolean striking, boolean aboutToStrike) {
        boolean shots = girl.survival().senses().incoming() != null;
        boolean aimedAt = false;
        for (Threats.Threat t : girl.survival().threats().within(24)) aimedAt |= t.aiming && t.visible;
        boolean want = hasShield() && girl.settings().useShield && !striking && (shots || aimedAt || aboutToStrike);
        if (want && !blocking) {
            if (!Gear.isShield(girl.getOffhandItem())) Gear.holdShield(girl);
            if (Gear.isShield(girl.getOffhandItem())) {
                girl.startUsingItem(InteractionHand.OFF_HAND);
                blocking = true;
            }
        } else if (!want && blocking) {
            girl.stopUsingItem();
            blocking = false;
        }
        // a shield only covers the side she faces
        if (blocking) {
            var shot = girl.survival().senses().incoming();
            if (shot != null) girl.getLookControl().setLookAt(shot, 60, 60);
        }
    }

    private boolean hasShield() {
        return Gear.isShield(girl.getOffhandItem()) || Gear.find(girl, Gear::isShield) >= 0;
    }

    /** After a hit on a creeper: backing away for this many ticks before going in again. */
    private int creeperRetreat;

    /**
     * Creepers the way players fight them: run in, a sprinting hit (it knocks it back), straight back out
     * before it can blow; when it hisses, away out of its range, or, if it's too close for that and she has a
     * shield, shield up towards it (a raised shield takes the blast).
     */
    private void tickCreeper(Creeper creeper, double dist, boolean ready) {
        boolean hissing = creeper.getSwellDir() > 0;
        if (hissing) {
            creeperRetreat = 0;
            girl.lookAt(creeper, 60, 60);
            if (dist < 3.2 && hasShield() && girl.settings().useShield) {
                // can't get away in time: take it on the shield, facing it
                girl.getNavigation().stop();
                if (!Gear.isShield(girl.getOffhandItem())) Gear.holdShield(girl);
                if (!blocking && Gear.isShield(girl.getOffhandItem())) {
                    girl.startUsingItem(InteractionHand.OFF_HAND);
                    blocking = true;
                }
                girl.getMoveControl().strafe(-0.4F, 0);
            } else {
                if (blocking) {
                    girl.stopUsingItem();
                    blocking = false;
                }
                // out of its blast (it gives up past seven blocks)
                girl.setSprinting(true);
                girl.setMode(GirlMode.RUN);
                backOff(1.8);
            }
            return;
        }
        if (blocking) {
            girl.stopUsingItem();
            blocking = false;
        }
        if (creeperRetreat > 0) {
            creeperRetreat--;
            girl.setMode(GirlMode.RUN);
            backOff(1.6);
            return;
        }
        if (ready) {
            approach(dist);
            girl.setSprinting(dist > 1.5);
            if (dist <= REACH && girl.hasLineOfSight(creeper)) {
                jumpTicks = -1;
                if (girl.strike(creeper, false)) girl.addSkillXp(GirlSkill.WEAPONS, 1);
                sinceAttack = 0;
                girl.setSprinting(false);
                creeperRetreat = 14;
            }
        } else {
            hold(Math.max(dist, 4.5));
        }
    }

    // ---- Bow ----

    /** A bow for ones far away, in the air or that she can't get to, if she has a bow and arrows. */
    private boolean useBow(double dist) {
        if (!Gear.hasBow(girl) || !Gear.hasArrows(girl)) return false;
        boolean flying = target instanceof Phantom || target instanceof Ghast || target instanceof Blaze;
        boolean creeperClose = target instanceof Creeper && dist < 3;
        if (creeperClose) return false;
        return flying || unreachable >= 3 || dist > BOW_FROM || target instanceof Creeper && dist > 4;
    }

    private void tickBow(double dist) {
        if (blocking) {
            girl.stopUsingItem();
            blocking = false;
        }
        if (!Gear.holdBow(girl)) return;
        // keeps her distance and circles while drawing, like a skeleton
        if (dist < 6) backOff(0.8);
        else hold(dist);
        if (!girl.hasLineOfSight(target)) {
            if (girl.isUsingItem()) girl.stopUsingItem();
            bowTicks = -1;
            if (--pathTimer <= 0) {
                pathTimer = 10;
                girl.getNavigation().moveTo(target, 1.1);
            }
            return;
        }
        if (bowTicks < 0) {
            girl.startUsingItem(InteractionHand.MAIN_HAND);
            bowTicks = 0;
        }
        if (++bowTicks >= BOW_CHARGE) {
            girl.stopUsingItem();
            shoot();
            bowTicks = -1;
        }
    }

    private void shoot() {
        ItemStack bow = girl.getMainHandItem();
        ItemStack ammo = Gear.takeArrow(girl);
        if (ammo.isEmpty()) return;
        AbstractArrow arrow = ProjectileUtil.getMobArrow(girl, ammo, 1.0F, bow);
        Vec3 to = target.position().add(0, target.getBbHeight() / 3, 0).subtract(arrow.position());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        arrow.shoot(to.x, to.y + horizontal * 0.2, to.z, 1.6F, 3.0F - Mth.clamp(girl.getSkillLevel(GirlSkill.WEAPONS) * 0.25F, 0, 2));
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        girl.level().addFreshEntity(arrow);
        girl.level().playSound(null, girl.getX(), girl.getY(), girl.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL,
                1.0F, 1.0F / (girl.getRandom().nextFloat() * 0.4F + 0.8F));
        bow.hurtAndBreak(1, girl, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        sinceAttack = 0;
    }

    @Nullable
    private Threats.Threat threatOf(LivingEntity e) {
        for (Threats.Threat t : girl.survival().threats().all()) if (t.entity == e) return t;
        return null;
    }
}
