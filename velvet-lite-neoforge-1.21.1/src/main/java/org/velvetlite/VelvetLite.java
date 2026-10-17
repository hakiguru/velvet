package org.velvetlite;

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
import org.velvetlite.command.VelvetCommands;
import org.velvetlite.registry.*;
import org.velvetlite.network.NetworkHandler;

@Mod(VelvetLite.MODID)
public class VelvetLite {

    public static final String MODID = "velvet_lite";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static VelvetLite instance;

    public VelvetLite(IEventBus modBus, ModContainer container) {
        instance = this;

        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, org.velvetlite.client.ClientConfig.SPEC);

        org.velvetlite.entity.girl.GirlRegistry.register(modBus);
        org.velvetlite.potion.VelvetPotions.register(modBus);
        VelvetSounds.register(modBus);
        VelvetBlocks.register(modBus);
        VelvetItems.register(modBus);
        VelvetCreativeTabs.register(modBus);
        org.velvetlite.potion.Potions.register(modBus);
        VelvetMenus.register(modBus);
        NetworkHandler.register(modBus);
        modBus.addListener(this::registerAttributes);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::loadComplete);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::registerBrewing);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("VelvetLite commonSetup begin");

            LOGGER.info("VelvetLite commonSetup done");
        });
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(org.velvetlite.entity.girl.GirlRegistry.ELSIE.get(),    org.velvetlite.entity.girl.AbstractGirlEntity.createAttributes().build());
    }

    // Awkward potion + wheat seeds -> horny potion (the old data/recipe json used a recipe type that doesn't exist)
    private void registerBrewing(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD,
                net.minecraft.world.item.Items.WHEAT_SEEDS, org.velvetlite.potion.VelvetPotions.HORNY_POTION);
    }

    private void loadComplete(FMLLoadCompleteEvent event) {

    }

    // RegisterCommandsEvent also fires on /reload, unlike ServerStartingEvent
    private void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        VelvetCommands.register(event.getDispatcher());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        org.velvetlite.entity.girl.GirlManager.clearAll();
        org.velvetlite.custommodel.CustomModelManager.resetStaticFlags();
    }
}