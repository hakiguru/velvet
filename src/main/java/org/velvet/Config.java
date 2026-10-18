package org.velvet;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/velvet-common.toml. */
public final class Config {
    private Config() {}

    /** Bumped when the file's layout changes, so older files can be migrated. */
    public static final int CURRENT_VERSION = 2;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Version of this file's layout. Set by the mod; don't change it.")
            .defineInRange("configVersion", CURRENT_VERSION, 1, Integer.MAX_VALUE);

    static {
        BUILDER.comment("Language models (Claude, ChatGPT, Gemini, local ones...) the NPCs can talk through.",
                "Requests are made by the server, with the API key of the NPC's owner.").push("ai");
    }

    public static final ModConfigSpec.BooleanValue AI_ENABLED = BUILDER
            .comment("Whether NPCs may use language models at all. Off: they all use their internal brain.")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue AI_LOCAL_FOR_EVERYONE = BUILDER
            .comment("Whether every player may point their NPC at a local or custom address (Ollama, LM Studio,",
                    "any OpenAI-compatible URL). The server makes these requests itself, so by default only",
                    "operators and the singleplayer host may.")
            .define("localForEveryone", false);

    public static final ModConfigSpec.IntValue AI_TIMEOUT_SECONDS = BUILDER
            .comment("How long to wait for an answer.")
            .defineInRange("timeoutSeconds", 40, 5, 300);

    public static final ModConfigSpec.IntValue AI_MAX_TOKENS = BUILDER
            .comment("Longest answer, in tokens.")
            .defineInRange("maxTokens", 300, 32, 4096);

    public static final ModConfigSpec.IntValue AI_COOLDOWN_SECONDS = BUILDER
            .comment("Least time between two requests of one NPC.")
            .defineInRange("cooldownSeconds", 3, 0, 120);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
