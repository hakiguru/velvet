package org.velvet;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Where the mod keeps its files: everything in its own folder under the game's config folder.
 * <pre>
 * config/velvet/
 *   common.toml, client.toml          settings
 *   npcs/&lt;id&gt;.json                     each NPC's instincts and debug switches
 *   npcs/personalities/&lt;id&gt;.json       each NPC's character and how she feels
 *   presets/&lt;id&gt;/                      custom presets (model, texture, animations, sounds)
 * </pre>
 */
public final class VelvetPaths {

    private VelvetPaths() {}

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve(Velvet.MODID);
    }

    public static Path npcs() {
        return root().resolve("npcs");
    }

    public static Path npc(UUID id) {
        return npcs().resolve(id + ".json");
    }

    public static Path personalities() {
        return npcs().resolve("personalities");
    }

    public static Path personality(UUID id) {
        return personalities().resolve(id + ".json");
    }

    public static Path presets() {
        return root().resolve("presets");
    }

    /**
     * Settings files from before they had a folder (config/velvet-common.toml) move into it, so nothing set
     * there is lost. Runs before the configs are registered.
     */
    static void moveOldConfigs() {
        Path config = FMLPaths.CONFIGDIR.get();
        for (String side : new String[]{"common", "client"}) {
            Path old = config.resolve(Velvet.MODID + "-" + side + ".toml");
            Path now = root().resolve(side + ".toml");
            try {
                if (Files.isRegularFile(old) && !Files.exists(now)) {
                    Files.createDirectories(root());
                    Files.move(old, now);
                }
            } catch (IOException e) {
                Velvet.LOGGER.warn("Couldn't move {} to {}", old, now, e);
            }
        }
    }
}
