package org.velvet.entity.girl.brain;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlManager;

import java.util.Locale;

/**
 * Hands what's said and what happens on the server to the girls who hear it.
 * <ul>
 *   <li>A message that starts with her name ("Jenny, follow me") or mentions @Jenny is for her: her owner
 *   reaches her anywhere in the same world, other players only close by and when she talks in public.</li>
 *   <li>With a language model she listens to everything else too: whatever her owner writes (the closest of
 *   his girls answers) and what players near her say.</li>
 *   <li>Advancements, deaths and players coming and going she may comment on (language models only, and
 *   not every time): her owner's always matter more.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Velvet.MODID)
public final class ChatListener {

    private ChatListener() {}

    private static final double OTHERS_RANGE = 24;
    private static final double OWNER_RANGE = 96;

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String text = event.getRawText();
        AbstractGirlEntity addressed = null, listening = null;
        String rest = null;
        double best = Double.MAX_VALUE, bestListener = Double.MAX_VALUE;
        for (AbstractGirlEntity girl : GirlManager.all()) {
            if (girl.level().isClientSide() || girl.level() != player.level() || !girl.isAlive()) continue;
            boolean owner = player.getUUID().equals(girl.getOwnerUUID());
            double dist = girl.distanceToSqr(player);
            boolean inPublic = girl.behavior().chatVisibility == GirlBehavior.ChatVisibility.PUBLIC;
            String message = addressedTo(girl.getName().getString(), text);
            if (message != null) {
                if (!owner && (dist > OTHERS_RANGE * OTHERS_RANGE || !inPublic)) continue;
                // her owner's own girl first, then the closest
                double rank = dist - (owner ? 1.0E9 : 0);
                if (rank < best) {
                    best = rank;
                    addressed = girl;
                    rest = message;
                }
                continue;
            }
            // not for anyone in particular: a girl with a language model may listen in
            GirlBehavior b = girl.behavior();
            if (!b.provider.isExternal() || !b.hearAll) continue;
            boolean hears = owner ? dist < OWNER_RANGE * OWNER_RANGE : inPublic && dist < OTHERS_RANGE * OTHERS_RANGE;
            if (!hears) continue;
            double rank = dist - (owner ? 1.0E9 : 0);
            if (rank < bestListener) {
                bestListener = rank;
                listening = girl;
            }
        }
        if (addressed != null) {
            AbstractGirlEntity girl = addressed;
            String message = rest;
            // after the player's own line has gone out
            player.server.execute(() -> girl.brain().hear(player, message, true));
        } else if (listening != null) {
            AbstractGirlEntity girl = listening;
            player.server.execute(() -> girl.brain().hear(player, text, false));
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        AdvancementHolder holder = event.getAdvancement();
        var display = holder.value().display().orElse(null);
        if (display == null || !display.shouldAnnounceChat() || !(event.getEntity() instanceof ServerPlayer player)) return;
        String title = display.getTitle().getString();
        String id = holder.id().getPath();
        boolean big = id.equals("end/kill_dragon") || id.equals("end/respawn_dragon") || id.equals("nether/create_full_beacon")
                || id.equals("adventure/kill_all_mobs") || display.getType() == net.minecraft.advancements.AdvancementType.CHALLENGE;
        tell(player, player.getGameProfile().getName() + " has made the advancement [" + title + "]", true, girl -> {
            if (player.getUUID().equals(girl.getOwnerUUID())) girl.psyche().onOwnerAdvancement(big, title);
        });
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String how = event.getSource().getLocalizedDeathMessage(player).getString();
        tell(player, how, true, girl -> {
            if (player.getUUID().equals(girl.getOwnerUUID())) {
                girl.psyche().note("your owner died: " + how);
                girl.psyche().set(org.velvet.entity.girl.psyche.Emotion.SADNESS, girl.psyche().get(org.velvet.entity.girl.psyche.Emotion.SADNESS) + 0.4F);
                girl.psyche().set(org.velvet.entity.girl.psyche.Emotion.FEAR, girl.psyche().get(org.velvet.entity.girl.psyche.Emotion.FEAR) + 0.2F);
            }
        });
    }

    @SubscribeEvent
    public static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tell(player, player.getGameProfile().getName() + " joined the game", false, girl -> {
                if (player.getUUID().equals(girl.getOwnerUUID())) girl.brain().thinkSoon();
            });
        }
    }

    @SubscribeEvent
    public static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tell(player, player.getGameProfile().getName() + " left the game", false, girl -> {});
        }
    }

    /** Every girl who'd know about it: her owner's girls, and the ones near where it happened. */
    private static void tell(Player about, String event, boolean important, java.util.function.Consumer<AbstractGirlEntity> also) {
        for (AbstractGirlEntity girl : GirlManager.all()) {
            if (girl.level().isClientSide() || !girl.isAlive()) continue;
            boolean owners = about.getUUID().equals(girl.getOwnerUUID());
            boolean near = girl.level() == about.level() && girl.distanceToSqr(about) < 48 * 48;
            if (!owners && !near) continue;
            also.accept(girl);
            girl.brain().hearEvent(event, important && owners);
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
