package org.velvet.entity.girl.girls;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlType;

public class ElsieEntity extends AbstractGirlEntity {
    public ElsieEntity(EntityType<? extends ElsieEntity> type, Level level) {
        super(type, level, GirlType.JENNY);
    }
}