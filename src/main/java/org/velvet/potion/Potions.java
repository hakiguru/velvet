package org.velvet.potion;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;


public final class Potions {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Velvet.MODID);

    public static final DeferredHolder<MobEffect, HornyEffect> HORNY =
            EFFECTS.register("horny", HornyEffect::new);

    public static final DeferredHolder<MobEffect, SizeEffect> GROWTH =
            EFFECTS.register("growth", SizeEffect::growth);
    public static final DeferredHolder<MobEffect, SizeEffect> SHRINK =
            EFFECTS.register("shrink", SizeEffect::shrink);

    private Potions() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}