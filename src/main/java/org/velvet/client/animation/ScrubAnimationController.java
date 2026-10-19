package org.velvet.client.animation;

import org.velvet.entity.girl.AbstractGirlEntity;
import software.bernie.geckolib.animation.AnimationController;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The action controller, which the animation tab can hold at a moment of its choosing (scrubbing through an
 * animation being made): while a time is set for her, the animation shows that moment and stands still.
 */
public class ScrubAnimationController extends AnimationController<AbstractGirlEntity> {

    /** Seconds into the animation to show, per girl (only the editor's stand-ins ever have one). */
    public static final Map<AbstractGirlEntity, Double> SCRUB = new WeakHashMap<>();

    private final AbstractGirlEntity girl;

    public ScrubAnimationController(AbstractGirlEntity girl, String name, int transitionTicks,
                                    AnimationStateHandler<AbstractGirlEntity> handler) {
        super(girl, name, transitionTicks, handler);
        this.girl = girl;
    }

    @Override
    protected double adjustTick(double tick) {
        Double at = SCRUB.get(girl);
        if (at == null) return super.adjustTick(tick);
        // straight through the transition into it, then held at that moment
        return this.animationState == State.TRANSITIONING ? this.transitionLength : at * 20;
    }
}
