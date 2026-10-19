package org.velvet.entity.girl;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * NPC names are unique, like players': no two NPCs (anywhere in the world, loaded or not) and no NPC and
 * player share one. A new NPC gets a free name of her preset ("Jenny", "Jenny 2", ...).
 */
public final class Names {

    private Names() {}

    /** Whether someone else (another NPC, a player) has the name already; {@code self} doesn't count. */
    public static boolean taken(MinecraftServer server, String name, @Nullable UUID self) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().toLowerCase(Locale.ROOT).equals(n)) return true;
        }
        for (OwnedGirls.Entry e : OwnedGirls.get(server).all()) {
            if (e.girl().equals(self)) continue;
            if (e.appearance().getString("name").trim().toLowerCase(Locale.ROOT).equals(n)) return true;
        }
        for (AbstractGirlEntity g : GirlManager.all()) {
            if (g.level().isClientSide() || g.getGirlUUID().equals(self)) continue;
            if (g.getCustomNameString().trim().toLowerCase(Locale.ROOT).equals(n)) return true;
        }
        return false;
    }

    /** A free name of her preset: its name, then with 2, 3, ... after it. */
    public static String freeName(MinecraftServer server, GirlType preset, @Nullable UUID self) {
        String base = preset.isCustom() ? preset.displayName().getString()
                : preset.id().substring(0, 1).toUpperCase(Locale.ROOT) + preset.id().substring(1);
        String name = base;
        for (int i = 2; taken(server, name, self); i++) name = base + " " + i;
        return name;
    }
}
