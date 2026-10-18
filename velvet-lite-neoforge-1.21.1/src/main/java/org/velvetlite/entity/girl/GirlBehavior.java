package org.velvetlite.entity.girl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;

/**
 * How a girl behaves, edited in the NPC editor: as a form on the AI tab or as raw JSON on its own tab. Saved
 * with the girl as that JSON, so keys this version doesn't know (from a newer one, or added by hand) are kept.
 * Server side only; the editor gets a copy when it opens.
 */
public final class GirlBehavior {

    public static final int FORMAT = 1;
    /** Longest JSON the editor sends and the girl keeps. */
    public static final int MAX_LENGTH = 8192;

    public static final double MIN_STOP = 2, MAX_STOP = 8;
    public static final double MIN_RUN = 4, MAX_RUN = 24;
    public static final double MIN_TELEPORT = 12, MAX_TELEPORT = 64;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Wanders around on her own while not following anyone and not sent anywhere. */
    public boolean wander = true;
    /** Picks up items lying next to her (what she keeps when her inventory is full depends on her state). */
    public boolean pickUpItems = true;
    /** Eats food from her inventory and hands when hungry. */
    public boolean autoEat = true;
    /** Runs from monsters nearby. */
    public boolean fleeMonsters = true;
    /** Following: stops this close to the owner. */
    public double followStopDistance = 3;
    /** Following: runs when further than this (and not too tired). */
    public double followRunDistance = 8;
    /** Following: teleports to the owner when further than this. */
    public double followTeleportDistance = 24;

    private JsonObject extra = new JsonObject();

    public JsonObject toJsonObject() {
        JsonObject json = extra.deepCopy();
        json.addProperty("format", FORMAT);
        json.addProperty("wander", wander);
        json.addProperty("pick_up_items", pickUpItems);
        json.addProperty("auto_eat", autoEat);
        json.addProperty("flee_monsters", fleeMonsters);
        JsonObject follow = json.has("follow") && json.get("follow").isJsonObject()
                ? json.getAsJsonObject("follow") : new JsonObject();
        follow.addProperty("stop_distance", followStopDistance);
        follow.addProperty("run_distance", followRunDistance);
        follow.addProperty("teleport_distance", followTeleportDistance);
        json.add("follow", follow);
        return json;
    }

    public String toJson() {
        return GSON.toJson(toJsonObject());
    }

    /**
     * Reads behavior from JSON; missing keys keep their defaults, numbers are clamped to sane ranges.
     *
     * @throws JsonParseException if it isn't a JSON object or a known key has the wrong type
     */
    public static GirlBehavior fromJson(String text) {
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonObject()) throw new JsonParseException("expected a JSON object { ... }");
        JsonObject json = root.getAsJsonObject();
        GirlBehavior b = new GirlBehavior();
        b.wander = bool(json, "wander", b.wander);
        b.pickUpItems = bool(json, "pick_up_items", b.pickUpItems);
        b.autoEat = bool(json, "auto_eat", b.autoEat);
        b.fleeMonsters = bool(json, "flee_monsters", b.fleeMonsters);
        if (json.has("follow")) {
            if (!json.get("follow").isJsonObject()) throw new JsonParseException("\"follow\" must be an object");
            JsonObject follow = json.getAsJsonObject("follow");
            b.followStopDistance = Mth.clamp(number(follow, "stop_distance", b.followStopDistance), MIN_STOP, MAX_STOP);
            b.followRunDistance = Mth.clamp(number(follow, "run_distance", b.followRunDistance), MIN_RUN, MAX_RUN);
            b.followTeleportDistance = Mth.clamp(number(follow, "teleport_distance", b.followTeleportDistance), MIN_TELEPORT, MAX_TELEPORT);
        }
        b.extra = json.deepCopy();
        return b;
    }

    /** Like {@link #fromJson}, but falls back to the defaults (for saves). */
    public static GirlBehavior fromJsonOrDefault(String text) {
        try {
            return fromJson(text);
        } catch (RuntimeException e) {
            return new GirlBehavior();
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        JsonElement e = json.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException("\"" + key + "\" must be true or false");
        }
        return e.getAsBoolean();
    }

    private static double number(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        JsonElement e = json.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("\"" + key + "\" must be a number");
        }
        return e.getAsDouble();
    }
}
