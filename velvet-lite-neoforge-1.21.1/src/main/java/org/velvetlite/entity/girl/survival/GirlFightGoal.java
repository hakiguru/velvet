package org.velvetlite.entity.girl.survival;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlMode;
import org.velvetlite.entity.girl.GirlSkill;

import java.util.EnumSet;

/**
 * Fighting back (FIGHT): goes for the most dangerous monster and hits it as often as her weapon allows. Against
 * archers she walks up behind her raised shield and lowers it to strike.
 */
public class GirlFightGoal extends Goal {

    private final AbstractGirlEntity girl;
    @Nullable private LivingEntity target;
    private int attackCooldown;
    private int pathTimer;

    public GirlFightGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (girl.survival().tactic() != SurvivalBrain.Tactic.FIGHT) return false;
        Threats.Threat worst = girl.survival().threats().worst();
        target = worst == null ? null : worst.entity;
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.FIGHT && target != null && target.isAlive()
                && girl.distanceToSqr(target) < 24 * 24;
    }

    @Override
    public void start() {
        pathTimer = 0;
        girl.setAggressive(true);
    }

    @Override
    public void stop() {
        girl.setAggressive(false);
        girl.stopUsingItem();
        girl.getNavigation().stop();
        girl.setMode(GirlMode.WALK);
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) return;
        // the most dangerous one may have changed
        Threats.Threat worst = girl.survival().threats().worst();
        if (worst != null && worst.entity != target && worst.entity.isAlive()) target = worst.entity;

        girl.getLookControl().setLookAt(target, 30, 30);
        double distSqr = girl.distanceToSqr(target);
        if (--pathTimer <= 0) {
            pathTimer = 10;
            girl.getNavigation().moveTo(target, 1.2);
        }
        if (attackCooldown > 0) attackCooldown--;

        boolean ranged = Threats.kindOf(target) == Threats.Kind.RANGED;
        boolean shield = girl.getOffhandItem().getItem() instanceof ShieldItem;
        boolean inReach = girl.isWithinMeleeAttackRange(target);
        if (shield && ranged && !inReach && distSqr < 20 * 20 && girl.hasLineOfSight(target)) {
            if (!girl.isUsingItem()) girl.startUsingItem(InteractionHand.OFF_HAND);
        } else if (girl.isUsingItem() && girl.getUseItem().getItem() instanceof ShieldItem) {
            girl.stopUsingItem();
        }

        if (inReach && attackCooldown <= 0 && girl.hasLineOfSight(target)) {
            girl.swing(InteractionHand.MAIN_HAND);
            if (girl.doHurtTarget(target)) girl.addSkillXp(GirlSkill.WEAPONS, 1);
            double speed = girl.getAttributeValue(Attributes.ATTACK_SPEED);
            attackCooldown = Math.max(8, (int) Math.ceil(20 / Math.max(0.5, speed)));
        }
    }
}
