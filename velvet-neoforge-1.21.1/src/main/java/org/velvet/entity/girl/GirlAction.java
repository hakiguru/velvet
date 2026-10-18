package org.velvet.entity.girl;

import org.jetbrains.annotations.Nullable;

/**
 * What a girl is doing right now; synced to clients and drives the "action" animation controller.
 * <p>
 * Each state plays {@code animation.<folder>.<animation>}. ONCE stages hand over to {@link #next()} after
 * {@link #length} ticks. The main part of a scene is a pair of loops, a slow and a fast one: the partner
 * holds the speed key for the fast one. Every finished cycle fills the progress bar a bit (the fast loop
 * several times quicker, see GirlSceneController), and the scene ends once the bar is full.
 */
public enum GirlAction {

    NULL(null, Play.LOOP, 0, null, Partner.NONE),

    // ---- Solo ----
    STRIP("strip", Play.ONCE, 101, "NULL", Partner.NONE),
    WAVE("wave", Play.ONCE, 71, "NULL", Partner.NONE),

    // ---- Paid scenes; PAYMENT runs first in survival and then hands over to the scene's first stage ----
    PAYMENT("payment", Play.ONCE, 186, null, Partner.ATTACHED),
    /** Undressing before a scene that needs it; the controller then starts the scene itself. */
    SCENE_STRIP("strip", Play.ONCE, 101, null, Partner.ATTACHED),

    BJ_INTRO("blowjobintro", Play.ONCE, 986, "BJ_SUCK", Partner.ATTACHED),
    BJ_SUCK("blowjobsuck", Pace.SLOW, 120.8F, "BJ_THRUST", "BJ_CUM"),
    BJ_THRUST("blowjobthrust", Pace.FAST, 35.2F, "BJ_SUCK", "BJ_CUM"),
    BJ_CUM("blowjobcum", Play.ONCE, 236, "NULL", Partner.ATTACHED),

    PZ_START("paizuri_start", Play.ONCE, 173, "PZ_SLOW", Partner.ATTACHED),
    PZ_SLOW("paizuri_slow", Pace.SLOW, 67.5F, "PZ_FAST", "PZ_CUM"),
    PZ_FAST("paizuri_fast", Pace.FAST, 22.0F, "PZ_SLOW", "PZ_CUM"),
    PZ_CUM("paizuri_cum", Play.ONCE, 230, "NULL", Partner.ATTACHED),

    // Doggy: she walks to a free spot next to a bed, climbs onto it and waits there until the partner
    // steps onto the spot she came from; only then is he attached and the scene goes on.
    /** Walking to the bed; not an animation stage, the girl uses her movement animations meanwhile. */
    DOGGY_TO_BED(null, Play.LOOP, 0, null, Partner.NEARBY),
    DOGGY_ON_BED("doggygoonbed", Play.ONCE, 130, "DOGGY_WAIT", Partner.NEARBY),
    DOGGY_WAIT("doggywait", Play.LOOP, 0, null, Partner.NEARBY),
    DOGGY_START("doggystart", Play.ONCE, 198, "DOGGY_SLOW", Partner.ATTACHED),
    DOGGY_SLOW("doggyslow", Pace.SLOW, 46.4F, "DOGGY_FAST", "DOGGY_CUM"),
    DOGGY_FAST("doggyfast_soft", Pace.FAST, 21.6F, "DOGGY_SLOW", "DOGGY_CUM"),
    DOGGY_CUM("doggycum", Play.ONCE, 134, "NULL", Partner.ATTACHED);

    public enum Play { ONCE, LOOP }

    /**
     * NEARBY: a player takes part but still walks around himself (drawn as usual).
     * ATTACHED: he's locked to the girl and drawn through the model's "steve" bone.
     */
    public enum Partner { NONE, NEARBY, ATTACHED }

    /** Loops of the main part of a scene; the progress bar only fills during these. */
    public enum Pace { NONE, SLOW, FAST }

    @Nullable public final String animation;
    public final Play play;
    /** Ticks until {@link #next()} for ONCE stages; 0 for loops. */
    public final int length;
    @Nullable private final String nextName;
    public final Partner partner;
    public final Pace pace;
    /** For paced loops: length of one animation cycle in ticks; speed changes and the finale wait for its end. */
    public final float cycle;
    /** For paced loops: the stage played at the end of the scene. */
    @Nullable private final String finishName;

    GirlAction(@Nullable String animation, Play play, int length, @Nullable String next, Partner partner) {
        this(animation, play, length, next, partner, Pace.NONE, 0, null);
    }

    /** A paced loop; {@code other} is the loop of the opposite pace. */
    GirlAction(String animation, Pace pace, float cycle, String other, String finish) {
        this(animation, Play.LOOP, 0, other, Partner.ATTACHED, pace, cycle, finish);
    }

    GirlAction(@Nullable String animation, Play play, int length, @Nullable String next, Partner partner,
               Pace pace, float cycle, @Nullable String finish) {
        this.animation = animation;
        this.play = play;
        this.length = length;
        this.nextName = next;
        this.partner = partner;
        this.pace = pace;
        this.cycle = cycle;
        this.finishName = finish;
    }

    /**
     * ONCE stages: the stage that follows, or null if the controller decides (PAYMENT, SCENE_STRIP).
     * Paced loops: the loop of the opposite pace.
     */
    @Nullable
    public GirlAction next() {
        return nextName == null ? null : valueOf(nextName);
    }

    @Nullable
    public GirlAction finish() {
        return finishName == null ? null : valueOf(finishName);
    }

    /** A player takes part in this stage (he may still be walking up to the girl). */
    public boolean hasPartner() {
        return partner != Partner.NONE;
    }

    /** The partner is locked to the girl and drawn through her model. */
    public boolean isAttached() {
        return partner == Partner.ATTACHED;
    }

    public boolean isPaced() {
        return pace != Pace.NONE;
    }

    /** Busy girls don't wander, follow or react to commands. */
    public boolean isBusy() {
        return this != NULL;
    }

    public static GirlAction byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            // saves from older versions used the legacy action names
            return NULL;
        }
    }
}
