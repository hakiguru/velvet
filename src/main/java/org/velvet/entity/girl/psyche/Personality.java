package org.velvet.entity.girl.psyche;

import com.google.gson.JsonObject;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Her character: every {@link Trait} from 0.01 to 1.00. Rolled once when she's made and kept in her file
 * under {@code config/velvet/npcs/personalities/}; the debug editor can change it.
 */
public final class Personality {

    public static final float MIN = 0.01F, MAX = 1.0F;

    private final EnumMap<Trait, Float> traits = new EnumMap<>(Trait.class);

    private Personality() {
        for (Trait t : Trait.values()) traits.put(t, 0.5F);
    }

    /**
     * A new character: five factors first, then each trait as its factor (with its loading) plus a good
     * deal of its own; most come out in the middle, few at the ends, like in people.
     */
    public static Personality random(RandomSource random) {
        Map<Trait.Factor, Double> factors = new EnumMap<>(Trait.Factor.class);
        for (Trait.Factor f : Trait.Factor.values()) factors.put(f, random.nextGaussian());
        Personality p = new Personality();
        for (Trait t : Trait.values()) {
            double own = Math.sqrt(1 - t.loading * t.loading);
            double z = t.loading * factors.get(t.factor) + own * random.nextGaussian();
            // about 95% of characters between 0.15 and 0.85
            p.set(t, (float) (0.5 + z * 0.18));
        }
        return p;
    }

    public float get(Trait trait) {
        return traits.get(trait);
    }

    /** Clamped to 0.01..1 and rounded to two decimals. */
    public void set(Trait trait, float value) {
        traits.put(trait, round(value));
    }

    public static float round(float value) {
        return Math.round(Mth.clamp(value, MIN, MAX) * 100) / 100.0F;
    }

    public Personality copy() {
        Personality p = new Personality();
        p.traits.putAll(traits);
        return p;
    }

    // ---- How she takes things (1 is average) ----

    /**
     * How strongly she feels each emotion compared with an average girl: 0.4 for the calm end of a trait, up
     * to 1.8 for the other.
     */
    public float sensitivity(Emotion e) {
        float s = get(Trait.SENSITIVITY);
        return switch (e) {
            case SADNESS -> scale(0.5F * get(Trait.MELANCHOLY) + 0.3F * s + 0.2F * (1 - get(Trait.OPTIMISM)));
            case JOY -> scale(0.4F * get(Trait.OPTIMISM) + 0.3F * get(Trait.PLAYFULNESS) + 0.3F * get(Trait.WARMTH));
            case FEAR -> scale(0.5F * (1 - get(Trait.COURAGE)) + 0.3F * get(Trait.ANXIOUSNESS) + 0.2F * s);
            case ANXIETY -> scale(0.6F * get(Trait.ANXIOUSNESS) + 0.2F * s + 0.2F * (1 - get(Trait.RESILIENCE)));
            case DISGUST -> scale(0.6F * get(Trait.SQUEAMISHNESS) + 0.4F * get(Trait.TIDINESS));
            case SHAME -> scale(0.5F * get(Trait.MODESTY) + 0.3F * get(Trait.SHYNESS) + 0.2F * s);
            case ANGER -> scale(0.5F * get(Trait.TEMPER) + 0.2F * get(Trait.PRIDE) + 0.3F * (1 - get(Trait.PATIENCE)));
        };
    }

    /** 0.5 (average) maps to 1, the ends to 0.4 and 1.8. */
    private static float scale(float mix) {
        return mix < 0.5F ? 0.4F + mix * 1.2F : 1 + (mix - 0.5F) * 1.6F;
    }

    /** Where the emotion settles with nothing going on: a melancholic girl is always a bit sad, a cheerful one a bit happy. */
    public float baseline(Emotion e) {
        return switch (e) {
            case SADNESS -> Math.max(0, get(Trait.MELANCHOLY) - 0.45F) * 0.35F;
            case JOY -> Math.max(0, get(Trait.OPTIMISM) - 0.35F) * 0.3F;
            case ANXIETY -> Math.max(0, get(Trait.ANXIOUSNESS) - 0.5F) * 0.3F;
            default -> 0;
        };
    }

    /** How fast she gets over things: 0.5 to 1.6 times the average. */
    public float recovery() {
        return 0.5F + 1.1F * (0.6F * get(Trait.RESILIENCE) + 0.4F * get(Trait.OPTIMISM));
    }

    /**
     * A few words on her character for the language models: the traits furthest from the middle, as
     * "very anxious, quite warm, a little shy".
     */
    public String describe(int count) {
        Trait[] sorted = Trait.values().clone();
        java.util.Arrays.sort(sorted, (a, b) -> Float.compare(Math.abs(get(b) - 0.5F), Math.abs(get(a) - 0.5F)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(count, sorted.length); i++) {
            Trait t = sorted[i];
            float v = get(t);
            String how = v >= 0.85F ? "extremely " : v >= 0.7F ? "very " : v >= 0.55F ? "somewhat " : v <= 0.15F ? "not at all " : v <= 0.3F ? "hardly " : "not very ";
            if (sb.length() > 0) sb.append(", ");
            sb.append(how).append(t.id().replace('_', ' ')).append(String.format(Locale.ROOT, " (%.2f)", v));
        }
        return sb.toString();
    }

    // ---- File ----

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (Trait t : Trait.values()) o.addProperty(t.id(), get(t));
        return o;
    }

    /** Traits missing from the file (a newer version added them) are rolled. */
    public static Personality fromJson(JsonObject o, RandomSource random) {
        Personality rolled = random(random);
        Personality p = new Personality();
        for (Trait t : Trait.values()) {
            p.set(t, o.has(t.id()) ? o.get(t.id()).getAsFloat() : rolled.get(t));
        }
        return p;
    }
}
