package org.velvetlite.entity.girl.scene;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.velvetlite.entity.girl.GirlAction;

/** Paid scenes offered in the girl's menu. Creative players skip the payment. */
public enum SceneType {
    BLOWJOB(Items.EMERALD, 3, GirlAction.BJ_INTRO, false, false),
    BOOBJOB(Items.ENDER_PEARL, 2, GirlAction.PZ_START, false, true),
    DOGGY(Items.DIAMOND, 2, GirlAction.DOGGY_TO_BED, true, true);

    private final Item costItem;
    private final int costCount;
    public final GirlAction firstStage;
    public final boolean needsBed;
    /** Undresses first (with the strip animation) if she's dressed. */
    public final boolean undresses;

    SceneType(Item costItem, int costCount, GirlAction firstStage, boolean needsBed, boolean undresses) {
        this.undresses = undresses;
        this.costItem = costItem;
        this.costCount = costCount;
        this.firstStage = firstStage;
        this.needsBed = needsBed;
    }

    public ItemStack cost() {
        return new ItemStack(costItem, costCount);
    }

    public String translationKey() {
        return "scene.velvet_lite." + name().toLowerCase();
    }
}
