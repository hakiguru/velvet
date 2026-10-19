package org.velvet.entity.girl;

import org.jetbrains.annotations.Nullable;

/**
 * What a girl is doing right now; synced to clients and drives the "action" animation controller.
 * <p>
 * Each state plays {@code animation.<source>.<animation>}: scenes and poses are shared by every preset, so a
 * stage names the preset whose animation file it comes from (null: her own, like strip or wave). ONCE stages
 * hand over to {@link #next()} after {@link #length} ticks; where a stage is shared by several scenes (walking
 * to the bed, sitting down on its edge, waiting there) the scene controller picks what follows. The main part
 * of a scene is a pair of loops, a slow and a fast one: the partner holds the speed key for the fast one.
 * Every finished cycle fills the progress bar a bit (the fast loop several times quicker, see
 * GirlSceneController), and the scene ends once the bar is full.
 */
public enum GirlAction {

    NULL(null, null, Play.LOOP, 0, null, Partner.NONE),

    // ---- Solo ----
    STRIP(null, "strip", Play.ONCE, 101, "NULL", Partner.NONE),
    WAVE(null, "wave", Play.ONCE, 71, "NULL", Partner.NONE),

    // ---- Paid scenes; PAYMENT runs first in survival and then hands over to the scene's first stage ----
    PAYMENT(null, "payment", Play.ONCE, 186, null, Partner.ATTACHED),
    /** Undressing before a scene that needs it; the controller then starts the scene itself. */
    SCENE_STRIP(null, "strip", Play.ONCE, 101, null, Partner.ATTACHED),

    // ---- Shared by the bed scenes: walking there, sitting down on the edge and waiting for the partner ----
    /** Walking to the bed; not an animation stage, the girl uses her movement animations meanwhile. */
    BED_WALK(null, null, Play.LOOP, 0, null, Partner.NEARBY),
    /** Sits down on the edge of the bed, her back to it. */
    EDGE_SIT("ellie", "sitdown", Play.ONCE, 110, null, Partner.NEARBY),
    EDGE_WAIT("ellie", "sitdownidle", Play.LOOP, 0, null, Partner.NEARBY),
    /** Waits standing next to the bed (the partner sits down on its edge). */
    STAND_WAIT(null, "idle", Play.LOOP, 0, null, Partner.NEARBY),

    // ---- Jenny ----
    BJ_INTRO("elsie", "blowjobintro", Play.ONCE, 986, "BJ_SUCK", Partner.ATTACHED),
    BJ_SUCK("elsie", "blowjobsuck", Pace.SLOW, 120.8F, "BJ_THRUST", "BJ_CUM"),
    BJ_THRUST("elsie", "blowjobthrust", Pace.FAST, 35.2F, "BJ_SUCK", "BJ_CUM"),
    BJ_CUM("elsie", "blowjobcum", Play.ONCE, 236, "NULL", Partner.ATTACHED),

    PZ_START("elsie", "paizuri_start", Play.ONCE, 173, "PZ_SLOW", Partner.ATTACHED),
    PZ_SLOW("elsie", "paizuri_slow", Pace.SLOW, 67.5F, "PZ_FAST", "PZ_CUM"),
    PZ_FAST("elsie", "paizuri_fast", Pace.FAST, 22.0F, "PZ_SLOW", "PZ_CUM"),
    PZ_CUM("elsie", "paizuri_cum", Play.ONCE, 230, "NULL", Partner.ATTACHED),

    // Doggy: she climbs onto the bed from her spot and waits there until the partner steps onto it
    DOGGY_ON_BED("elsie", "doggygoonbed", Play.ONCE, 130, "DOGGY_WAIT", Partner.NEARBY),
    DOGGY_WAIT("elsie", "doggywait", Play.LOOP, 0, null, Partner.NEARBY),
    DOGGY_START("elsie", "doggystart", Play.ONCE, 198, "DOGGY_SLOW", Partner.ATTACHED),
    DOGGY_SLOW("elsie", "doggyslow", Pace.SLOW, 46.4F, "DOGGY_FAST", "DOGGY_CUM"),
    DOGGY_FAST("elsie", "doggyfast_soft", Pace.FAST, 21.6F, "DOGGY_SLOW", "DOGGY_CUM"),
    DOGGY_CUM("elsie", "doggycum", Play.ONCE, 134, "NULL", Partner.ATTACHED),

    // ---- Slime ----
    SLIME_BJ_SUCK("slime", "blowjobsuck", Pace.SLOW, 120.0F, "SLIME_BJ_THRUST", "SLIME_BJ_CUM"),
    SLIME_BJ_THRUST("slime", "blowjobthrust", Pace.FAST, 42.4F, "SLIME_BJ_SUCK", "SLIME_BJ_CUM"),
    SLIME_BJ_CUM("slime", "blowjobcum", Play.ONCE, 231, "NULL", Partner.ATTACHED),
    SLIME_DOGGY_ON_BED("slime", "doggygoonbed", Play.ONCE, 34, "SLIME_DOGGY_WAIT", Partner.NEARBY),
    SLIME_DOGGY_WAIT("slime", "doggywait", Play.LOOP, 0, null, Partner.NEARBY),
    SLIME_DOGGY_START("slime", "doggystart", Play.ONCE, 196, "SLIME_DOGGY_SLOW", Partner.ATTACHED),
    SLIME_DOGGY_SLOW("slime", "doggyslow", Pace.SLOW, 45.6F, "SLIME_DOGGY_FAST", "SLIME_DOGGY_CUM"),
    SLIME_DOGGY_FAST("slime", "doggyfast", Pace.FAST, 21.6F, "SLIME_DOGGY_SLOW", "SLIME_DOGGY_CUM"),
    SLIME_DOGGY_CUM("slime", "doggycum", Play.ONCE, 112, "NULL", Partner.ATTACHED),

    // ---- Ellie ----
    COWGIRL_START("ellie", "cowgirlstart", Play.ONCE, 315, "COWGIRL_SLOW", Partner.ATTACHED),
    COWGIRL_SLOW("ellie", "cowgirlslow2", Pace.SLOW, 64.0F, "COWGIRL_FAST", "COWGIRL_CUM"),
    COWGIRL_FAST("ellie", "cowgirlfast", Pace.FAST, 68.0F, "COWGIRL_SLOW", "COWGIRL_CUM"),
    COWGIRL_CUM("ellie", "cowgirlcum", Play.ONCE, 307, "NULL", Partner.ATTACHED),
    MISSIONARY_START("ellie", "missionary_start", Play.ONCE, 65, "MISSIONARY_SLOW", Partner.ATTACHED),
    MISSIONARY_SLOW("ellie", "missionary_slow", Pace.SLOW, 58.33F, "MISSIONARY_FAST", "MISSIONARY_CUM"),
    MISSIONARY_FAST("ellie", "missionary_fast", Pace.FAST, 37.5F, "MISSIONARY_SLOW", "MISSIONARY_CUM"),
    MISSIONARY_CUM("ellie", "missionary_cum", Play.ONCE, 289, "NULL", Partner.ATTACHED),

    // ---- Bia ----
    ANAL_PREPARE("bia", "anal_prepare", Play.ONCE, 110, "ANAL_START", Partner.ATTACHED),
    ANAL_START("bia", "anal_start", Play.ONCE, 57, "ANAL_SLOW", Partner.ATTACHED),
    ANAL_SLOW("bia", "anal_slow", Pace.SLOW, 29.6F, "ANAL_FAST", "ANAL_CUM"),
    ANAL_FAST("bia", "anal_fast", Pace.FAST, 18.4F, "ANAL_SLOW", "ANAL_CUM"),
    ANAL_CUM("bia", "anal_cum", Play.ONCE, 75, "NULL", Partner.ATTACHED),
    PRONE_INTRO("bia", "prone_doggy_intro", Play.ONCE, 38, "PRONE_INSERT", Partner.ATTACHED),
    PRONE_INSERT("bia", "prone_doggy_insert", Play.ONCE, 43, "PRONE_SOFT", Partner.ATTACHED),
    PRONE_SOFT("bia", "prone_doggy_soft", Pace.SLOW, 29.17F, "PRONE_HARD", "PRONE_CUM"),
    PRONE_HARD("bia", "prone_doggy_hard1", Pace.FAST, 40.83F, "PRONE_SOFT", "PRONE_CUM"),
    PRONE_CUM("bia", "prone_doggy_cum", Play.ONCE, 58, "NULL", Partner.ATTACHED),

    // ---- Allie ----
    DEEPTHROAT_PREPARE("allie", "deepthroat_prepare", Play.ONCE, 69, "DEEPTHROAT_START", Partner.ATTACHED),
    DEEPTHROAT_START("allie", "deepthroat_start", Play.ONCE, 64, "DEEPTHROAT_SLOW", Partner.ATTACHED),
    DEEPTHROAT_SLOW("allie", "deepthroat_slow", Pace.SLOW, 59.2F, "DEEPTHROAT_FAST", "DEEPTHROAT_CUM"),
    DEEPTHROAT_FAST("allie", "deepthroat_fast", Pace.FAST, 24.0F, "DEEPTHROAT_SLOW", "DEEPTHROAT_CUM"),
    DEEPTHROAT_CUM("allie", "deepthroat_cum", Play.ONCE, 46, "NULL", Partner.ATTACHED),
    /**
     * Deepthroat on a bed, for every preset but Allie: she lies down on her back along it, her head at its foot,
     * and waits for him there; the rest is Allie's.
     */
    DT_BED_WAIT("allie", "deepthroat_start", Play.ONCE, 0, null, Partner.NEARBY),
    DT_BED_START("allie", "deepthroat_start", Play.ONCE, 64, "DT_BED_SLOW", Partner.ATTACHED),
    DT_BED_SLOW("allie", "deepthroat_slow", Pace.SLOW, 59.2F, "DT_BED_FAST", "DT_BED_CUM"),
    DT_BED_FAST("allie", "deepthroat_fast", Pace.FAST, 24.0F, "DT_BED_SLOW", "DT_BED_CUM"),
    DT_BED_CUM("allie", "deepthroat_cum", Play.ONCE, 46, "NULL", Partner.ATTACHED),
    /** Anal for every preset but Bia and Allie: like Jenny's doggy on a bed (she's drawn a bit lower, see SceneFits). */
    ANAL_BED_CLIMB("elsie", "doggygoonbed", Play.ONCE, 130, "ANAL_BED_WAIT", Partner.NEARBY),
    ANAL_BED_WAIT("elsie", "doggywait", Play.LOOP, 0, null, Partner.NEARBY),
    ANAL_BED_START("elsie", "doggystart", Play.ONCE, 198, "ANAL_BED_SLOW", Partner.ATTACHED),
    ANAL_BED_SLOW("elsie", "doggyslow", Pace.SLOW, 46.4F, "ANAL_BED_FAST", "ANAL_BED_CUM"),
    ANAL_BED_FAST("elsie", "doggyfast_soft", Pace.FAST, 21.6F, "ANAL_BED_SLOW", "ANAL_BED_CUM"),
    ANAL_BED_CUM("elsie", "doggycum", Play.ONCE, 134, "NULL", Partner.ATTACHED),
    REVERSE_COWGIRL_START("allie", "reverse_cowgirl_start", Play.ONCE, 88, "REVERSE_COWGIRL_SLOW", Partner.ATTACHED),
    REVERSE_COWGIRL_SLOW("allie", "reverse_cowgirl_slow1", Pace.SLOW, 47.5F, "REVERSE_COWGIRL_FAST", "REVERSE_COWGIRL_CUM"),
    REVERSE_COWGIRL_FAST("allie", "reverse_cowgirl_fastc1", Pace.FAST, 43.33F, "REVERSE_COWGIRL_SLOW", "REVERSE_COWGIRL_CUM"),
    REVERSE_COWGIRL_CUM("allie", "reverse_cowgirl_cum", Play.ONCE, 48, "NULL", Partner.ATTACHED),

    // ---- Luna ----
    SITTING_INTRO("cat", "sitting_intro", Play.ONCE, 118, "SITTING_SLOW", Partner.ATTACHED),
    SITTING_SLOW("cat", "sitting_slow", Pace.SLOW, 40.0F, "SITTING_FAST", "SITTING_CUM"),
    SITTING_FAST("cat", "sitting_fast", Pace.FAST, 29.17F, "SITTING_SLOW", "SITTING_CUM"),
    SITTING_CUM("cat", "sitting_cum", Play.ONCE, 104, "NULL", Partner.ATTACHED),
    TOUCH_INTRO("cat", "touch_boobs_intro", Play.ONCE, 215, "TOUCH_SLOW", Partner.ATTACHED),
    TOUCH_SLOW("cat", "touch_boobs_slow", Pace.SLOW, 85.0F, "TOUCH_FAST", "TOUCH_CUM"),
    TOUCH_FAST("cat", "touch_boobs_fast", Pace.FAST, 28.33F, "TOUCH_SLOW", "TOUCH_CUM"),
    TOUCH_CUM("cat", "touch_boobs_cum", Play.ONCE, 106, "NULL", Partner.ATTACHED),

    // ---- Galath ----
    GALATH_BED_START("galath", "bed_soft", Play.ONCE, 44, "GALATH_BED_SLOW", Partner.ATTACHED),
    GALATH_BED_SLOW("galath", "bed_slow", Pace.SLOW, 40.0F, "GALATH_BED_FAST", "GALATH_BED_CUM"),
    GALATH_BED_FAST("galath", "bed_fast", Pace.FAST, 11.67F, "GALATH_BED_SLOW", "GALATH_BED_CUM"),
    GALATH_BED_CUM("galath", "bed_cum", Play.ONCE, 78, "NULL", Partner.ATTACHED),
    /** A show for whoever paid: she does it herself, he only watches. */
    SOLO_SHOW("galath", "masterbate", Play.ONCE, 406, "NULL", Partner.NEARBY);

    /**
     * Scenes on a bed: she WALKs to a spot next to it, CLIMBs on or sits down on its edge, WAITs for the
     * partner to step onto her spot, and the rest is played ON the bed with him there.
     */
    public enum Bed { NONE, WALK, CLIMB, WAIT, ON }

    static {
        bed(Bed.WALK, BED_WALK);
        bed(Bed.CLIMB, DOGGY_ON_BED, SLIME_DOGGY_ON_BED, EDGE_SIT, ANAL_BED_CLIMB);
        bed(Bed.WAIT, DOGGY_WAIT, SLIME_DOGGY_WAIT, EDGE_WAIT, STAND_WAIT, DT_BED_WAIT, ANAL_BED_WAIT);
    }

    static void bed(Bed role, GirlAction... actions) {
        for (GirlAction a : actions) a.bed = role;
    }

    private Bed bed = Bed.NONE;

    public Bed bed() {
        return bed;
    }

    /** Marks the attached stages of a bed scene (SceneType does, for the scenes it puts on a bed). */
    public static void markOnBed(GirlAction first) {
        java.util.Set<GirlAction> seen = java.util.EnumSet.noneOf(GirlAction.class);
        java.util.ArrayDeque<GirlAction> todo = new java.util.ArrayDeque<>();
        todo.add(first);
        while (!todo.isEmpty()) {
            GirlAction a = todo.poll();
            if (a == NULL || !seen.add(a)) continue;
            if (a.isAttached()) a.bed = Bed.ON;
            if (a.next() != null) todo.add(a.next());
            if (a.finish() != null) todo.add(a.finish());
        }
    }

    public enum Play { ONCE, LOOP }

    /**
     * NEARBY: a player takes part but still walks around himself (drawn as usual).
     * ATTACHED: he's locked to the girl and drawn through the model's "steve" bone.
     */
    public enum Partner { NONE, NEARBY, ATTACHED }

    /** Loops of the main part of a scene; the progress bar only fills during these. */
    public enum Pace { NONE, SLOW, FAST }

    /** Prefix of the animation file the stage comes from; null for the girl's own preset. */
    @Nullable public final String source;
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

    GirlAction(@Nullable String source, @Nullable String animation, Play play, int length, @Nullable String next, Partner partner) {
        this(source, animation, play, length, next, partner, Pace.NONE, 0, null);
    }

    /** A paced loop; {@code other} is the loop of the opposite pace. */
    GirlAction(String source, String animation, Pace pace, float cycle, String other, String finish) {
        this(source, animation, Play.LOOP, 0, other, Partner.ATTACHED, pace, cycle, finish);
    }

    GirlAction(@Nullable String source, @Nullable String animation, Play play, int length, @Nullable String next,
               Partner partner, Pace pace, float cycle, @Nullable String finish) {
        this.source = source;
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
     * ONCE stages: the stage that follows, or null if the controller decides (PAYMENT, SCENE_STRIP, the shared
     * bed stages). Paced loops: the loop of the opposite pace.
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
            // saves from older versions used other action names
            return NULL;
        }
    }
}
