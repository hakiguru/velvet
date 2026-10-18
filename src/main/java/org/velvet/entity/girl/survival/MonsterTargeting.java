package org.velvet.entity.girl.survival;

import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;

/**
 * Monsters hunt NPCs the way they hunt players: vanilla ones only go for players and villagers, so without
 * this a zombie walks past her and her instinct to survive has nothing to do. Only monsters with ordinary
 * goals and a grudge against everyone; neutral ones (endermen, piglins) and the warden keep their own ways.
 */
@EventBusSubscriber(modid = Velvet.MODID)
public final class MonsterTargeting {

    private MonsterTargeting() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster)) return;
        if (!(monster instanceof Enemy) || monster instanceof NeutralMob || monster instanceof Warden) return;
        // brain-driven monsters (piglins, hoglins...) have no goal selectors to speak of
        if (monster.targetSelector.getAvailableGoals().isEmpty()) return;
        monster.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(monster, AbstractGirlEntity.class, true,
                girl -> girl.isAlive() && !((AbstractGirlEntity) girl).getCurrentAction().isBusy()));
    }
}
