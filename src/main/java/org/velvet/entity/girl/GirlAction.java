package org.velvet.entity.girl;

import org.jetbrains.annotations.Nullable;

/**
 * What a girl is doing right now; synced to clients and drives the "action" animation controller.
 * <p>
 * Each state plays {@code animation.<folder>.<animation>} of the girl's preset; each preset's scenes have
 * their own stages (see {@link org.velvet.entity.girl.scene.SceneType}). ONCE stages hand over to {@link #next()} after
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
    DOGGY_TO_BED(null, Play.LOOP, 0, "DOGGY_ON_BED", Partner.NEARBY),
    DOGGY_ON_BED("doggygoonbed", Play.ONCE, 130, "DOGGY_WAIT", Partner.NEARBY),
    DOGGY_WAIT("doggywait", Play.LOOP, 0, "DOGGY_START", Partner.NEARBY),
    DOGGY_START("doggystart", Play.ONCE, 198, "DOGGY_SLOW", Partner.ATTACHED),
    DOGGY_SLOW("doggyslow", Pace.SLOW, 46.4F, "DOGGY_FAST", "DOGGY_CUM"),
    DOGGY_FAST("doggyfast_soft", Pace.FAST, 21.6F, "DOGGY_SLOW", "DOGGY_CUM"),
    DOGGY_CUM("doggycum", Play.ONCE, 134, "NULL", Partner.ATTACHED),

    // ---- Slime ----
    SLIME_BJ_SUCK("blowjobsuck", Pace.SLOW, 120.0F, "SLIME_BJ_THRUST", "SLIME_BJ_CUM"),
    SLIME_BJ_THRUST("blowjobthrust", Pace.FAST, 42.4F, "SLIME_BJ_SUCK", "SLIME_BJ_CUM"),
    SLIME_BJ_CUM("blowjobcum", Play.ONCE, 231, "NULL", Partner.ATTACHED),
    SLIME_DOGGY_TO_BED(null, Play.LOOP, 0, "DOGGY_ON_BED", Partner.NEARBY),
    SLIME_DOGGY_ON_BED("doggygoonbed", Play.ONCE, 34, "SLIME_DOGGY_WAIT", Partner.NEARBY),
    SLIME_DOGGY_WAIT("doggywait", Play.LOOP, 0, "SLIME_DOGGY_START", Partner.NEARBY),
    SLIME_DOGGY_START("doggystart", Play.ONCE, 196, "SLIME_DOGGY_SLOW", Partner.ATTACHED),
    SLIME_DOGGY_SLOW("doggyslow", Pace.SLOW, 45.6F, "SLIME_DOGGY_FAST", "SLIME_DOGGY_CUM"),
    SLIME_DOGGY_FAST("doggyfast", Pace.FAST, 21.6F, "SLIME_DOGGY_SLOW", "SLIME_DOGGY_CUM"),
    SLIME_DOGGY_CUM("doggycum", Play.ONCE, 112, "NULL", Partner.ATTACHED),

    // ---- Ellie ----
    COWGIRL_START("cowgirlstart", Play.ONCE, 315, "COWGIRL_SLOW", Partner.ATTACHED),
    COWGIRL_SLOW("cowgirlslow2", Pace.SLOW, 64.0F, "COWGIRL_FAST", "COWGIRL_CUM"),
    COWGIRL_FAST("cowgirlfast", Pace.FAST, 68.0F, "COWGIRL_SLOW", "COWGIRL_CUM"),
    COWGIRL_CUM("cowgirlcum", Play.ONCE, 307, "NULL", Partner.ATTACHED),
    MISSIONARY_START("missionary_start", Play.ONCE, 65, "MISSIONARY_SLOW", Partner.ATTACHED),
    MISSIONARY_SLOW("missionary_slow", Pace.SLOW, 58.33F, "MISSIONARY_FAST", "MISSIONARY_CUM"),
    MISSIONARY_FAST("missionary_fast", Pace.FAST, 37.5F, "MISSIONARY_SLOW", "MISSIONARY_CUM"),
    MISSIONARY_CUM("missionary_cum", Play.ONCE, 289, "NULL", Partner.ATTACHED),

    // ---- Bia ----
    ANAL_PREPARE("anal_prepare", Play.ONCE, 110, "ANAL_START", Partner.ATTACHED),
    ANAL_START("anal_start", Play.ONCE, 57, "ANAL_SLOW", Partner.ATTACHED),
    ANAL_SLOW("anal_slow", Pace.SLOW, 29.6F, "ANAL_FAST", "ANAL_CUM"),
    ANAL_FAST("anal_fast", Pace.FAST, 18.4F, "ANAL_SLOW", "ANAL_CUM"),
    ANAL_CUM("anal_cum", Play.ONCE, 75, "NULL", Partner.ATTACHED),
    PRONE_INTRO("prone_doggy_intro", Play.ONCE, 38, "PRONE_INSERT", Partner.ATTACHED),
    PRONE_INSERT("prone_doggy_insert", Play.ONCE, 43, "PRONE_SOFT", Partner.ATTACHED),
    PRONE_SOFT("prone_doggy_soft", Pace.SLOW, 29.17F, "PRONE_HARD", "PRONE_CUM"),
    PRONE_HARD("prone_doggy_hard1", Pace.FAST, 40.83F, "PRONE_SOFT", "PRONE_CUM"),
    PRONE_CUM("prone_doggy_cum", Play.ONCE, 58, "NULL", Partner.ATTACHED),

    // ---- Allie ----
    DEEPTHROAT_PREPARE("deepthroat_prepare", Play.ONCE, 69, "DEEPTHROAT_START", Partner.ATTACHED),
    DEEPTHROAT_START("deepthroat_start", Play.ONCE, 64, "DEEPTHROAT_SLOW", Partner.ATTACHED),
    DEEPTHROAT_SLOW("deepthroat_slow", Pace.SLOW, 59.2F, "DEEPTHROAT_FAST", "DEEPTHROAT_CUM"),
    DEEPTHROAT_FAST("deepthroat_fast", Pace.FAST, 24.0F, "DEEPTHROAT_SLOW", "DEEPTHROAT_CUM"),
    DEEPTHROAT_CUM("deepthroat_cum", Play.ONCE, 46, "NULL", Partner.ATTACHED),
    REVERSE_COWGIRL_START("reverse_cowgirl_start", Play.ONCE, 88, "REVERSE_COWGIRL_SLOW", Partner.ATTACHED),
    REVERSE_COWGIRL_SLOW("reverse_cowgirl_slow1", Pace.SLOW, 47.5F, "REVERSE_COWGIRL_FAST", "REVERSE_COWGIRL_CUM"),
    REVERSE_COWGIRL_FAST("reverse_cowgirl_fastc1", Pace.FAST, 43.33F, "REVERSE_COWGIRL_SLOW", "REVERSE_COWGIRL_CUM"),
    REVERSE_COWGIRL_CUM("reverse_cowgirl_cum", Play.ONCE, 48, "NULL", Partner.ATTACHED),

    // ---- Luna ----
    SITTING_INTRO("sitting_intro", Play.ONCE, 118, "SITTING_SLOW", Partner.ATTACHED),
    SITTING_SLOW("sitting_slow", Pace.SLOW, 40.0F, "SITTING_FAST", "SITTING_CUM"),
    SITTING_FAST("sitting_fast", Pace.FAST, 29.17F, "SITTING_SLOW", "SITTING_CUM"),
    SITTING_CUM("sitting_cum", Play.ONCE, 104, "NULL", Partner.ATTACHED),
    TOUCH_INTRO("touch_boobs_intro", Play.ONCE, 215, "TOUCH_SLOW", Partner.ATTACHED),
    TOUCH_SLOW("touch_boobs_slow", Pace.SLOW, 85.0F, "TOUCH_FAST", "TOUCH_CUM"),
    TOUCH_FAST("touch_boobs_fast", Pace.FAST, 28.33F, "TOUCH_SLOW", "TOUCH_CUM"),
    TOUCH_CUM("touch_boobs_cum", Play.ONCE, 106, "NULL", Partner.ATTACHED);

    /**
     * Scenes on a bed: she WALKs to a spot next to it, CLIMBs on, WAITs for the partner to step onto her
     * spot, and the rest is played ON the bed with him standing there.
     */
    public enum Bed { NONE, WALK, CLIMB, WAIT, ON }

    static {
        bed(Bed.WALK, DOGGY_TO_BED, SLIME_DOGGY_TO_BED);
        bed(Bed.CLIMB, DOGGY_ON_BED, SLIME_DOGGY_ON_BED);
        bed(Bed.WAIT, DOGGY_WAIT, SLIME_DOGGY_WAIT);
        bed(Bed.ON, DOGGY_START, DOGGY_SLOW, DOGGY_FAST, DOGGY_CUM,
                SLIME_DOGGY_START, SLIME_DOGGY_SLOW, SLIME_DOGGY_FAST, SLIME_DOGGY_CUM);
    }

    private static void bed(Bed role, GirlAction... actions) {
        for (GirlAction a : actions) a.bed = role;
    }

    private Bed bed = Bed.NONE;

    public Bed bed() {
        return bed;
    }


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
