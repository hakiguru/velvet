package org.velvetlite;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/velvet_lite-common.toml. Only its version so far; settings come here as they're added. */
public final class Config {
    private Config() {}

    /** Bumped when the file's layout changes, so older files can be migrated. */
    public static final int CURRENT_VERSION = 1;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Version of this file's layout. Set by the mod; don't change it.")
            .defineInRange("configVersion", CURRENT_VERSION, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
