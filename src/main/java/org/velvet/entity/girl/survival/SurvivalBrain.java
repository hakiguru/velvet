package org.velvet.entity.girl.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlLoot;
import org.velvet.entity.girl.brain.GirlChat;

import java.util.ArrayList;
import java.util.List;

/**
 * Her instinct to stay alive, server side. Every half second it looks at the threats she knows of
 * ({@link Threats}: what she sees or hears) and her own state and picks a {@link Tactic}, which the survival
 * goals carry out:
 * <ul>
 *   <li>STEALTH: a warden she sees or hears that isn't after her (or the darkness of a shrieker, or a sculk
 *       shrieker right by her): she sneaks (no vibrations) and keeps away from it.</li>
 *   <li>FIGHT: when she'd win: the time she needs to kill them all with what's in her hands against the time
 *       they need to kill her through her armor, shield and what she has to heal with. Never wardens or
 *       bosses; creepers only with a good weapon (hit and step back).</li>
 *   <li>FLEE: away along the best escape route; she keeps going while they're after her.</li>
 *   <li>SHELTER: cornered, with blocks: walls herself in, roof included, and waits until it's safe.</li>
 *   <li>PILLAR: cornered by monsters that can't climb or shoot, with blocks to spare: a pillar up.</li>
 *   <li>DIG_IN: weak, cornered or without anything to defend herself with: she digs down into soft ground,
 *       covers the hole and waits there; afterwards everything is put back as it was.</li>
 *   <li>EXTINGUISH: burning with nothing around: to the nearest water.</li>
 * </ul>
 * Meanwhile she uses what she has: armor, weapon and shield on as soon as something's after her, a totem
 * when she's nearly dead, golden apples and potions that help, an ender pearl as a last resort, and she
 * calls for help in chat when she has nothing left (and when she's starving without food). Which of these
 * she may do is in her settings file ({@link org.velvet.entity.girl.NpcSettings}).
 */
public final class SurvivalBrain {

    public enum Tactic { NONE, STEALTH, FLEE, FIGHT, SHELTER, PILLAR, DIG_IN, EXTINGUISH }

    private static final int THINK_INTERVAL = 10;
    /** She fights only when they'd need this many times longer to kill her than she needs for them. */
    private static final double FIGHT_MARGIN = 1.6;
    /** Already fighting she keeps at it unless it's clearly lost. */
    private static final double KEEP_FIGHTING_MARGIN = 1.1;
    private static final float FIGHT_MIN_HEALTH = 0.3F;
    private static final float WEAK_HEALTH = 0.4F;
    private static final float CRITICAL_HEALTH = 0.3F;
    private static final int HELP_COOLDOWN = 20 * 30;
    private static final int HUNGER_HELP_COOLDOWN = 20 * 120;
    private static final int PEARL_COOLDOWN = 20 * 5;
    private static final int CONSUME_COOLDOWN = 30;
    /** Hidden (dug in or on a pillar) she waits until nothing has been after her for this long. */
    public static final int SAFE_AFTER = 20 * 20;
    private static final int STEALTH_CHECK_INTERVAL = 40;

    private final AbstractGirlEntity girl;
    private final Threats threats;
    private final org.velvet.entity.girl.mind.Senses senses;
    private Tactic tactic = Tactic.NONE;
    /** Set by the flee goal: how often in a row it found no way out. */
    public int fleeFailures;
    private long lastHelp = -HELP_COOLDOWN, lastHungerHelp = -HUNGER_HELP_COOLDOWN, lastPearl = -PEARL_COOLDOWN;
    private long lastConsume;
    /** Digging in didn't work out (no spot after all, no way there): not again before this. */
    private long digBlockedUntil;
    private long lastThreat;
    private boolean stealthPlace;
    /** Set by the dig-in and pillar goals from when she starts digging or climbing until she's out again. */
    public boolean hidden;
    /** Dug in: the hole is covered over her head. */
    public boolean covered;
    /** Dug in: where she stood before digging (the cover goes there), and the blocks she dug, top first. */
    @Nullable public BlockPos hole;
    public final List<BlockState> pocket = new ArrayList<>();

    public SurvivalBrain(AbstractGirlEntity girl) {
        this.girl = girl;
        this.threats = new Threats(girl);
        this.senses = new org.velvet.entity.girl.mind.Senses(girl);
    }

    public org.velvet.entity.girl.mind.Senses senses() {
        return senses;
    }

    public Threats threats() {
        return threats;
    }

    public Tactic tactic() {
        return tactic;
    }

    /** Hunted, hurt a moment ago, or burning: closes her menu, makes gear precious. */
    public boolean inDanger() {
        return tactic == Tactic.FLEE || tactic == Tactic.FIGHT || tactic == Tactic.PILLAR || tactic == Tactic.EXTINGUISH
                || (tactic == Tactic.DIG_IN || tactic == Tactic.SHELTER) && !safeLongEnough();
    }

    public boolean safeLongEnough() {
        return girl.level().getGameTime() - lastThreat > SAFE_AFTER;
    }

    public long lastThreatTime() {
        return lastThreat;
    }

    public void tick() {
        threats.tick();
        senses.tick();
        if (girl.tickCount % THINK_INTERVAL != 0) return;
        org.velvet.entity.girl.NpcSettings b = girl.settings();
        long now = girl.level().getGameTime();
        if (girl.tickCount % STEALTH_CHECK_INTERVAL == 0) stealthPlace = isStealthPlace();

        boolean hunted = threats.anyHunting() || !threats.within(12).isEmpty();
        if (hunted || girl.isOnFire()) lastThreat = now;
        Tactic before = tactic;
        tactic = decide(b, hunted);
        girl.setCrouching(b.survival && b.stealth && tactic == Tactic.STEALTH);
        // like a player who sees trouble coming: armor on and the sword out
        if (b.survival && b.useItems && hunted && before == Tactic.NONE) org.velvet.entity.girl.mind.Gear.prepareForFight(girl);

        if (b.survival && b.useItems && (hunted || girl.isOnFire())) useItems();
        if (b.useItems && tactic == Tactic.NONE && girl.tickCount % 40 == 0) {
            org.velvet.entity.girl.mind.Gear.wearBestArmor(girl);
            placeTorchIfDark();
        }
        if (b.survival && b.askForHelp) maybeAskForHelp(hunted);
    }

    /** An order from her owner (in chat, or from a language model he talks to) for a while. */
    public void order(Tactic ordered, int ticks) {
        this.ordered = ordered;
        this.orderedUntil = girl.level().getGameTime() + ticks;
    }

    @Nullable private Tactic ordered;
    private long orderedUntil;

    /** The order, if it still stands and makes sense right now (never a fight with a warden). */
    @Nullable
    private Tactic orderedNow() {
        if (ordered == null || girl.level().getGameTime() > orderedUntil) return null;
        Threats.Threat worst = threats.worst();
        return switch (ordered) {
            case FIGHT -> worst != null && worst.kind != Threats.Kind.APEX ? Tactic.FIGHT : null;
            case SHELTER -> hidden || countBuildingBlocks() >= 6 && GirlShelterGoal.canShelterHere(girl) ? Tactic.SHELTER : null;
            case FLEE -> worst != null ? Tactic.FLEE : null;
            default -> null;
        };
    }

    private Tactic decide(org.velvet.entity.girl.NpcSettings b, boolean hunted) {
        if (girl.getCurrentAction().isBusy()) return Tactic.NONE;
        Tactic order = orderedNow();
        if (order != null) return order;
        // hidden: stays put until it's safe again
        if (hidden && tactic == Tactic.DIG_IN) return Tactic.DIG_IN;
        if (hidden && tactic == Tactic.SHELTER) return Tactic.SHELTER;
        if (hidden && tactic == Tactic.PILLAR && !safeLongEnough()) return Tactic.PILLAR;

        Threats.Threat worst = threats.worst();
        Threats.Threat warden = threats.warden();
        boolean stealthy = b.survival && b.stealth;
        if (worst == null || !hunted && worst.kind != Threats.Kind.APEX) {
            if (girl.isOnFire() && !girl.hasEffect(MobEffects.FIRE_RESISTANCE)) return Tactic.EXTINGUISH;
            return stealthy && (stealthPlace || warden != null) ? Tactic.STEALTH : Tactic.NONE;
        }
        // a warden that hasn't noticed her: sneak away instead of running (running is loud)
        if (warden != null && !warden.hunting && stealthy && (worst == warden || !hunted)) return Tactic.STEALTH;

        float health = girl.getHealth() / girl.getMaxHealth();
        if (b.survival && b.fightBack && canFight(worst) && health > FIGHT_MIN_HEALTH
                && fightOdds() > (tactic == Tactic.FIGHT ? KEEP_FIGHTING_MARGIN : FIGHT_MARGIN)) {
            return Tactic.FIGHT;
        }
        if (!girl.behavior().fleeMonsters) return Tactic.NONE;

        double closest = threats.nearest() == null ? 99 : threats.nearest().distanceTo(girl.position());
        boolean cornered = fleeFailures >= 2;
        boolean armed = org.velvet.entity.girl.mind.Gear.isMeleeWeapon(girl.getMainHandItem());
        boolean helpless = health < WEAK_HEALTH || !armed && !hasFood() && health < 0.7F;
        if (b.survival && closest < 8 && (cornered || helpless) && worst.kind != Threats.Kind.APEX) {
            if (b.shelter && countBuildingBlocks() >= 6 && GirlShelterGoal.canShelterHere(girl)) return Tactic.SHELTER;
            if (b.pillarUp && canPillar() && countBuildingBlocks() >= 3) return Tactic.PILLAR;
            if (b.digIn && girl.level().getGameTime() >= digBlockedUntil && canDigIn(worst)) return Tactic.DIG_IN;
        }
        return Tactic.FLEE;
    }

    /** Melee and ranged monsters; creepers only with a weapon that kills one in a few hits. */
    private boolean canFight(Threats.Threat t) {
        if (t.kind == Threats.Kind.EXPLOSIVE) return org.velvet.entity.girl.mind.Gear.attackDamage(girl.getMainHandItem()) >= 6;
        return t.kind == Threats.Kind.MELEE || t.kind == Threats.Kind.RANGED;
    }

    /**
     * How the fight would go: the time they'd need to kill her divided by the time she needs to kill them
     * (above 1 she wins). Her side: damage a second of her best weapon, health through armor, a shield and
     * what she has to heal with. Theirs: everything after her within 16 blocks, by their own damage and health.
     */
    public double fightOdds() {
        double enemyHealth = 0, enemyDps = 0;
        for (Threats.Threat t : threats.within(16)) {
            if (t.kind == Threats.Kind.APEX) return 0;
            if (!t.hunting && t.distanceTo(girl.position()) > 8) continue;
            enemyHealth += Math.max(1, t.entity.getHealth()) * (1 + t.entity.getArmorValue() * 0.04);
            enemyDps += Threats.dps(t.entity);
        }
        if (enemyDps <= 0) return 10;
        ItemStack weapon = girl.getMainHandItem();
        ItemStack best = org.velvet.entity.girl.mind.Gear.firstOf(girl, s -> org.velvet.entity.girl.mind.Gear.dps(s)
                > org.velvet.entity.girl.mind.Gear.dps(weapon));
        double myDps = org.velvet.entity.girl.mind.Gear.dps(best != null ? best : weapon)
                * (1 + 0.1 * girl.getSkillLevel(org.velvet.entity.girl.GirlSkill.WEAPONS));
        // vanilla armor takes 4% per point off (up to 80%), toughness a bit more against big hits
        double armor = Math.min(0.8, girl.getArmorValue() * 0.04
                + girl.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) * 0.01);
        boolean shield = org.velvet.entity.girl.mind.Gear.isShield(girl.getOffhandItem())
                || org.velvet.entity.girl.mind.Gear.find(girl, org.velvet.entity.girl.mind.Gear::isShield) >= 0;
        double myHealth = girl.getHealth() + healingAtHand();
        double timeToDie = myHealth / (enemyDps * (1 - armor) * (shield && girl.settings().useShield ? 0.55 : 1));
        double timeToKill = enemyHealth / Math.max(0.5, myDps);
        return timeToDie / timeToKill;
    }

    /** Health she could get back mid-fight: golden apples, healing potions, a totem. */
    private double healingAtHand() {
        double heal = 0;
        for (ItemStack s : allItems()) {
            if (s.is(Items.ENCHANTED_GOLDEN_APPLE)) heal += 16 * s.getCount();
            else if (s.is(Items.GOLDEN_APPLE)) heal += 8 * s.getCount();
            else if (s.is(Items.TOTEM_OF_UNDYING)) heal += 10;
            else if (s.is(Items.POTION) && (hasEffect(s, MobEffects.HEAL) || hasEffect(s, MobEffects.REGENERATION))) heal += 6;
        }
        return Math.min(heal, 30);
    }

    /** About how well she'd do in a fight, for the language models: her odds, roughly. */
    public double power() {
        return fightOdds();
    }

    /** Only against monsters that can't climb up or shoot her down. */
    private boolean canPillar() {
        for (Threats.Threat t : threats.within(20)) {
            if (t.kind != Threats.Kind.MELEE || t.entity instanceof Spider || t.entity instanceof CaveSpider) return false;
        }
        BlockPos head = girl.blockPosition();
        for (int dy = 2; dy <= 5; dy++) {
            if (!girl.level().getBlockState(head.above(dy)).getCollisionShape(girl.level(), head.above(dy)).isEmpty()) return false;
        }
        return girl.onGround();
    }

    /** Not from a warden (its sonic boom goes through the ground) or the wither, and only where she may dig. */
    private boolean canDigIn(Threats.Threat worst) {
        if (worst.kind == Threats.Kind.APEX) return false;
        return EventHooks.canEntityGrief(girl.level(), girl) && DigSpot.find(girl, 4) != null;
    }

    /**
     * Danger she can tell is there without seeing a monster: a shrieker's darkness on her, or a sculk
     * shrieker or sensor right by her that she can see (stepping near one calls the warden). Just being in
     * the deep dark isn't enough.
     */
    private boolean isStealthPlace() {
        if (girl.hasEffect(MobEffects.DARKNESS)) return true;
        BlockPos pos = girl.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, -2, -5), pos.offset(5, 2, 5))) {
            var state = girl.level().getBlockState(p);
            if ((state.is(Blocks.SCULK_SHRIEKER) || state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.CALIBRATED_SCULK_SENSOR))
                    && org.velvet.entity.girl.mind.BlockWork.visible(girl, p)) return true;
        }
        return false;
    }

    // ---- Using what she has ----

    private void useItems() {
        long now = girl.level().getGameTime();
        equipBestGear();
        float health = girl.getHealth() / girl.getMaxHealth();
        if (health < 0.5F) holdTotem();
        if (now - lastConsume >= CONSUME_COOLDOWN) {
            if (girl.isOnFire() && !girl.hasEffect(MobEffects.FIRE_RESISTANCE) && drinkPotion(MobEffects.FIRE_RESISTANCE)) {
                lastConsume = now;
            } else if (health < 0.5F && (drinkPotion(MobEffects.HEAL) || drinkPotion(MobEffects.REGENERATION)
                    || eatGolden())) {
                lastConsume = now;
            } else if (tactic == Tactic.FIGHT && !girl.hasEffect(MobEffects.DAMAGE_BOOST) && drinkPotion(MobEffects.DAMAGE_BOOST)) {
                lastConsume = now;
            } else if (tactic == Tactic.FLEE && !girl.hasEffect(MobEffects.MOVEMENT_SPEED) && drinkPotion(MobEffects.MOVEMENT_SPEED)) {
                lastConsume = now;
            }
        }
        Threats.Threat nearest = threats.nearest();
        if (health < CRITICAL_HEALTH && nearest != null && nearest.distanceTo(girl.position()) < 5
                && now - lastPearl > PEARL_COOLDOWN && !hidden && (tactic == Tactic.FLEE || tactic == Tactic.FIGHT)
                && throwPearl(nearest)) {
            lastPearl = now;
        }
    }

    /** Her best weapon into her hand, her best armor on, a shield into her free off hand. */
    private void equipBestGear() {
        if (girl.isUsingItem() && girl.getUseItem().has(DataComponents.FOOD)) return; // eating: not now
        org.velvet.entity.girl.mind.Gear.prepareForFight(girl);
    }

    /** A totem in a hand saves her from dying (vanilla checks both hands of any creature). */
    private void holdTotem() {
        if (girl.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || girl.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) return;
        int slot = find(s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot < 0) return;
        SimpleContainer bag = girl.getBag();
        ItemStack totem = bag.getItem(slot).split(1);
        ItemStack offhand = girl.getOffhandItem();
        girl.setItemSlot(EquipmentSlot.OFFHAND, totem);
        if (!offhand.isEmpty()) {
            ItemStack rest = GirlLoot.addToBag(girl, offhand);
            if (!rest.isEmpty()) girl.spawnAtLocation(rest);
        }
        bag.setChanged();
    }

    private boolean eatGolden() {
        int slot = find(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
        if (slot < 0) slot = find(s -> s.is(Items.GOLDEN_APPLE));
        if (slot < 0) return false;
        girl.needs().eatNow(girl.getBag().getItem(slot));
        return true;
    }

    /** Drinks a potion with this effect from her inventory (not splash or lingering ones). */
    private boolean drinkPotion(Holder<MobEffect> wanted) {
        int slot = find(s -> s.is(Items.POTION) && hasEffect(s, wanted));
        if (slot < 0) return false;
        SimpleContainer bag = girl.getBag();
        ItemStack potion = bag.getItem(slot);
        PotionContents contents = potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        contents.forEachEffect(effect -> {
            if (effect.getEffect().value().isInstantenous()) {
                effect.getEffect().value().applyInstantenousEffect(girl, girl, girl, effect.getAmplifier(), 1.0);
            } else {
                girl.addEffect(effect);
            }
        });
        girl.level().playSound(null, girl, SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        girl.swing(InteractionHand.MAIN_HAND);
        potion.shrink(1);
        ItemStack bottle = GirlLoot.addToBag(girl, new ItemStack(Items.GLASS_BOTTLE));
        if (!bottle.isEmpty()) girl.spawnAtLocation(bottle);
        bag.setChanged();
        return true;
    }

    private static boolean hasEffect(ItemStack stack, Holder<MobEffect> wanted) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (var effect : contents.getAllEffects()) if (effect.getEffect().is(wanted)) return true;
        return false;
    }

    /** Throws a pearl away from the threat, where nothing is in the way for a few blocks. */
    private boolean throwPearl(Threats.Threat threat) {
        int slot = find(s -> s.is(Items.ENDER_PEARL));
        if (slot < 0) return false;
        Vec3 away = girl.position().subtract(threat.lastSeenAt).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = Vec3.directionFromRotation(0, girl.getYRot());
        float base = (float) (Mth.atan2(away.z, away.x) * Mth.RAD_TO_DEG) - 90;
        for (float turn : new float[]{0, 30, -30, 60, -60, 90, -90}) {
            float yaw = base + turn;
            Vec3 dir = Vec3.directionFromRotation(0, yaw);
            Vec3 eye = girl.getEyePosition();
            var hit = girl.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.scale(8)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, girl));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            ItemStack pearl = girl.getBag().getItem(slot);
            ThrownEnderpearl thrown = new ThrownEnderpearl(girl.level(), girl);
            thrown.setItem(pearl.copyWithCount(1));
            // about 15 blocks: away, but not off somewhere she can't come back from
            thrown.shootFromRotation(girl, -20, yaw, 0, 0.9F, 1.0F);
            girl.level().addFreshEntity(thrown);
            girl.level().playSound(null, girl, SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
            girl.swing(InteractionHand.MAIN_HAND);
            pearl.shrink(1);
            girl.getBag().setChanged();
            return true;
        }
        return false;
    }

    /** Down in the dark with torches on her: one on the ground where she stands, unless one's close by. */
    private void placeTorchIfDark() {
        var level = girl.level();
        BlockPos at = girl.blockPosition();
        if (!girl.onGround() || girl.getCurrentAction().isBusy() || !org.velvet.entity.girl.mind.BlockWork.mayGrief(girl)) return;
        if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) > 3
                || level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at) > 6) return;
        int slot = org.velvet.entity.girl.mind.Gear.find(girl, s -> s.is(Items.TORCH));
        if (slot < 0) return;
        if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) return;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
            if (level.getBlockState(p).is(Blocks.TORCH) || level.getBlockState(p).is(Blocks.WALL_TORCH)) return;
        }
        level.setBlock(at, Blocks.TORCH.defaultBlockState(), 3);
        level.gameEvent(girl, net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, at);
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1, 1);
        girl.swing(InteractionHand.MAIN_HAND);
        girl.getBag().getItem(slot).shrink(1);
        girl.getBag().setChanged();
    }

    // ---- Calling for help ----

    private void maybeAskForHelp(boolean hunted) {
        long now = girl.level().getGameTime();
        if (hunted && now - lastHelp > HELP_COOLDOWN) {
            float health = girl.getHealth() / girl.getMaxHealth();
            Threats.Threat worst = threats.worst();
            boolean armed = GirlLoot.isWeapon(girl.getMainHandItem());
            if (worst != null && (health < 0.5F || !armed && tactic != Tactic.FIGHT && tactic != Tactic.STEALTH)) {
                lastHelp = now;
                BlockPos p = girl.blockPosition();
                Component where = Component.literal(p.getX() + " " + p.getY() + " " + p.getZ());
                String need = !armed ? "weapon" : health < 0.5F ? "hurt" : "generic";
                GirlChat.say(girl, Component.translatable("chat.velvet.help." + need, worst.entity.getName(), where));
            }
        }
        if (!hunted && girl.getFood() <= 6 && !hasFood() && now - lastHungerHelp > HUNGER_HELP_COOLDOWN) {
            lastHungerHelp = now;
            GirlChat.say(girl, Component.translatable("chat.velvet.help.hungry"));
        }
    }

    /** Called by the dig-in goal when there's nowhere to dig after all: she runs instead for a while. */
    public void digFailed() {
        digBlockedUntil = girl.level().getGameTime() + 100;
        if (tactic == Tactic.DIG_IN && !hidden) tactic = Tactic.FLEE;
    }

    /** Called by the dig-in goal once she's covered. */
    public void announceHidden() {
        BlockPos p = girl.blockPosition();
        GirlChat.say(girl, Component.translatable("chat.velvet.help.hidden",
                Component.literal(p.getX() + " " + p.getY() + " " + p.getZ())));
    }

    // ---- Inventory helpers ----

    private int find(java.util.function.Predicate<ItemStack> test) {
        SimpleContainer bag = girl.getBag();
        for (int i = 0; i < girl.getBagCapacity(); i++) if (test.test(bag.getItem(i))) return i;
        return -1;
    }

    private boolean hasFood() {
        for (ItemStack s : allItems()) {
            var food = s.getFoodProperties(girl);
            if (food != null && food.nutrition() > 0) return true;
        }
        return false;
    }

    private List<ItemStack> allItems() {
        List<ItemStack> all = new ArrayList<>(girl.getBag().getItems().subList(0, girl.getBagCapacity()));
        all.add(girl.getMainHandItem());
        all.add(girl.getOffhandItem());
        return all;
    }

    /** Whole blocks she can stand on, cheapest first is up to {@link #takeBuildingBlock}. */
    public static boolean isBuildingBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        Block block = item.getBlock();
        var state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && !state.hasBlockEntity() && !(block instanceof net.minecraft.world.level.block.FallingBlock);
    }

    public int countBuildingBlocks() {
        int n = 0;
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack s = girl.getBag().getItem(i);
            if (isBuildingBlock(s)) n += s.getCount();
        }
        return n;
    }

    /** One block from her inventory to build with, the least worth first; null if she has none. */
    @Nullable
    public Block takeBuildingBlock() {
        SimpleContainer bag = girl.getBag();
        int best = -1;
        var context = org.velvet.entity.girl.loot.LootContext.of(girl);
        org.velvet.entity.girl.loot.ItemCaste bestCaste = null;
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack s = bag.getItem(i);
            if (!isBuildingBlock(s)) continue;
            var caste = org.velvet.entity.girl.loot.ItemCastes.caste(context, s);
            if (bestCaste == null || bestCaste.above(caste)) {
                best = i;
                bestCaste = caste;
            }
        }
        if (best < 0) return null;
        ItemStack s = bag.getItem(best);
        Block block = ((BlockItem) s.getItem()).getBlock();
        s.shrink(1);
        bag.setChanged();
        return block;
    }

    /** A block she took for building but couldn't place goes back into her inventory. */
    public void giveBack(Block block) {
        ItemStack rest = GirlLoot.addToBag(girl, new ItemStack(block));
        if (!rest.isEmpty()) girl.spawnAtLocation(rest);
    }

    // ---- Saving: only what keeps her safe in her hole over a reload ----

    public void save(CompoundTag tag) {
        if (hole == null) return;
        CompoundTag hiding = new CompoundTag();
        hiding.put("hole", NbtUtils.writeBlockPos(hole));
        hiding.putBoolean("covered", covered);
        ListTag blocks = new ListTag();
        for (BlockState state : pocket) blocks.add(NbtUtils.writeBlockState(state));
        hiding.put("pocket", blocks);
        tag.put("hiding", hiding);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("hiding")) return;
        CompoundTag hiding = tag.getCompound("hiding");
        hole = NbtUtils.readBlockPos(hiding, "hole").orElse(null);
        if (hole == null) return;
        covered = hiding.getBoolean("covered");
        hidden = true;
        tactic = Tactic.DIG_IN;
        pocket.clear();
        var blocks = girl.level().holderLookup(Registries.BLOCK);
        for (Tag t : hiding.getList("pocket", Tag.TAG_COMPOUND)) pocket.add(NbtUtils.readBlockState(blocks, (CompoundTag) t));
    }

    public void clear() {
        threats.clear();
        tactic = Tactic.NONE;
        hidden = false;
        covered = false;
        hole = null;
        pocket.clear();
        fleeFailures = 0;
    }
}
