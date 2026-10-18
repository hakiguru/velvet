package org.velvet.entity.girl;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;
import org.velvet.entity.girl.girls.*;

public final class GirlRegistry {

    private GirlRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Velvet.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ElsieEntity>> ELSIE =
            registerGirl("elsie", ElsieEntity::new);


    // --- helpers ---

    private static <T extends AbstractGirlEntity> DeferredHolder<EntityType<?>, EntityType<T>>
    registerGirl(String name, EntityType.EntityFactory<T> factory) {
        return ENTITIES.register(name, () ->
                EntityType.Builder.of(factory, MobCategory.CREATURE)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(10)
                        .updateInterval(3)
                        .build(name));
    }

    private static <T extends AbstractGirlEntity> DeferredHolder<EntityType<?>, EntityType<T>>
    registerSmall(String name, EntityType.EntityFactory<T> factory, float w, float h) {
        return ENTITIES.register(name, () ->
                EntityType.Builder.of(factory, MobCategory.CREATURE)
                        .sized(w, h)
                        .clientTrackingRange(10)
                        .updateInterval(3)
                        .build(name));
    }

    public static EntityType<? extends AbstractGirlEntity> get(GirlType type) {
        return switch (type) {
            case ELSIE     -> ELSIE.get();
        };
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}