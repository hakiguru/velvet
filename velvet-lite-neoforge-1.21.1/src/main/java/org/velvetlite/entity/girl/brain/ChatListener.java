package org.velvetlite.entity.girl.brain;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlBehavior;
import org.velvetlite.entity.girl.GirlManager;

import java.util.Locale;

/**
 * Hands chat messages to the girl they're meant for: one that starts with her name ("Elsie, follow me") or
 * mentions @Elsie. Her owner reaches her anywhere in the same world; other players only close by, and only
 * when she talks in public.
 */
@EventBusSubscriber(modid = VelvetLite.MODID)
public final class ChatListener {

    private ChatListener() {}

    private static final double OTHERS_RANGE = 24;

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String text = event.getRawText();
        AbstractGirlEntity addressed = null;
        String rest = null;
        double best = Double.MAX_VALUE;
        for (AbstractGirlEntity girl : GirlManager.all()) {
            if (girl.level().isClientSide() || girl.level() != player.level() || !girl.isAlive()) continue;
            String message = addressedTo(girl.getName().getString(), text);
            if (message == null) continue;
            boolean owner = player.getUUID().equals(girl.getOwnerUUID());
            double dist = girl.distanceToSqr(player);
            if (!owner && (dist > OTHERS_RANGE * OTHERS_RANGE || girl.behavior().chatVisibility != GirlBehavior.ChatVisibility.PUBLIC)) continue;
            // her owner's own girl first, then the closest
            double rank = dist - (owner ? 1.0E9 : 0);
            if (rank < best) {
                best = rank;
                addressed = girl;
                rest = message;
            }
        }
        if (addressed != null) {
            AbstractGirlEntity girl = addressed;
            String message = rest;
            // after the player's own line has gone out
            player.server.execute(() -> girl.brain().hear(player, message));
        }
    }

    /** What's left of the message if it's addressed to {@code name}, or null. */
    @Nullable
    static String addressedTo(String name, String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        String n = name.toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        if (lower.startsWith(n) && (lower.length() == n.length() || !Character.isLetterOrDigit(lower.charAt(n.length())))) {
            String rest = text.trim().substring(n.length()).replaceFirst("^[\\s,:;!?.-]+", "");
            return rest.isEmpty() ? text.trim() : rest;
        }
        int at = lower.indexOf("@" + n);
        if (at >= 0) {
            int end = at + 1 + n.length();
            if (end == lower.length() || !Character.isLetterOrDigit(lower.charAt(end))) {
                return (text.substring(0, at) + text.substring(end)).trim();
            }
        }
        return null;
    }
}
