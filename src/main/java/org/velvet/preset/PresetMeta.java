package org.velvet.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.GirlType;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a custom preset is ({@code preset.json} in its folder): its name, the built-in preset it's made from
 * (every file it doesn't have comes from there), height, voice ("" for none, a built-in voice, or "own" for
 * the sounds in its own folder), and whether it has a dressed model to strip and see-through parts.
 */
public record PresetMeta(String id, String name, String base, float height, String voice, boolean strips, boolean translucent) {

    public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");
    public static final float MIN_HEIGHT = 0.8F, MAX_HEIGHT = 2.6F;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public PresetMeta {
        name = name.length() > 32 ? name.substring(0, 32) : name;
        height = Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        if (GirlType.byId(base) == null || GirlType.byId(base).isCustom()) base = "jenny";
    }

    /** An id for a new preset from its name: lower case letters, digits and underscores. */
    public static String idFor(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty()) s = "preset";
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    /** The voice as GirlType has it: null, a built-in voice folder, or "custom:<id>". */
    @Nullable
    public String voiceFolder() {
        if (voice.isEmpty()) return null;
        if (voice.equals("own")) return "custom:" + id;
        return voice;
    }

    public GirlType register() {
        return GirlType.registerCustom(id, name, voiceFolder(), height, strips, translucent, GirlType.byId(base));
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("base", base);
        o.addProperty("height", height);
        o.addProperty("voice", voice);
        o.addProperty("strips", strips);
        o.addProperty("translucent", translucent);
        return GSON.toJson(o);
    }

    @Nullable
    public static PresetMeta fromJson(String id, String json) {
        if (!ID.matcher(id).matches() || GirlType.byId(id) != null && !GirlType.byId(id).isCustom()) return null;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return new PresetMeta(id, string(o, "name", id), string(o, "base", "jenny"),
                    o.has("height") ? o.get("height").getAsFloat() : 1.8F, string(o, "voice", ""),
                    o.has("strips") && o.get("strips").getAsBoolean(), o.has("translucent") && o.get("translucent").getAsBoolean());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }
}
