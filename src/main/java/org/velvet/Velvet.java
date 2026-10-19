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
        var b = event.getBuilder();
        b.addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD,
                net.minecraft.world.item.Items.WHEAT_SEEDS, org.velvet.potion.VelvetPotions.HORNY_POTION);

        var awkward = net.minecraft.world.item.alchemy.Potions.AWKWARD;
        var glowstone = net.minecraft.world.item.Items.GLOWSTONE_DUST;
        var redstone = net.minecraft.world.item.Items.REDSTONE;

        // growth: a ghast tear starts it, glowstone makes it stronger (×2 → ×4 → ×8), redstone makes it last
        b.addMix(awkward, net.minecraft.world.item.Items.GHAST_TEAR, org.velvet.potion.VelvetPotions.GROWTH);
        b.addMix(org.velvet.potion.VelvetPotions.GROWTH, glowstone, org.velvet.potion.VelvetPotions.GROWTH_2);
        b.addMix(org.velvet.potion.VelvetPotions.GROWTH_2, glowstone, org.velvet.potion.VelvetPotions.GROWTH_3);
        b.addMix(org.velvet.potion.VelvetPotions.GROWTH, redstone, org.velvet.potion.VelvetPotions.GROWTH_LONG);

        // shrink: a phantom membrane starts it, glowstone shrinks further (1 block → half → ~1.8 px)
        b.addMix(awkward, net.minecraft.world.item.Items.PHANTOM_MEMBRANE, org.velvet.potion.VelvetPotions.SHRINK);
        b.addMix(org.velvet.potion.VelvetPotions.SHRINK, glowstone, org.velvet.potion.VelvetPotions.SHRINK_2);
        b.addMix(org.velvet.potion.VelvetPotions.SHRINK_2, glowstone, org.velvet.potion.VelvetPotions.SHRINK_3);
        b.addMix(org.velvet.potion.VelvetPotions.SHRINK, redstone, org.velvet.potion.VelvetPotions.SHRINK_LONG);
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