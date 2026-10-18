package org.velvet.entity.girl.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlLoot;
import org.velvet.entity.girl.loot.ItemCaste;
import org.velvet.entity.girl.loot.ItemCastes;
import org.velvet.entity.girl.loot.LootContext;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Goes to items she wants that she can see lying around: the highest caste first, then the closest. Only
 * items she'd actually take (room in her inventory, better gear, or worth more than her cheapest stack);
 * following her owner she only steps aside for things close to him. Items she can't reach are left alone for
 * a while.
 */
public class GirlCollectItemsGoal extends Goal {

    private static final double RANGE = 12;
    /** Following: only items this close to the owner. */
    private static final double OWNER_RANGE = 8;
    private static final int SCAN_INTERVAL = 10;
    private static final int GIVE_UP_AFTER = 160;
    private static final int UNREACHABLE_FOR = 600;

    private final AbstractGirlEntity girl;
    @Nullable private ItemEntity target;
    private int scanTimer;
    private int ticks;
    private final Map<Integer, Long> unreachable = new HashMap<>();

    public GirlCollectItemsGoal(AbstractGirlEntity girl) {
        this.girl = girl;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!girl.behavior().pickUpItems || girl.getCurrentAction().isBusy() || girl.shouldBeAtTarget()
                || girl.isInDanger()) return false;
        if (--scanTimer > 0) return false;
        scanTimer = SCAN_INTERVAL;
        target = findTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && !girl.isInDanger() && ticks < GIVE_UP_AFTER
                && girl.behavior().pickUpItems && !girl.getCurrentAction().isBusy();
    }

    @Override
    public void start() {
        ticks = 0;
        moveToTarget();
    }

    @Override
    public void stop() {
        if (target != null && target.isAlive() && ticks >= GIVE_UP_AFTER) {
            unreachable.put(target.getId(), girl.level().getGameTime());
        }
        target = null;
        girl.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (target == null) return;
        if (ticks % 20 == 0 && !GirlLoot.wants(girl, target.getItem(), LootContext.of(girl))) {
            target = null;
            return;
        }
        if (girl.getNavigation().isDone()) {
            if (girl.distanceToSqr(target) > 2.5 && !moveToTarget()) {
                unreachable.put(target.getId(), girl.level().getGameTime());
                target = null;
            }
        }
    }

    private boolean moveToTarget() {
        if (target == null) return false;
        ItemCaste caste = ItemCastes.caste(LootContext.of(girl), target.getItem());
        double speed = caste.above(ItemCaste.VALUABLE) ? 1.2 : 1.0;
        return girl.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), speed);
    }

    @Nullable
    private ItemEntity findTarget() {
        long now = girl.level().getGameTime();
        unreachable.values().removeIf(t -> now - t > UNREACHABLE_FOR);
        Player owner = girl.isFollowing() && girl.getOwnerUUID() != null ? girl.level().getPlayerByUUID(girl.getOwnerUUID()) : null;
        LootContext context = LootContext.of(girl);
        ItemEntity best = null;
        ItemCaste bestCaste = null;
        double bestDist = 0;
        for (ItemEntity item : girl.level().getEntitiesOfClass(ItemEntity.class, girl.getBoundingBox().inflate(RANGE, 4, RANGE))) {
            if (!item.isAlive() || item.getItem().isEmpty() || unreachable.containsKey(item.getId())) continue;
            // just thrown (by a player, or what she threw out herself): not yet
            if (item.hasPickUpDelay()) continue;
            if (owner != null && item.distanceToSqr(owner) > OWNER_RANGE * OWNER_RANGE) continue;
            if (!girl.hasLineOfSight(item) || !GirlLoot.wants(girl, item.getItem(), context)) continue;
            ItemCaste caste = ItemCastes.caste(context, item.getItem());
            double dist = girl.distanceToSqr(item);
            if (best == null || caste.above(bestCaste) || caste == bestCaste && dist < bestDist) {
                best = item;
                bestCaste = caste;
                bestDist = dist;
            }
        }
        return best;
    }
}
