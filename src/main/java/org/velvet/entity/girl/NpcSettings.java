package org.velvet.entity.girl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Settings of one NPC that players don't change in the editor: her instincts and the debug switches, and a copy
 * of the rest the debug tool sets (her levels and her character; her name and preset just to tell the files
 * apart). Each NPC has her own file, {@code config/velvet/npcs/<her id>.json}, written whenever she's saved; it
 * can be edited by hand while the world is closed (read again whenever she's loaded) or with the debug tool.
 * Keys this version doesn't know are kept. Her character also has a file of its own (with her emotions), see
 * {@link org.velvet.entity.girl.psyche.PsycheStore}: whichever of the two was changed last wins.
 * <pre>
 * {
 *   "self_preservation": { "enabled": true, "fight_back": true, "stealth": true, "dig_in": true,
 *                          "pillar_up": true, "shelter": true, "gather_blocks": true, "mine_ores": true,
 *                          "use_shield": true, "use_items": true, "ask_for_help": true },
 *   "debug": { "ai": true, "damage_from_players": true, "invulnerable": false },
 *   "npc": { "name": "Jenny", "preset": "jenny" },
 *   "levels": { "capacity": 2, "endurance": 0, ... },
 *   "personality": { "sociability": 0.62, ... }
 * }
 * </pre>
 */
public final class NpcSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // ---- self-preservation: all on by default ----
    /** All the tactics below; without it she only runs. */
    public boolean survival = true;
    /** Fights monsters she can beat. */
    public boolean fightBack = true;
    /** Sneaks when she sees a warden or sculk close by. */
    public boolean stealth = true;
    /** Digs in and covers herself when she can't get away. */
    public boolean digIn = true;
    /** Builds a pillar under herself to get out of reach. */
    public boolean pillarUp = true;
    /** Walls herself in when cornered. */
    public boolean shelter = true;
    /** Mines blocks around her to build with when she has too few. */
    public boolean gatherBlocks = true;
    /** Mines ores she sees while it's safe. */
    public boolean mineOres = true;
    /** Blocks arrows and hits with a shield. */
    public boolean useShield = true;
    /** Food, golden apples, potions, totems, ender pearls and her best gear. */
    public boolean useItems = true;
    /** Calls for help in chat when she has nothing to defend herself with, or nothing to eat. */
    public boolean askForHelp = true;

    // ---- debug ----
    /** Off: she stands still like a mob without AI. */
    public boolean ai = true;
    public boolean damageFromPlayers = true;
    public boolean invulnerable = false;

    // ---- the rest of what the debug editor sets, kept here too to see and change by hand ----
    /** Who she is, for whoever opens the file (not read back). */
    public String name = "", preset = "";
    /** Her skill levels by skill id; -1 for one the file doesn't give. */
    public final int[] levels = filledLevels();
    /** Her character, trait id to 0.01..1.00; null if the file doesn't give it. */
    @Nullable public JsonObject personality;

    private JsonObject extra = new JsonObject();

    private static int[] filledLevels() {
        int[] l = new int[GirlSkill.values().length];
        java.util.Arrays.fill(l, -1);
        return l;
    }

    public JsonObject toJsonObject() {
        JsonObject json = extra.deepCopy();
        JsonObject s = object(json, "self_preservation");
        s.addProperty("enabled", survival);
        s.addProperty("fight_back", fightBack);
        s.addProperty("stealth", stealth);
        s.addProperty("dig_in", digIn);
        s.addProperty("pillar_up", pillarUp);
        s.addProperty("shelter", shelter);
        s.addProperty("gather_blocks", gatherBlocks);
        s.addProperty("mine_ores", mineOres);
        s.addProperty("use_shield", useShield);
        s.addProperty("use_items", useItems);
        s.addProperty("ask_for_help", askForHelp);
        JsonObject d = object(json, "debug");
        d.addProperty("ai", ai);
        d.addProperty("damage_from_players", damageFromPlayers);
        d.addProperty("invulnerable", invulnerable);
        // her character used to have a placeholder here; it has its own file now
        json.remove("character");
        JsonObject who = object(json, "npc");
        who.addProperty("name", name);
        who.addProperty("preset", preset);
        JsonObject l = object(json, "levels");
        for (GirlSkill skill : GirlSkill.values()) {
            if (levels[skill.ordinal()] >= 0) l.addProperty(skill.name().toLowerCase(java.util.Locale.ROOT), levels[skill.ordinal()]);
        }
        if (personality != null) json.add("personality", personality.deepCopy());
        return json;
    }

    public String toJson() {
        return GSON.toJson(toJsonObject());
    }

    public NpcSettings copy() {
        return fromJson(toJson());
    }

    /** Reads what it can; anything malformed keeps its default. */
    public static NpcSettings fromJson(String text) {
        NpcSettings n = new NpcSettings();
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            return n;
        }
        if (!root.isJsonObject()) return n;
        JsonObject json = root.getAsJsonObject();
        JsonObject s = child(json, "self_preservation");
        if (s != null) n.readSurvival(s);
        JsonObject d = child(json, "debug");
        if (d != null) {
            n.ai = bool(d, "ai", n.ai);
            n.damageFromPlayers = bool(d, "damage_from_players", n.damageFromPlayers);
            n.invulnerable = bool(d, "invulnerable", n.invulnerable);
        }
        JsonObject l = child(json, "levels");
        if (l != null) {
            for (GirlSkill skill : GirlSkill.values()) {
                String key = skill.name().toLowerCase(java.util.Locale.ROOT);
                try {
                    if (l.has(key)) n.levels[skill.ordinal()] = Math.clamp(l.get(key).getAsInt(), 0, GirlSkill.MAX_LEVEL);
                } catch (RuntimeException ignored) {
                    // not a number: left as it is
                }
            }
        }
        n.personality = child(json, "personality");
        n.extra = json.deepCopy();
        return n;
    }

    /** The instincts as they were kept in her behavior up to 1.4. */
    void readSurvival(JsonObject s) {
        survival = bool(s, "enabled", survival);
        fightBack = bool(s, "fight_back", fightBack);
        stealth = bool(s, "stealth", stealth);
        digIn = bool(s, "dig_in", digIn);
        pillarUp = bool(s, "pillar_up", pillarUp);
        shelter = bool(s, "shelter", shelter);
        gatherBlocks = bool(s, "gather_blocks", gatherBlocks);
        mineOres = bool(s, "mine_ores", mineOres);
        useShield = bool(s, "use_shield", useShield);
        useItems = bool(s, "use_items", useItems);
        askForHelp = bool(s, "ask_for_help", askForHelp);
    }

    // ---- files ----

    public static Path file(MinecraftServer server, UUID girl) {
        return org.velvet.VelvetPaths.npc(girl);
    }

    /** Where 1.5 kept them: in the world's folder. */
    private static Path oldFile(MinecraftServer server, UUID girl) {
        return server.getWorldPath(LevelResource.ROOT).resolve(Velvet.MODID).resolve("npcs").resolve(girl + ".json");
    }

    /** Her file, or null if she has none yet. */
    @Nullable
    public static NpcSettings read(MinecraftServer server, UUID girl) {
        Path file = file(server, girl);
        if (!Files.isRegularFile(file)) file = oldFile(server, girl);
        if (!Files.isRegularFile(file)) return null;
        try {
            return fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't read {}: {}", file, e.toString());
            return null;
        }
    }

    public void write(MinecraftServer server, UUID girl) {
        Path file = file(server, girl);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't write {}: {}", file, e.toString());
        }
    }

    // ---- JSON helpers ----

    private static JsonObject object(JsonObject json, String key) {
        JsonObject child = json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
        json.add(key, child);
        return child;
    }

    @Nullable
    private static JsonObject child(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : null;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }
}
