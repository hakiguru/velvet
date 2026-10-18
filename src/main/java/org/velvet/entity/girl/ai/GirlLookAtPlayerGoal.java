package org.velvet.entity.girl.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import org.velvet.entity.girl.AbstractGirlEntity;

public class GirlLookAtPlayerGoal extends LookAtPlayerGoal {

    public boolean enabled = true;

    public GirlLookAtPlayerGoal(AbstractGirlEntity mob,
                                Class<? extends LivingEntity> targetType,
                                float lookDistance,
                                float probability) {
        super(mob, targetType, lookDistance, probability);
    }

    @Override
    public void tick() {
        if (this.enabled) super.tick();
    }
}