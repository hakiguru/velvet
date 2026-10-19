package org.velvet.entity.girl.scene;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.GirlAction;
import org.velvet.entity.girl.GirlType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paid scenes offered in the girl's menu: one list for every NPC, whatever preset she wears (all models have
 * the same skeleton, so any of them plays any scene). Each scene comes from one preset's animations (its
 * "style"), and says where it is played:
 * <ul>
 *   <li>FLOOR: right where they stand, the partner in front of her.</li>
 *   <li>BED: she walks to a free spot next to a bed (any side, or the end of its foot for scenes where he lies
 *   along it), climbs on or sits down on the edge, and waits there until the partner steps onto her spot;
 *   then she moves {@link Place#forward} towards the bed and the attached part starts. Or she lies down on
 *   the bed right away ({@link Place#liesDown}) and waits for him there.</li>
 * </ul>
 * A few scenes are played another way on presets they weren't made for ({@link #flow(GirlType)}): Allie's
 * deepthroat on a bed, Bia's anal as a doggy on a bed.
 * Creative players skip the payment.
 */
public enum SceneType {
    //                style            cost                          first stage                  undress  place
    BLOWJOB          (GirlType.JENNY,  Items.EMERALD, 3,       GirlAction.BJ_INTRO,          true,  Place.floor(0.94)),
    BOOBJOB          (GirlType.JENNY,  Items.ENDER_PEARL, 2,   GirlAction.PZ_START,          true,  Place.floor(0.94)),
    DOGGY            (GirlType.JENNY,  Items.DIAMOND, 2,       GirlAction.DOGGY_START,       true,
            Place.bed(Spot.SIDE, GirlAction.DOGGY_ON_BED, GirlAction.DOGGY_WAIT, 0, 0, 0)),

    SLIME_BLOWJOB    (GirlType.SLIME,  Items.EMERALD, 3,       GirlAction.SLIME_BJ_SUCK,     false, Place.floor(0.94)),
    SLIME_DOGGY      (GirlType.SLIME,  Items.DIAMOND, 2,       GirlAction.SLIME_DOGGY_START, true,
            Place.bed(Spot.SIDE, GirlAction.SLIME_DOGGY_ON_BED, GirlAction.SLIME_DOGGY_WAIT, 0, 0, 0)),

    COWGIRL          (GirlType.ELLIE,  Items.DIAMOND, 2,       GirlAction.COWGIRL_START,     true,
            Place.bed(Spot.FOOT, GirlAction.EDGE_SIT, GirlAction.EDGE_WAIT, 0.5, 0, 1.2)),
    MISSIONARY       (GirlType.ELLIE,  Items.EMERALD, 4,       GirlAction.MISSIONARY_START,  true,
            Place.bed(Spot.SIDE, GirlAction.EDGE_SIT, GirlAction.EDGE_WAIT, 0.5, 0, -0.2)),

    ANAL             (GirlType.BIA,    Items.DIAMOND, 3,       GirlAction.ANAL_PREPARE,      true,  Place.floor(0.3),
            Set.of(GirlType.BIA, GirlType.ALLIE),
            new Flow(GirlAction.ANAL_BED_START, Place.bed(Spot.SIDE, GirlAction.ANAL_BED_CLIMB, GirlAction.ANAL_BED_WAIT, 0, 0, 0))),
    PRONE_DOGGY      (GirlType.BIA,    Items.GOLD_INGOT, 6,    GirlAction.PRONE_INTRO,       true,
            Place.bed(Spot.SIDE, GirlAction.EDGE_SIT, GirlAction.EDGE_WAIT, 0.5, 0, -0.3)),

    DEEPTHROAT       (GirlType.ALLIE,  Items.GOLD_BLOCK, 1,    GirlAction.DEEPTHROAT_START,  false, Place.floor(0.94),
            Set.of(GirlType.ALLIE), new Flow(GirlAction.DT_BED_START, Place.lieDown(Spot.FOOT, GirlAction.DT_BED_WAIT, 1.0, 0.94))),
    REVERSE_COWGIRL  (GirlType.ALLIE,  Items.DIAMOND, 2,       GirlAction.REVERSE_COWGIRL_START, false, Place.floor(0.8)),

    SITTING          (GirlType.LUNA,   Items.COOKED_COD, 4,    GirlAction.SITTING_INTRO,     false,
            Place.bed(Spot.SIDE, null, GirlAction.STAND_WAIT, 0, 0, 0.53)),
    TOUCH            (GirlType.LUNA,   Items.COOKED_SALMON, 4, GirlAction.TOUCH_INTRO,       false, Place.floor(0.94)),

    GALATH_BED       (GirlType.GALATH, Items.GOLD_INGOT, 4,    GirlAction.GALATH_BED_START,  true,
            Place.alongBed(Spot.LEFT, GirlAction.EDGE_SIT, GirlAction.EDGE_WAIT, 0.8, 0.3)),
    SOLO_SHOW        (GirlType.GALATH, Items.AMETHYST_SHARD, 4, GirlAction.SOLO_SHOW,        true,  Place.floor(1.5));

    /** Where the scene is played; see the class comment. */
    public enum Spot { SIDE, FOOT, LEFT }

    /**
     * @param bed           played at a bed
     * @param spot          which spots next to the bed will do
     * @param arrive        played once she's at the spot (climbing on, sitting down), or null
     * @param waiting       played while she waits for the partner to step onto her spot
     * @param forward       how far she moves towards the bed when the attached part starts (blocks)
     * @param up            and how far up
     * @param partnerAhead  where the partner is during the attached part, along the way she faces (blocks;
     *                      negative behind her); used before the model has placed him, and to put him down after
     * @param along         NaN, or: for the attached part she's put on the middle line of the bed this far from
     *                      the centre of its foot half towards the head, facing the head (the scenes where the
     *                      model has her beside him while he lies in the bed)
     */
    public record Place(boolean bed, Spot spot, @Nullable GirlAction arrive, @Nullable GirlAction waiting,
                        double forward, double up, double partnerAhead, double along, boolean liesDown) {
        static Place floor(double partnerAhead) {
            return new Place(false, Spot.SIDE, null, null, 0, 0, partnerAhead, Double.NaN, false);
        }

        static Place bed(Spot spot, @Nullable GirlAction arrive, GirlAction waiting, double forward, double up, double partnerAhead) {
            return new Place(true, spot, arrive, waiting, forward, up, partnerAhead, Double.NaN, false);
        }

        static Place alongBed(Spot spot, @Nullable GirlAction arrive, GirlAction waiting, double along, double partnerAhead) {
            return new Place(true, spot, arrive, waiting, 0, 0, partnerAhead, along, false);
        }

        /**
         * She lies down on the bed as soon as she's at the spot: put {@code forward} blocks into it, facing back
         * out at the spot, where he then steps up to her.
         */
        static Place lieDown(Spot spot, GirlAction waiting, double forward, double partnerAhead) {
            return new Place(true, spot, null, waiting, forward, 0, partnerAhead, Double.NaN, true);
        }

        public boolean alongBed() {
            return !Double.isNaN(along);
        }
    }

    /**
     * How a scene is played: its first stage with the partner attached (after the payment, undressing and the
     * way to the bed) and where.
     */
    public record Flow(GirlAction firstStage, Place place) {}

    /** The preset whose animations the scene comes from: its style, shown in the menu. */
    public final GirlType style;
    private final Item costItem;
    private final int costCount;
    private final Flow flow;
    /** The presets the scene is played the usual way on; the others play {@link #away}. */
    private final Set<GirlType> natives;
    @Nullable private final Flow away;
    /** Undresses first (with the strip animation) if she's dressed and her preset can. */
    private final boolean undresses;

    private static final Map<GirlAction, SceneType> BY_STAGE = new EnumMap<>(GirlAction.class);
    private static final Map<GirlAction, Flow> FLOW_BY_STAGE = new EnumMap<>(GirlAction.class);

    static {
        for (SceneType type : values()) {
            for (Flow f : type.away == null ? List.of(type.flow) : List.of(type.flow, type.away)) {
                if (f.place().bed()) GirlAction.markOnBed(f.firstStage());
                collect(type, f, f.firstStage());
                if (f.place().waiting() != null) collect(type, f, f.place().waiting());
                if (f.place().arrive() != null) collect(type, f, f.place().arrive());
            }
        }
    }

    private static void collect(SceneType type, Flow flow, @Nullable GirlAction a) {
        if (a == null || a == GirlAction.NULL || BY_STAGE.containsKey(a)) return;
        // the bed stages several scenes share belong to none
        if (a == GirlAction.EDGE_SIT || a == GirlAction.EDGE_WAIT || a == GirlAction.STAND_WAIT) return;
        BY_STAGE.put(a, type);
        FLOW_BY_STAGE.put(a, flow);
        collect(type, flow, a.next());
        collect(type, flow, a.finish());
    }

    SceneType(GirlType style, Item costItem, int costCount, GirlAction firstStage, boolean undresses, Place place) {
        this(style, costItem, costCount, firstStage, undresses, place, Set.of(), null);
    }

    SceneType(GirlType style, Item costItem, int costCount, GirlAction firstStage, boolean undresses, Place place,
              Set<GirlType> natives, @Nullable Flow away) {
        this.style = style;
        this.costItem = costItem;
        this.costCount = costCount;
        this.flow = new Flow(firstStage, place);
        this.undresses = undresses;
        this.natives = natives;
        this.away = away;
    }

    /** How it's played on an NPC wearing {@code preset}. */
    public Flow flow(GirlType preset) {
        return away != null && !natives.contains(preset.base()) ? away : flow;
    }

    public boolean needsBed(GirlType preset) {
        return flow(preset).place().bed();
    }

    /** Whether she takes her clothes off first: only presets that have a dressed model and a strip animation. */
    public boolean undresses(GirlType preset) {
        return undresses && preset.strips();
    }

    public ItemStack cost() {
        return new ItemStack(costItem, costCount);
    }

    public String translationKey() {
        return "scene.velvet." + name().toLowerCase();
    }

    /**
     * The scenes an NPC wearing {@code preset} can play, in menu order: all of them, for every preset, except
     * the slime's own versions of scenes everyone has (only a slime plays those).
     */
    public static List<SceneType> of(GirlType preset) {
        boolean slime = preset.base() == GirlType.SLIME;
        return Arrays.stream(values()).filter(t -> slime || t.style != GirlType.SLIME).toList();
    }

    /** The scene a stage belongs to (the shared stages of the bed scenes belong to none). */
    @Nullable
    public static SceneType ofStage(GirlAction stage) {
        return BY_STAGE.get(stage);
    }

    /** And the way of playing it the stage is part of. */
    @Nullable
    public static Flow flowOfStage(GirlAction stage) {
        return FLOW_BY_STAGE.get(stage);
    }
}
