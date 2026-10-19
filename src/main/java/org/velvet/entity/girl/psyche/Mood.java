package org.velvet.entity.girl.psyche;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Her mood in a word or two: what the strongest emotions (and adrenaline) add up to. Emotions mix, so the
 * same two strong ones read differently together than either alone: joy and fear is a thrill, joy and
 * sadness bittersweet, anger and disgust indignation, fear and anger feeling cornered.
 */
public enum Mood {
    CALM, CONTENT, HAPPY, ELATED, THRILLED, BITTERSWEET, FLUSTERED,
    SAD, HEARTBROKEN, HURT, DOWNCAST,
    SCARED, TERRIFIED, CORNERED,
    NERVOUS, IRRITABLE, WORRIED,
    GROSSED_OUT, INDIGNANT,
    EMBARRASSED,
    ANGRY, FURIOUS,
    FOCUSED;

    public Component label() {
        return Component.translatable("mood.velvet." + name().toLowerCase());
    }

    /** In English, for the language models. */
    public String describe() {
        return name().toLowerCase().replace('_', ' ');
    }

    public static Mood of(Psyche p) {
        return ofValues(p::get, p.adrenaline());
    }

    /** From emotion values however they're known (the client only has the synced tenths). */
    public static Mood ofValues(java.util.function.ToDoubleFunction<Emotion> value, float adrenaline) {
        if (adrenaline >= 0.5F) return FOCUSED;
        Emotion[] order = Emotion.values().clone();
        Arrays.sort(order, Comparator.comparingDouble(e -> -value.applyAsDouble(e)));
        Emotion first = order[0], second = order[1];
        float a = (float) value.applyAsDouble(first), b = (float) value.applyAsDouble(second);
        if (a < 0.25F) return value.applyAsDouble(Emotion.JOY) >= 0.12F ? CONTENT : CALM;
        @Nullable Emotion mix = b >= 0.3F && b >= a * 0.6F ? second : null;
        return switch (first) {
            case JOY -> mix == Emotion.FEAR || mix == Emotion.ANGER ? THRILLED : mix == Emotion.SADNESS ? BITTERSWEET
                    : mix == Emotion.SHAME ? FLUSTERED : a >= 0.7F ? ELATED : HAPPY;
            case SADNESS -> mix == Emotion.ANGER ? HURT : mix == Emotion.ANXIETY || mix == Emotion.FEAR ? WORRIED
                    : mix == Emotion.SHAME ? DOWNCAST : mix == Emotion.JOY ? BITTERSWEET : a >= 0.7F ? HEARTBROKEN : SAD;
            case FEAR -> mix == Emotion.ANGER ? CORNERED : mix == Emotion.JOY ? THRILLED : a >= 0.7F ? TERRIFIED : SCARED;
            case ANXIETY -> mix == Emotion.ANGER ? IRRITABLE : mix == Emotion.SADNESS ? WORRIED : NERVOUS;
            case DISGUST -> mix == Emotion.ANGER ? INDIGNANT : GROSSED_OUT;
            case SHAME -> mix == Emotion.JOY ? FLUSTERED : mix == Emotion.SADNESS ? DOWNCAST : EMBARRASSED;
            case ANGER -> mix == Emotion.FEAR ? CORNERED : mix == Emotion.DISGUST ? INDIGNANT : mix == Emotion.SADNESS ? HURT
                    : mix == Emotion.ANXIETY ? IRRITABLE : a >= 0.7F ? FURIOUS : ANGRY;
        };
    }
}
