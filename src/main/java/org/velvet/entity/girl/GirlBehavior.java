package org.velvet.entity.girl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.brain.AiProvider;
import org.velvet.entity.girl.loot.ItemCaste;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How a girl behaves, edited in the NPC editor: as forms on the AI tab or as raw JSON on its own tab. Saved
 * with the girl as that JSON, so keys this version doesn't know (from a newer one, or added by hand) are kept.
 * Server side only; the editor gets a copy when it opens. Her instincts used to be here as well; they are
 * in her own settings file now ({@link NpcSettings}).
 * <pre>
 * {
 *   "format": 2,
 *   "wander": true, "pick_up_items": true, "auto_eat": true, "flee_monsters": true,
 *   "follow": { "stop_distance": 3, "run_distance": 8, "teleport_distance": 24 },
 *   "chat": { "visibility": "private" },
 *   "brain": { "provider": "internal", "model": "", "endpoint": "", "persona": "",
 *              "hear_all": true, "comment_events": true, "autonomy": true, "vision": false },
 *   "voice": { "listen": false, "speak": false, "voice": "nova" },
 *   "talk": { "enabled": true, "chattiness": 2, "complain_hunger": true, "complain_hurt": true, "call_for_help": true,
 *             "joy_self": true, "joy_owner": true, "comment_events": true, "greet": true },
 *   "loot": { "castes": { "minecraft:stick": "valuable", "#minecraft:logs": "valuable" } }
 * }
 * </pre>
 */
public final class GirlBehavior {

    public static final int FORMAT = 2;
    /** Longest JSON the editor sends and the girl keeps. */
    public static final int MAX_LENGTH = 16384;
    public static final int MAX_PERSONA = 4000;

    public static final double MIN_STOP = 2, MAX_STOP = 8;
    public static final double MIN_RUN = 4, MAX_RUN = 24;
    public static final double MIN_TELEPORT = 12, MAX_TELEPORT = 64;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Who hears what she says in chat. */
    public enum ChatVisibility {
        /** Only her owner. */
        PRIVATE,
        /** Everyone on the server. */
        PUBLIC;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Wanders around on her own while not following anyone and not sent anywhere. */
    public boolean wander = true;
    /** Picks up items lying around (what she keeps when her inventory is full depends on their caste). */
    public boolean pickUpItems = true;
    /** Eats food from her inventory and hands when hungry. */
    public boolean autoEat = true;
    /** Runs from monsters. */
    public boolean fleeMonsters = true;
    /** Following: stops this close to the owner. */
    public double followStopDistance = 3;
    /** Following: runs when further than this (and not too tired). */
    public double followRunDistance = 8;
    /** Following: teleports to the owner when further than this. */
    public double followTeleportDistance = 24;

    public ChatVisibility chatVisibility = ChatVisibility.PRIVATE;

    // ---- brain ----
    public AiProvider provider = AiProvider.INTERNAL;
    /** The provider's model id; empty for its default. */
    public String model = "";
    /** Only for providers with a configurable address (local servers, custom endpoints). */
    public String endpoint = "";
    /** Who she is, for the language models: her character, how she talks. */
    public String persona = "";
    /** With a language model: she hears everything her owner says in chat (and players close by), not only what starts with her name. */
    public boolean hearAll = true;
    /** With a language model: she comments on advancements, deaths and who comes and goes, now and then. */
    public boolean commentEvents = true;
    /** With a language model: it decides what she does next when nothing's going on (every minute or so). */
    public boolean autonomy = true;
    /** With a model that sees images: she sends it what she sees, rendered from her eyes by her owner's game. */
    public boolean vision = false;

    // ---- voice (Simple Voice Chat) ----
    /** Hears players talking near her in voice chat (speech to text through her owner's OpenAI-compatible key). */
    public boolean voiceListen = false;
    /** Speaks her answers out loud in voice chat (text to speech, same key). */
    public boolean voiceSpeak = false;
    /** The text-to-speech voice. */
    public String ttsVoice = "nova";

    // ---- what she says by herself ("talk") ----
    /** Off: she can't say anything at all, in chat or out loud (and the language models know it). */
    public boolean talk = true;
    /** 0: only answers, 1: little, 2: as she is, 3: everything (and she reminds of her hunger early). */
    public int chattiness = 2;
    public boolean complainHunger = true, complainHurt = true, callForHelp = true, joySelf = true, joyOwner = true,
            greet = true;

    /** "loot" → "castes": item ids or #tags moved to another caste for her. */
    private final Map<String, ItemCaste> casteOverrides = new LinkedHashMap<>();

    private JsonObject extra = new JsonObject();
    /** Instincts from a behavior of 1.4 or older, for {@link NpcSettings}; null otherwise. */
    @Nullable transient JsonObject legacySurvival;

    public JsonObject toJsonObject() {
        JsonObject json = extra.deepCopy();
        json.addProperty("format", FORMAT);
        json.addProperty("wander", wander);
        json.addProperty("pick_up_items", pickUpItems);
        json.addProperty("auto_eat", autoEat);
        json.addProperty("flee_monsters", fleeMonsters);
        JsonObject follow = object(json, "follow");
        follow.addProperty("stop_distance", followStopDistance);
        follow.addProperty("run_distance", followRunDistance);
        follow.addProperty("teleport_distance", followTeleportDistance);
        object(json, "chat").addProperty("visibility", chatVisibility.id());
        JsonObject brain = object(json, "brain");
        brain.addProperty("provider", provider.id);
        brain.addProperty("model", model);
        brain.addProperty("endpoint", endpoint);
        brain.addProperty("persona", persona);
        brain.addProperty("hear_all", hearAll);
        brain.addProperty("comment_events", commentEvents);
        brain.addProperty("autonomy", autonomy);
        brain.addProperty("vision", vision);
        JsonObject talkJson = object(json, "talk");
        talkJson.addProperty("enabled", talk);
        talkJson.addProperty("chattiness", chattiness);
        talkJson.addProperty("complain_hunger", complainHunger);
        talkJson.addProperty("complain_hurt", complainHurt);
        talkJson.addProperty("call_for_help", callForHelp);
        talkJson.addProperty("joy_self", joySelf);
        talkJson.addProperty("joy_owner", joyOwner);
        talkJson.addProperty("comment_events", commentEvents);
        talkJson.addProperty("greet", greet);
        JsonObject voice = object(json, "voice");
        voice.addProperty("listen", voiceListen);
        voice.addProperty("speak", voiceSpeak);
        voice.addProperty("voice", ttsVoice);
        JsonObject loot = object(json, "loot");
        JsonObject castes = new JsonObject();
        casteOverrides.forEach((key, caste) -> castes.addProperty(key, caste.id()));
        loot.add("castes", castes);
        return json;
    }

    /** The object under {@code key} (kept with any unknown keys inside it), added if missing. */
    private static JsonObject object(JsonObject json, String key) {
        JsonObject child = json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
        json.add(key, child);
        return child;
    }

    public String toJson() {
        return GSON.toJson(toJsonObject());
    }

    public GirlBehavior copy() {
        return fromJsonOrDefault(toJson());
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
        JsonObject follow = child(json, "follow");
        if (follow != null) {
            b.followStopDistance = Mth.clamp(number(follow, "stop_distance", b.followStopDistance), MIN_STOP, MAX_STOP);
            b.followRunDistance = Mth.clamp(number(follow, "run_distance", b.followRunDistance), MIN_RUN, MAX_RUN);
            b.followTeleportDistance = Mth.clamp(number(follow, "teleport_distance", b.followTeleportDistance), MIN_TELEPORT, MAX_TELEPORT);
        }
        // up to 1.4: moved into her settings file the first time she's loaded
        if (json.has("self_preservation") && json.get("self_preservation").isJsonObject()) {
            b.legacySurvival = json.getAsJsonObject("self_preservation").deepCopy();
        }
        JsonObject chat = child(json, "chat");
        if (chat != null && chat.has("visibility")) {
            String v = string(chat, "visibility", "private");
            try {
                b.chatVisibility = ChatVisibility.valueOf(v.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("\"chat.visibility\" must be \"private\" or \"public\"");
            }
        }
        JsonObject brain = child(json, "brain");
        if (brain != null) {
            String id = string(brain, "provider", b.provider.id);
            AiProvider provider = AiProvider.byId(id);
            if (provider == null) throw new JsonParseException("unknown \"brain.provider\": " + id + " (" + AiProvider.idList() + ")");
            b.provider = provider;
            b.model = limit(string(brain, "model", b.model), 128);
            b.endpoint = limit(string(brain, "endpoint", b.endpoint), 256);
            b.persona = limit(string(brain, "persona", b.persona), MAX_PERSONA);
            b.hearAll = bool(brain, "hear_all", b.hearAll);
            b.commentEvents = bool(brain, "comment_events", b.commentEvents);
            b.autonomy = bool(brain, "autonomy", b.autonomy);
            b.vision = bool(brain, "vision", b.vision);
        }
        JsonObject t = child(json, "talk");
        if (t != null) {
            b.talk = bool(t, "enabled", b.talk);
            b.chattiness = (int) Mth.clamp(number(t, "chattiness", b.chattiness), 0, 3);
            b.complainHunger = bool(t, "complain_hunger", b.complainHunger);
            b.complainHurt = bool(t, "complain_hurt", b.complainHurt);
            b.callForHelp = bool(t, "call_for_help", b.callForHelp);
            b.joySelf = bool(t, "joy_self", b.joySelf);
            b.joyOwner = bool(t, "joy_owner", b.joyOwner);
            b.commentEvents = bool(t, "comment_events", b.commentEvents);
            b.greet = bool(t, "greet", b.greet);
        }
        JsonObject voice = child(json, "voice");
        if (voice != null) {
            b.voiceListen = bool(voice, "listen", b.voiceListen);
            b.voiceSpeak = bool(voice, "speak", b.voiceSpeak);
            b.ttsVoice = limit(string(voice, "voice", b.ttsVoice), 32);
        }
        JsonObject loot = child(json, "loot");
        JsonObject castes = loot == null ? null : child(loot, "castes");
        if (castes != null) {
            for (Map.Entry<String, JsonElement> e : castes.entrySet()) {
                String key = e.getKey().trim();
                if (!e.getValue().isJsonPrimitive()) throw new JsonParseException("\"loot.castes." + key + "\" must be a caste name");
                ItemCaste caste = ItemCaste.byId(e.getValue().getAsString());
                if (caste == null) throw new JsonParseException("unknown caste \"" + e.getValue().getAsString()
                        + "\" (junk, common, valuable, precious, legendary)");
                String id = key.startsWith("#") ? key.substring(1) : key;
                if (ResourceLocation.tryParse(id) == null) throw new JsonParseException("bad item id \"" + key + "\"");
                if (b.casteOverrides.size() < 256) b.casteOverrides.put(key, caste);
            }
        }
        b.extra = json.deepCopy();
        b.extra.remove("self_preservation");
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

    /** The caste she was told this item belongs to: by its id first, then by a tag; null if none. */
    @Nullable
    public ItemCaste casteOverride(ItemStack stack) {
        if (casteOverrides.isEmpty()) return null;
        ItemCaste byId = casteOverrides.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (byId != null) return byId;
        for (Map.Entry<String, ItemCaste> e : casteOverrides.entrySet()) {
            if (!e.getKey().startsWith("#")) continue;
            ResourceLocation tag = ResourceLocation.tryParse(e.getKey().substring(1));
            if (tag != null && stack.is(TagKey.<Item>create(Registries.ITEM, tag))) return e.getValue();
        }
        return null;
    }

    @Nullable
    private static JsonObject child(JsonObject json, String key) {
        if (!json.has(key)) return null;
        if (!json.get(key).isJsonObject()) throw new JsonParseException("\"" + key + "\" must be an object { ... }");
        return json.getAsJsonObject(key);
    }

    private static String limit(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
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

    private static String string(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        JsonElement e = json.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("\"" + key + "\" must be a string \"...\"");
        }
        return e.getAsString();
    }
}
