package org.velvetlite.potion;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvetlite.VelvetLite;

public final class VelvetPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, VelvetLite.MODID);

    public static final DeferredHolder<Potion, Potion> HORNY_POTION =
            POTIONS.register("horny", () -> new Potion(
                    "horny",
                    new MobEffectInstance(Potions.HORNY, 3600)));

    private VelvetPotions() {}

    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
    }
}