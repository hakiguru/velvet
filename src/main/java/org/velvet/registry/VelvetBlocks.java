package org.velvet.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;

public final class VelvetBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Velvet.MODID);

    public static final DeferredBlock<Block> FIRE = BLOCKS.register("fire",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .replaceable()
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .lightLevel(s -> 15)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
