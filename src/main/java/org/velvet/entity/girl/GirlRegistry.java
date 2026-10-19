package org.velvet.entity.girl;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;
import org.velvet.entity.girl.girls.*;

import java.util.List;

public final class GirlRegistry {

    private GirlRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Velvet.MODID);

    /** NPCs from the spawn egg; what they look like is their preset. */
    public static final DeferredHolder<EntityType<?>, EntityType<NpcEntity>> NPC =
            registerGirl("npc", NpcEntity::new);
    /** Up to 1.4 every NPC was this one: kept so older worlds still have her (as the Jenny preset). */
    public static final DeferredHolder<EntityType<?>, EntityType<ElsieEntity>> ELSIE =
            registerGirl("elsie", ElsieEntity::new);

    public static List<EntityType<? extends AbstractGirlEntity>> all() {
        return List.of(NPC.get(), ELSIE.get());
    }

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

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
