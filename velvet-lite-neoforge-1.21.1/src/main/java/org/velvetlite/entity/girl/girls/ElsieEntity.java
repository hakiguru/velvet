package org.velvetlite.entity.girl.girls;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlType;

public class ElsieEntity extends AbstractGirlEntity {
    public ElsieEntity(EntityType<? extends ElsieEntity> type, Level level) {
        super(type, level, GirlType.ELSIE);
    }
}