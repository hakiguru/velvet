package org.velvet.entity.girl.brain;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;

import java.util.UUID;

/**
 * What a girl says in chat: to her owner only, or to the whole server, as set in her behavior ("chat" →
 * "visibility"). Either way it's an ordinary chat line, "&lt;Name&gt; text", like a player talking.
 */
public final class GirlChat {

    private GirlChat() {}

    public static void say(AbstractGirlEntity girl, Component text) {
        MinecraftServer server = girl.getServer();
        if (server == null) return;
        Component name = girl.getName().copy().withStyle(ChatFormatting.LIGHT_PURPLE);
        Component line = Component.translatable("chat.type.text", name, text);
        if (girl.behavior().chatVisibility == GirlBehavior.ChatVisibility.PUBLIC) {
            server.getPlayerList().broadcastSystemMessage(line, false);
            return;
        }
        UUID ownerId = girl.getOwnerUUID();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(line);
    }

    /** Only to her owner, whatever the visibility: errors and notes about her settings. */
    public static void tellOwner(AbstractGirlEntity girl, Component text) {
        MinecraftServer server = girl.getServer();
        UUID ownerId = girl.getOwnerUUID();
        ServerPlayer owner = server == null || ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(text);
    }
}
