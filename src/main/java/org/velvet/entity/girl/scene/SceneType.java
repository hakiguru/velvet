package org.velvet.entity.girl.scene;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.velvet.entity.girl.GirlAction;
import org.velvet.entity.girl.GirlType;

import java.util.Arrays;
import java.util.List;

/** Paid scenes offered in the girl's menu, each for one preset. Creative players skip the payment. */
public enum SceneType {
    BLOWJOB(GirlType.ELSIE, Items.EMERALD, 3, GirlAction.BJ_INTRO, true),
    BOOBJOB(GirlType.ELSIE, Items.ENDER_PEARL, 2, GirlAction.PZ_START, true),
    DOGGY(GirlType.ELSIE, Items.DIAMOND, 2, GirlAction.DOGGY_TO_BED, true),

    SLIME_BLOWJOB(GirlType.SLIME, Items.EMERALD, 3, GirlAction.SLIME_BJ_SUCK, false),
    SLIME_DOGGY(GirlType.SLIME, Items.DIAMOND, 2, GirlAction.SLIME_DOGGY_TO_BED, true),

    COWGIRL(GirlType.ELLIE, Items.DIAMOND, 2, GirlAction.COWGIRL_START, true),
    MISSIONARY(GirlType.ELLIE, Items.EMERALD, 4, GirlAction.MISSIONARY_START, true),

    ANAL(GirlType.BIA, Items.DIAMOND, 3, GirlAction.ANAL_PREPARE, true),
    PRONE_DOGGY(GirlType.BIA, Items.GOLD_INGOT, 6, GirlAction.PRONE_INTRO, true),

    DEEPTHROAT(GirlType.ALLIE, Items.GOLD_BLOCK, 1, GirlAction.DEEPTHROAT_PREPARE, false),
    REVERSE_COWGIRL(GirlType.ALLIE, Items.DIAMOND, 2, GirlAction.REVERSE_COWGIRL_START, false),

    SITTING(GirlType.LUNA, Items.COOKED_COD, 4, GirlAction.SITTING_INTRO, false),
    TOUCH(GirlType.LUNA, Items.COOKED_SALMON, 4, GirlAction.TOUCH_INTRO, false);

    public final GirlType preset;
    private final Item costItem;
    private final int costCount;
    public final GirlAction firstStage;
    public final boolean needsBed;
    /** Undresses first (with the strip animation) if she's dressed and her preset can. */
    public final boolean undresses;

    SceneType(GirlType preset, Item costItem, int costCount, GirlAction firstStage, boolean undresses) {
        this.preset = preset;
        this.undresses = undresses && preset.strips();
        this.costItem = costItem;
        this.costCount = costCount;
        this.firstStage = firstStage;
        this.needsBed = firstStage.bed() == GirlAction.Bed.WALK;
    }

    public ItemStack cost() {
        return new ItemStack(costItem, costCount);
    }

    public String translationKey() {
        return "scene.velvet." + name().toLowerCase();
    }

    /** The scenes of one preset, in menu order. */
    public static List<SceneType> of(GirlType preset) {
        return Arrays.stream(values()).filter(t -> t.preset == preset).toList();
    }
}
