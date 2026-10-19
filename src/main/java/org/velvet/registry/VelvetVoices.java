package org.velvet.registry;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The presets' voices: for each voice (a folder under sounds/girls/) its kinds of sounds ("moan", "giggle",
 * ...), each with a few takes to pick from at random. Elsie's were registered before there were several.
 */
public final class VelvetVoices {

    private VelvetVoices() {}

    /** voice, kind and how many takes, as in the sounds folder (and sounds.json). */
    private static final List<String> TABLE = List.of(
            "ellie aftersessionmoan 5",
            "ellie ahh 10",
            "ellie bjmoan 13",
            "ellie cometomommy 2",
            "ellie giggle 5",
            "ellie goodboy 2",
            "ellie happyoh 3",
            "ellie heavybreathing 9",
            "ellie hmph 4",
            "ellie huh 2",
            "ellie lightbreathing 8",
            "ellie lipsound 10",
            "ellie mmm 9",
            "ellie moan 9",
            "ellie mommyhorny 2",
            "ellie sadoh 2",
            "ellie sigh 2",
            "bia ahh 8",
            "bia bjmoan 5",
            "bia breath 4",
            "bia giggle 3",
            "bia hey 4",
            "bia huh 3",
            "bia mmm 8",
            "luna ahh 18",
            "luna cutenya 12",
            "luna giggle 15",
            "luna happyoh 8",
            "luna hmph 6",
            "luna horninya 10",
            "luna huh 5",
            "luna lightbreathing 25",
            "luna mmm 8",
            "luna moan 10",
            "luna ouu 13",
            "luna owo 8",
            "luna sadoh 7",
            "luna sigh 8",
            "luna singing 8",
            "allie aftersessionmoan 4",
            "allie ahh 10",
            "allie bjmoan 14",
            "allie giggle 5",
            "allie happyoh 3",
            "allie heavybreathing 8",
            "allie hmph 5",
            "allie huh 2",
            "allie lightbreathing 11",
            "allie lipsound 14",
            "allie mmm 10",
            "allie moan 8",
            "allie sadoh 2",
            "allie scawy 3",
            "allie sigh 2");

    private static final Map<String, Map<String, DeferredHolder<SoundEvent, SoundEvent>[]>> VOICES = new HashMap<>();

    static {
        for (String row : TABLE) {
            String[] p = row.split(" ");
            VOICES.computeIfAbsent(p[0], v -> new HashMap<>())
                    .put(p[1], VelvetSounds.make("girls_" + p[0] + "_" + p[1], Integer.parseInt(p[2])));
        }
        Map<String, DeferredHolder<SoundEvent, SoundEvent>[]> elsie = VOICES.computeIfAbsent("elsie", v -> new HashMap<>());
        elsie.put("aftersessionmoan", VelvetSounds.GIRLS_ELSIE_AFTERSSNMN);
        elsie.put("ahh", VelvetSounds.GIRLS_ELSIE_AHH);
        elsie.put("bjmoan", VelvetSounds.GIRLS_ELSIE_BJMOAN);
        elsie.put("giggle", VelvetSounds.GIRLS_ELSIE_GIGGLE);
        elsie.put("happyoh", VelvetSounds.GIRLS_ELSIE_HAPPYOH);
        elsie.put("heavybreathing", VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING);
        elsie.put("hmph", VelvetSounds.GIRLS_ELSIE_HMPH);
        elsie.put("huh", VelvetSounds.GIRLS_ELSIE_HUH);
        elsie.put("lightbreathing", VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING);
        elsie.put("lipsound", VelvetSounds.GIRLS_ELSIE_LIPSOUND);
        elsie.put("mmm", VelvetSounds.GIRLS_ELSIE_MMM);
        elsie.put("moan", VelvetSounds.GIRLS_ELSIE_MOAN);
        elsie.put("sadoh", VelvetSounds.GIRLS_ELSIE_SADOH);
        elsie.put("sigh", VelvetSounds.GIRLS_ELSIE_SIGH);
    }

    /** Loads the class, so the sounds are registered with the others. */
    public static void init() {}

    /** A random take of the first of {@code kinds} the voice has; null if it has none of them. */
    @Nullable
    public static SoundEvent random(@Nullable String voice, String... kinds) {
        Map<String, DeferredHolder<SoundEvent, SoundEvent>[]> v = voice == null ? null : VOICES.get(voice);
        if (v == null) return null;
        for (String kind : kinds) {
            var takes = v.get(kind);
            if (takes != null) return VelvetSounds.random(takes);
        }
        return null;
    }
}
