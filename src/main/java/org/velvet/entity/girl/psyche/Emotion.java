package org.velvet.entity.girl.psyche;

import java.util.Locale;

/**
 * The basic emotions, each from 0 to 1 in steps of 0.1 (that's how they're shown and synced; inside they move
 * smoothly). Unlike her character they change all the time: in a second when something scares her, over
 * minutes when she's sad.
 * <p>
 * {@link #rise} and {@link #fade} are how fast each one comes and goes (per second, towards where the
 * situation puts it): fear and disgust flare up at once and pass quickly, sadness creeps in and lingers,
 * anger holds a grudge for a while.
 */
public enum Emotion {
    SADNESS(0xFF5C7BD9, 0.35F, 0.035F),
    JOY(0xFFF2C94C, 1.6F, 0.12F),
    FEAR(0xFF9B59D0, 3.5F, 0.45F),
    ANXIETY(0xFFE08A3C, 0.6F, 0.08F),
    DISGUST(0xFF6DAA45, 3.0F, 0.35F),
    SHAME(0xFFE77FA8, 1.5F, 0.07F),
    ANGER(0xFFD9483B, 2.2F, 0.1F);

    /** Bar colour in the editor. */
    public final int color;
    public final float rise;
    public final float fade;

    Emotion(int color, float rise, float fade) {
        this.color = color;
        this.rise = rise;
        this.fade = fade;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "emotion.velvet." + id();
    }

    /** Shown and synced in tenths. */
    public static float tenth(float value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 10) / 10.0F;
    }
}
