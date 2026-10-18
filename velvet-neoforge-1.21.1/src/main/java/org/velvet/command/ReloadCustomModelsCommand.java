package org.velvet.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.velvet.custommodel.CustomModelManager;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.SetCustomModelPacket;

import java.util.HashMap;

public final class ReloadCustomModelsCommand {
    private ReloadCustomModelsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("reloadcustommodels")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    CustomModelManager.loadAll();
                    var payload = new SetCustomModelPacket(new HashMap<>());
                    for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                        NetworkHandler.sendToPlayer(p, payload);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Custom models reloaded"), true);
                    return 1;
                });
    }
}