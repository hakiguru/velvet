package org.velvet;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.velvet.command.VelvetCommands;
import org.velvet.registry.*;
import org.velvet.network.NetworkHandler;

@Mod(Velvet.MODID)
public class Velvet {

    public static final String MODID = "velvet";
    /** The mod's id up to 1.3: worlds from back then still find their NPCs, items and blocks under it. */
    private static final String LEGACY_MODID = "velvet_lite";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static Velvet instance;

    public Velvet(IEventBus modBus, ModContainer container) {
        instance = this;

        // the mod's files have a folder of their own, see VelvetPaths
        VelvetPaths.moveOldConfigs();
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC, MODID + "/common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, org.velvet.client.ClientConfig.SPEC, MODID + "/client.toml");

        org.velvet.entity.girl.GirlRegistry.register(modBus);
        org.velvet.potion.VelvetPotions.register(modBus);
        VelvetSounds.register(modBus);
        VelvetVoices.init();
        VelvetBlocks.register(modBus);
        VelvetItems.register(modBus);
        VelvetCreativeTabs.register(modBus);
        org.velvet.potion.Potions.register(modBus);
        VelvetMenus.register(modBus);
        NetworkHandler.register(modBus);
        aliasLegacyIds();
        modBus.addListener(this::registerAttributes);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::registerBrewing);
    }

    private static void aliasLegacyIds() {
        for (net.neoforged.neoforge.registries.DeferredRegister<?> registry : java.util.List.of(
                org.velvet.entity.girl.GirlRegistry.ENTITIES, VelvetItems.ITEMS, VelvetBlocks.BLOCKS, VelvetSounds.SOUNDS,
                VelvetMenus.MENUS, VelvetCreativeTabs.TABS, org.velvet.potion.VelvetPotions.POTIONS, org.velvet.potion.Potions.EFFECTS)) {
            for (var entry : registry.getEntries()) {
                registry.addAlias(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LEGACY_MODID, entry.getId().getPath()), entry.getId());
            }
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("Velvet commonSetup begin");

            LOGGER.info("Velvet commonSetup done");
        });
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        for (var type : org.velvet.entity.girl.GirlRegistry.all()) {
            event.put(type, org.velvet.entity.girl.AbstractGirlEntity.createAttributes().build());
        }
    }

    // Awkward potion + wheat seeds -> horny potion (the old data/recipe json used a recipe type that doesn't exist)
    private void registerBrewing(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD,
                net.minecraft.world.item.Items.WHEAT_SEEDS, org.velvet.potion.VelvetPotions.HORNY_POTION);
    }

    private void loadComplete(FMLLoadCompleteEvent event) {

    }

    // RegisterCommandsEvent also fires on /reload, unlike ServerStartingEvent
    private void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        VelvetCommands.register(event.getDispatcher());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        org.velvet.entity.girl.GirlManager.clearAll();
        org.velvet.custommodel.CustomModelManager.resetStaticFlags();
    }
}