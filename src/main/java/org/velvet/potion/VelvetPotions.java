package org.velvet.potion;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import org.velvet.Velvet;

public final class VelvetPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Velvet.MODID);

    private static final int BASE = 2400;  // 2 minutes
    private static final int LONG = 9600;  // 8 minutes

    public static final DeferredHolder<Potion, Potion> HORNY_POTION =
            POTIONS.register("horny", () -> new Potion("horny", new MobEffectInstance(Potions.HORNY, 3600)));

    // Growth: a glowstone bumps the tier (×2, ×4, ×8), redstone makes the base tier last longer.
    public static final DeferredHolder<Potion, Potion> GROWTH =
            POTIONS.register("growth", () -> new Potion("growth", new MobEffectInstance(Potions.GROWTH, BASE, 0)));
    public static final DeferredHolder<Potion, Potion> GROWTH_LONG =
            POTIONS.register("long_growth", () -> new Potion("growth", new MobEffectInstance(Potions.GROWTH, LONG, 0)));
    public static final DeferredHolder<Potion, Potion> GROWTH_2 =
            POTIONS.register("growth_2", () -> new Potion("growth", new MobEffectInstance(Potions.GROWTH, BASE, 1)));
    public static final DeferredHolder<Potion, Potion> GROWTH_3 =
            POTIONS.register("growth_3", () -> new Potion("growth", new MobEffectInstance(Potions.GROWTH, BASE, 2)));

    // Shrink: a glowstone shrinks her further (1 block, half a block, ~1.8 px), redstone extends the base.
    public static final DeferredHolder<Potion, Potion> SHRINK =
            POTIONS.register("shrink", () -> new Potion("shrink", new MobEffectInstance(Potions.SHRINK, BASE, 0)));
    public static final DeferredHolder<Potion, Potion> SHRINK_LONG =
            POTIONS.register("long_shrink", () -> new Potion("shrink", new MobEffectInstance(Potions.SHRINK, LONG, 0)));
    public static final DeferredHolder<Potion, Potion> SHRINK_2 =
            POTIONS.register("shrink_2", () -> new Potion("shrink", new MobEffectInstance(Potions.SHRINK, BASE, 1)));
    public static final DeferredHolder<Potion, Potion> SHRINK_3 =
            POTIONS.register("shrink_3", () -> new Potion("shrink", new MobEffectInstance(Potions.SHRINK, BASE, 2)));

    private VelvetPotions() {}

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
    }
}
