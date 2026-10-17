package org.velvetlite.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/velvet_lite-client.toml */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue SHADING_SOFTNESS = BUILDER
            .comment("How much NPC model lighting is evened out across faces.",
                     "Models are built from many thin rotated cubes; with vanilla per-face lighting every cube",
                     "gets its own brightness and curves look faceted. 0 = vanilla lighting, 1 = flat (no shading).")
            .defineInRange("shadingSoftness", 0.6, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
