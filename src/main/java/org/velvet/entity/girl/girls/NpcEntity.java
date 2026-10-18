package org.velvet.entity.girl.girls;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlType;

/** An NPC from the spawn egg: starts out blank (a white player) until a preset is picked in the editor. */
public class NpcEntity extends AbstractGirlEntity {
    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level, GirlType.UNKNOWN);
    }
}
