package org.velvet.entity.girl.psyche;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlManager;
import org.velvet.entity.girl.brain.GirlChat;
import org.velvet.entity.girl.brain.VoiceBridge;

/**
 * When an NPC fights for her life (adrenaline), her mind gets what it needs, the way a brain in danger takes
 * everything for getting out alive: the server thread runs at the highest priority the system gives, so it
 * isn't held up by the rest of the computer; other NPCs who aren't in danger leave their errands for later
 * ({@link #crisis()}); and if the game has so little memory left that it may stutter right then, her owner is
 * told once how to give it more. A running Java program can't give itself more memory than it was started
 * with: that only the launcher's memory setting (-Xmx) can do.
 */
@EventBusSubscriber(modid = Velvet.MODID)
public final class CrisisGuard {

    private CrisisGuard() {}

    private static final float CRISIS = 0.5F;
    /** Free memory below this share of the most the game may use counts as little. */
    private static final double LOW_MEMORY = 0.12;
    private static volatile boolean crisis;
    private static boolean warned;
    private static int normalPriority = Thread.NORM_PRIORITY;

    /** Somebody is fighting for her life right now: the others keep to what's necessary. */
    public static boolean crisis() {
        return crisis;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        VoiceBridge.tick(server);
        if (server.getTickCount() % 10 != 0) return;
        AbstractGirlEntity worst = null;
        for (AbstractGirlEntity girl : GirlManager.all()) {
            if (girl.level().isClientSide() || !girl.isAlive()) continue;
            if (girl.psyche().adrenaline() >= CRISIS && (worst == null || girl.psyche().adrenaline() > worst.psyche().adrenaline())) worst = girl;
        }
        boolean now = worst != null;
        if (now != crisis) {
            Thread thread = Thread.currentThread();
            if (now) {
                normalPriority = thread.getPriority();
                thread.setPriority(Thread.MAX_PRIORITY);
            } else {
                thread.setPriority(normalPriority);
            }
            crisis = now;
        }
        if (now && !warned) {
            Runtime rt = Runtime.getRuntime();
            long free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            if (free < rt.maxMemory() * LOW_MEMORY) {
                warned = true;
                GirlChat.tellOwner(worst, Component.translatable("chat.velvet.low_memory", worst.getName(),
                        rt.maxMemory() / (1024 * 1024)).withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
