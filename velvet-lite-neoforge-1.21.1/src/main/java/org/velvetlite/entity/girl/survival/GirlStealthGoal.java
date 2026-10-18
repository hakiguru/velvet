package org.velvetlite.entity.girl.survival;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlMode;

import java.util.EnumSet;

/**
 * Sneaking away (STEALTH) from a warden that hasn't noticed her yet: slowly, crouched (the brain makes her
 * crouch), to somewhere further from it, and then she keeps still. Without a warden close by this goal does
 * nothing and she just goes about her business crouched.
 */
public class GirlStealthGoal extends Goal {

    /** Closer than this to a warden she creeps away; further, she stays where she is. */
    private static final double KEEP_AWAY = 20;

    private final AbstractGirlEntity girl;
    private int timer;

    public GirlStealthGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (girl.survival().tactic() != SurvivalBrain.Tactic.STEALTH) return false;
        Threats.Threat warden = girl.survival().threats().warden();
        return warden != null && warden.distanceTo(girl.position()) < KEEP_AWAY;
    }

    @Override
    public boolean canContinueToUse() {
        return girl.survival().tactic() == SurvivalBrain.Tactic.STEALTH;
    }

    @Override
    public void start() {
        timer = 0;
        girl.setMode(GirlMode.WALK);
    }

    @Override
    public void stop() {
        girl.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--timer > 0) return;
        timer = 30;
        Threats.Threat warden = girl.survival().threats().warden();
        if (warden == null || warden.distanceTo(girl.position()) >= KEEP_AWAY) {
            girl.getNavigation().stop();
            return;
        }
        if (!girl.getNavigation().isDone()) return;
        Vec3 away = DefaultRandomPos.getPosAway(girl, 12, 4, warden.lastSeenAt);
        if (away != null) girl.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
    }
}
