package org.velvet.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.velvet.potion.Potions;

public final class HornyCommand {
    private HornyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("horny").executes(ctx -> {
            ServerPlayer sp = ctx.getSource().getPlayer();
            if (sp != null) {
                sp.addEffect(new MobEffectInstance(Potions.HORNY, 3600, 0));
                ctx.getSource().sendSuccess(() -> Component.literal("Feeling horny..."), false);
            }
            return 1;
        });
    }
}