package org.velvet.entity.girl.brain;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;

import java.util.UUID;

/**
 * What a girl says in chat: either whispered to her owner only, or said to the whole server, as set in her
 * behavior ("chat" → "visibility"). Uses the vanilla chat formats, so it reads like a player talking.
 */
public final class GirlChat {

    private GirlChat() {}

    public static void say(AbstractGirlEntity girl, Component text) {
        MinecraftServer server = girl.getServer();
        if (server == null) return;
        Component name = girl.getName().copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        if (girl.behavior().chatVisibility == GirlBehavior.ChatVisibility.PUBLIC) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("chat.type.text", name, text), false);
            return;
        }
        UUID ownerId = girl.getOwnerUUID();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("commands.message.display.incoming", name, text)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Only to her owner, whatever the visibility: errors and notes about her settings. */
    public static void tellOwner(AbstractGirlEntity girl, Component text) {
        MinecraftServer server = girl.getServer();
        UUID ownerId = girl.getOwnerUUID();
        ServerPlayer owner = server == null || ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(text);
    }
}
