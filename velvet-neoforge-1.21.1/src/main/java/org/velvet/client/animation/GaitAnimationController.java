package org.velvet.client.animation;

import org.velvet.entity.girl.AbstractGirlEntity;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Movement controller whose speed may change every frame. GeckoLib multiplies the whole time since the
 * animation started by the speed, so changing it mid-loop jumps the walk cycle to another phase; here the
 * time is accumulated at whatever speed applies right now, so the legs only speed up or slow down.
 */
public class GaitAnimationController extends AnimationController<AbstractGirlEntity> {

    private double elapsed;
    private double played;

    public GaitAnimationController(AbstractGirlEntity girl, String name, int transitionTicks,
                                   AnimationStateHandler<AbstractGirlEntity> handler) {
        super(girl, name, transitionTicks, handler);
        setAnimationSpeedHandler(GirlAnimationController::gaitSpeed);
    }

    @Override
    protected double adjustTick(double tick) {
        if (this.shouldResetTick) {
            this.elapsed = 0;
            this.played = 0;
            return super.adjustTick(tick);
        }
        double now = Math.max(tick - this.tickOffset, 0);
        // a new animation or a restarted clock
        if (now < this.elapsed) {
            this.elapsed = 0;
            this.played = 0;
        }
        this.played += getAnimationSpeed() * (now - this.elapsed);
        this.elapsed = now;
        return this.played;
    }
}
