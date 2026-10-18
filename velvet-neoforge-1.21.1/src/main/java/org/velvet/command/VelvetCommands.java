package org.velvet.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class VelvetCommands {
    private VelvetCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("velvet")
                .then(FutaCommand.build())
                .then(HornyCommand.build())
                .then(ReloadCustomModelsCommand.build()));
    }
}