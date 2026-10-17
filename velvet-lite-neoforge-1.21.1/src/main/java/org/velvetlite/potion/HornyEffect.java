package org.velvetlite.potion;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.HornyMeterPacket;

public class HornyEffect extends MobEffect {

    public HornyEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFF6458);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer sp) {
            float value = 0.5F + amplifier * 0.1F;
            NetworkHandler.sendToPlayer(sp, new HornyMeterPacket(value, 1.0F, 0));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}