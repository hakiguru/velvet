package org.velvetlite.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.SetFutaStatePacket;

public final class FutaCommand {
    private FutaCommand() {}

    public static boolean futaEnabled = false;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("futa")
                .then(Commands.literal("true").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("false").executes(ctx -> set(ctx, false)))
                .executes(FutaCommand::usage);
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean value) {
        futaEnabled = value;
        NetworkHandler.sendToPlayer((ServerPlayer) ctx.getSource().getPlayer(),
                new SetFutaStatePacket(value));
        ctx.getSource().sendSuccess(() -> Component.literal("Futa mode: " + value)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("/velvet futa <true|false>")
                .withStyle(ChatFormatting.YELLOW), false);
        return 0;
    }
}