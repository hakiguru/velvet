package org.velvetlite.entity.girl.loot;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.velvetlite.entity.girl.GirlNeeds;

/**
 * A rule that moves an item to another caste depending on the situation, see {@link ItemCastes#registerPriority}.
 * For example (not built in yet): in a cave, wood and sticks could count as valuable, since she can't get more.
 */
@FunctionalInterface
public interface LootPriority {

    ItemCaste adjust(LootContext context, ItemStack stack, ItemCaste caste);

    /** Food she can eat safely: common normally, valuable when she's hungry, precious when she's starving. */
    LootPriority HUNGER = (context, stack, caste) -> {
        FoodProperties food = stack.getFoodProperties(context.girl());
        if (food == null || food.nutrition() <= 0 || GirlNeeds.isHarmful(stack, food)) return caste;
        return switch (context.mood()) {
            case STARVING -> caste.atLeast(ItemCaste.PRECIOUS);
            case HUNGRY -> caste.atLeast(ItemCaste.VALUABLE);
            default -> caste.atLeast(ItemCaste.COMMON);
        };
    };

    /** In danger, weapons, armor, shields, totems, pearls and golden apples are precious. */
    LootPriority DANGER = (context, stack, caste) ->
            context.mood() == LootContext.Mood.IN_DANGER && ItemCastes.isGear(stack) ? caste.atLeast(ItemCaste.PRECIOUS) : caste;

    /** Her own list from behavior.json ("loot" → "castes"): item ids or #tags with the caste they belong to. */
    LootPriority BEHAVIOR_OVERRIDES = (context, stack, caste) -> {
        ItemCaste forced = context.girl().behavior().casteOverride(stack);
        return forced != null ? forced : caste;
    };
}
