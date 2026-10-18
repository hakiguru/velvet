package org.velvet.entity.girl.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import org.velvet.entity.girl.AbstractGirlEntity;


public class GirlOpenDoorGoal extends OpenDoorGoal {

    public GirlOpenDoorGoal(AbstractGirlEntity girl, boolean closeDoor) {
        super(girl, closeDoor);
    }

    @Override
    public boolean canUse() {
        return !girlShouldStayInPlace() && super.canUse();
    }

    private boolean girlShouldStayInPlace() {
        Mob mob = (Mob) this.mob;
        return mob instanceof AbstractGirlEntity girl
                && girl.getCurrentAction().isBusy();
    }
}