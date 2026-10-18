package org.velvetlite.entity.girl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.VelvetLite;

import java.util.ArrayList;
import java.util.List;

/**
 * Hunger and breath, server side. Works like the player's food bar: everything she does tires her a little
 * (exhaustion), which eats up saturation first and then food points; with no food left she starves. She
 * eats by herself from her inventory and hands. Running also uses up stamina: once it's gone she's tired,
 * can't run and walks slower until she has caught her breath.
 * <p>
 * Endurance stretches all of it: less exhaustion per action and more stamina.
 */
public final class GirlNeeds {

    public static final int MAX_FOOD = 20;
    /** Food points at or above which she heals by herself. */
    public static final int REGEN_FOOD = 12;
    private static final float MAX_SATURATION = 20;
    private static final float EXHAUSTION_PER_POINT = 4;
    /** Being alive: about one food point a minute at endurance 0, once saturation is used up. */
    private static final float EXHAUSTION_PER_TICK = 0.0035F;
    private static final float EXHAUSTION_PER_BLOCK_WALKED = 0.01F;
    private static final float EXHAUSTION_PER_BLOCK_RUN = 0.1F;
    public static final float EXHAUSTION_PER_HEAL = 3;
    /** Each endurance level takes this share off exhaustion (level 10: half). */
    private static final float EXHAUSTION_SAVED_PER_ENDURANCE = 0.05F;
    private static final int STARVE_INTERVAL = 80;

    /** Faster than this (blocks per tick) counts as running; following at a walk stays below it. */
    private static final double RUN_SPEED = 0.12;
    private static final int BASE_STAMINA = 200;
    private static final int STAMINA_PER_ENDURANCE = 60;
    /** Tired until this share of stamina is back. */
    private static final float RECOVERED = 0.5F;
    private static final int RUN_TICKS_PER_ENDURANCE_XP = 200;
    private static final ResourceLocation TIRED = ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "tired");
    private static final double TIRED_SPEED = -0.25;

    /** Ticks between bites: eating takes her about as long as the player. */
    private static final int EAT_COOLDOWN = 40;
    /** Hungrier than this, she also eats food that makes her sick. */
    private static final int DESPERATE_FOOD = 4;

    private final AbstractGirlEntity girl;
    private float saturation = 5;
    private float exhaustion;
    private int starveTimer;
    private int stamina = -1;
    private boolean tired;
    private int runTicks;
    private int eatCooldown;

    GirlNeeds(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public void tick(double moved) {
        float drain = exhaustionFactor();
        boolean peaceful = girl.level().getDifficulty() == Difficulty.PEACEFUL;
        boolean running = moved > RUN_SPEED && girl.onGround();

        // ---- stamina ----
        int max = maxStamina();
        if (stamina < 0) stamina = max;
        if (running) {
            stamina = Math.max(0, stamina - 1);
            if (stamina == 0) setTired(true);
            if (++runTicks >= RUN_TICKS_PER_ENDURANCE_XP) {
                runTicks = 0;
                girl.addSkillXp(GirlSkill.ENDURANCE, 1);
            }
        } else {
            stamina = Math.min(max, stamina + (moved < 0.01 ? 2 : 1));
            if (tired && stamina >= max * RECOVERED) setTired(false);
        }

        // ---- food ----
        if (peaceful) {
            // like the player on peaceful: never hungry
            if (girl.getFood() < MAX_FOOD && girl.tickCount % 20 == 0) girl.setFood(girl.getFood() + 1);
            return;
        }
        exhaustion += drain * (EXHAUSTION_PER_TICK
                + (float) moved * (running ? EXHAUSTION_PER_BLOCK_RUN : EXHAUSTION_PER_BLOCK_WALKED));
        while (exhaustion >= EXHAUSTION_PER_POINT) {
            exhaustion -= EXHAUSTION_PER_POINT;
            if (saturation > 0) saturation = Math.max(0, saturation - 1);
            else girl.setFood(Math.max(0, girl.getFood() - 1));
        }
        if (girl.getFood() == 0) {
            if (++starveTimer >= STARVE_INTERVAL) {
                starveTimer = 0;
                girl.hurt(girl.damageSources().starve(), 1.0F);
            }
        } else {
            starveTimer = 0;
        }

        if (eatCooldown > 0) eatCooldown--;
        else if (girl.behavior().autoEat && !girl.getCurrentAction().isBusy()) tryEat();
    }

    public void addExhaustion(float amount) {
        exhaustion += amount * exhaustionFactor();
    }

    private float exhaustionFactor() {
        return 1 - EXHAUSTION_SAVED_PER_ENDURANCE * girl.getSkillLevel(GirlSkill.ENDURANCE);
    }

    public int maxStamina() {
        return BASE_STAMINA + STAMINA_PER_ENDURANCE * girl.getSkillLevel(GirlSkill.ENDURANCE);
    }

    /** Out of breath: no running until she has rested. */
    public boolean isTired() {
        return tired;
    }

    public boolean canRun() {
        return !tired;
    }

    private void setTired(boolean value) {
        tired = value;
        var speed = girl.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (value) speed.addOrUpdateTransientModifier(new AttributeModifier(TIRED, TIRED_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(TIRED);
    }

    // ---- Eating ----

    /** Eats one bite of the best food she has, if she's hungry enough for it not to go to waste. */
    private void tryEat() {
        int food = girl.getFood();
        if (food >= MAX_FOOD) return;
        boolean hurt = girl.getHealth() < girl.getMaxHealth();
        SimpleContainer bag = girl.getBag();
        ItemStack best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : candidates(bag)) {
            FoodProperties props = stack.getFoodProperties(girl);
            if (props == null || props.nutrition() <= 0) continue;
            boolean harmful = isHarmful(stack, props);
            if (harmful && food > DESPERATE_FOOD) continue;
            int missing = MAX_FOOD - food;
            // waits until a whole portion fits, unless she's hurt and needs food to heal
            if (props.nutrition() > missing && !hurt && food >= REGEN_FOOD) continue;
            // the best fit: no waste, then the most filling; sickening food last
            int score = -Math.abs(missing - props.nutrition()) * 2 + Math.round(props.saturation()) - (harmful ? 100 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        if (best != null) eat(best);
    }

    private Iterable<ItemStack> candidates(SimpleContainer bag) {
        List<ItemStack> all = new ArrayList<>(bag.getItems());
        all.add(girl.getItemBySlot(EquipmentSlot.MAINHAND));
        all.add(girl.getItemBySlot(EquipmentSlot.OFFHAND));
        all.removeIf(ItemStack::isEmpty);
        return all;
    }

    /** Eats it right away, hungry or not (a golden apple in a fight). */
    public void eatNow(ItemStack stack) {
        eat(stack);
    }

    private void eat(ItemStack stack) {
        FoodProperties props = stack.getFoodProperties(girl);
        if (props == null) return;
        @Nullable ItemStack leftover = props.usingConvertsTo().map(ItemStack::copy).orElse(null);
        girl.setFood(Math.min(MAX_FOOD, girl.getFood() + props.nutrition()));
        saturation = Math.min(Math.min(MAX_SATURATION, girl.getFood()), saturation + props.saturation());
        girl.eat(girl.level(), stack, props); // sound, effects, one item used up
        girl.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (leftover != null && !leftover.isEmpty()) {
            // the bowl or bottle goes back into her inventory, or onto the ground
            ItemStack rest = GirlLoot.addToBag(girl, leftover);
            if (!rest.isEmpty()) girl.spawnAtLocation(rest);
        }
        eatCooldown = EAT_COOLDOWN;
    }

    public static boolean isHarmful(ItemStack stack, FoodProperties props) {
        if (stack.is(Items.CHORUS_FRUIT)) return true; // teleports her away
        for (FoodProperties.PossibleEffect effect : props.effects()) {
            if (effect.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
        }
        return false;
    }

    // ---- Saving ----

    public void save(CompoundTag tag) {
        tag.putInt("food", girl.getFood());
        tag.putFloat("saturation", saturation);
        tag.putFloat("exhaustion", exhaustion);
        tag.putInt("stamina", stamina);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("food")) girl.setFood(Mth.clamp(tag.getInt("food"), 0, MAX_FOOD));
        if (tag.contains("saturation")) saturation = tag.getFloat("saturation");
        exhaustion = tag.getFloat("exhaustion");
        if (tag.contains("stamina")) stamina = tag.getInt("stamina");
    }
}
