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

    /** What a line is, so her talk settings can let it through or not. */
    public enum Kind { ANSWER, HELP, HUNGER, HURT, JOY_SELF, JOY_OWNER, EVENT, GREETING, IDLE }

    /** Whether her talk settings let her say this kind of thing. */
    public static boolean allowed(AbstractGirlEntity girl, Kind kind) {
        GirlBehavior b = girl.behavior();
        if (!b.talk) return false;
        return switch (kind) {
            case ANSWER -> true;
            case HELP -> b.callForHelp;
            case HUNGER -> b.complainHunger && b.chattiness >= 1;
            case HURT -> b.complainHurt && b.chattiness >= 1;
            case JOY_SELF -> b.joySelf && b.chattiness >= 1;
            case JOY_OWNER -> b.joyOwner && b.chattiness >= 1;
            case EVENT -> b.commentEvents && b.chattiness >= 1;
            case GREETING -> b.greet && b.chattiness >= 1;
            case IDLE -> b.chattiness >= 2;
        };
    }

    public static void say(AbstractGirlEntity girl, Component text) {
        say(girl, text, Kind.ANSWER);
    }

    /** Says it, if her talk settings allow this kind of line. */
    public static void say(AbstractGirlEntity girl, Component text, Kind kind) {
        if (!allowed(girl, kind)) return;
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
