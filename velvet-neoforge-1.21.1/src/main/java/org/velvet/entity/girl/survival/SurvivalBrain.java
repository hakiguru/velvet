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
import net.minecraft.world.level.biome.Biomes;
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
 * Her instinct to stay alive, server side. Every half second it looks at the threats around her
 * ({@link Threats}) and her own state and picks a {@link Tactic}, which the survival goals carry out:
 * <ul>
 *   <li>STEALTH: in the deep dark, near sculk shriekers or with a warden around that isn't after her yet she
 *       sneaks (like a sneaking player: no vibrations, smaller, slower) and keeps away from it.</li>
 *   <li>FIGHT: monsters she can beat (weapon, armor and health against their strength), never creepers or
 *       bosses.</li>
 *   <li>FLEE: away along the best escape route; she keeps going while they're after her.</li>
 *   <li>PILLAR: cornered by monsters that can't climb or shoot, with blocks to spare: a pillar up.</li>
 *   <li>DIG_IN: weak, cornered or without anything to defend herself with: she digs down into soft ground,
 *       covers the hole and waits there; afterwards everything is put back as it was.</li>
 *   <li>EXTINGUISH: burning with nothing around: to the nearest water.</li>
 * </ul>
 * Meanwhile she uses what she has: puts on her best gear from the inventory, holds a totem when she's
 * nearly dead, eats golden apples and drinks potions that help, throws an ender pearl as a last resort, and
 * calls for help in chat when she has nothing left (and when she's starving without food).
 */
public final class SurvivalBrain {

    public enum Tactic { NONE, STEALTH, FLEE, FIGHT, PILLAR, DIG_IN, EXTINGUISH }

    private static final int THINK_INTERVAL = 10;
    /** She fights only when her strength beats theirs by this much. */
    private static final double FIGHT_MARGIN = 1.25;
    private static final float FIGHT_MIN_HEALTH = 0.45F;
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
                || tactic == Tactic.DIG_IN && !safeLongEnough();
    }

    public boolean safeLongEnough() {
        return girl.level().getGameTime() - lastThreat > SAFE_AFTER;
    }

    public long lastThreatTime() {
        return lastThreat;
    }

    public void tick() {
        threats.tick();
        if (girl.tickCount % THINK_INTERVAL != 0) return;
        GirlBehavior b = girl.behavior();
        long now = girl.level().getGameTime();
        if (girl.tickCount % STEALTH_CHECK_INTERVAL == 0) stealthPlace = isStealthPlace();

        boolean hunted = threats.anyHunting() || !threats.within(10).isEmpty();
        if (hunted || girl.isOnFire()) lastThreat = now;
        tactic = decide(b, hunted);
        girl.setCrouching(b.survival && b.stealth && tactic == Tactic.STEALTH);

        if (b.survival && b.useItems && (hunted || girl.isOnFire())) useItems();
        if (b.survival && b.askForHelp) maybeAskForHelp(hunted);
    }

    private Tactic decide(GirlBehavior b, boolean hunted) {
        if (girl.getCurrentAction().isBusy()) return Tactic.NONE;
        // hidden: stays put until it's safe again
        if (hidden && tactic == Tactic.DIG_IN) return Tactic.DIG_IN;
        if (hidden && tactic == Tactic.PILLAR && !safeLongEnough()) return Tactic.PILLAR;

        Threats.Threat worst = threats.worst();
        Threats.Threat warden = threats.warden();
        if (worst == null || !hunted && worst.kind != Threats.Kind.APEX) {
            if (girl.isOnFire() && !girl.hasEffect(MobEffects.FIRE_RESISTANCE)) return Tactic.EXTINGUISH;
            return b.survival && b.stealth && (stealthPlace || warden != null) ? Tactic.STEALTH : Tactic.NONE;
        }
        // a warden that hasn't noticed her: sneak away instead of running (running is loud)
        if (warden != null && !warden.hunting && b.survival && b.stealth && (worst == warden || !hunted)) return Tactic.STEALTH;

        float health = girl.getHealth() / girl.getMaxHealth();
        double near = threats.dangerWithin(10);
        boolean armed = GirlLoot.isWeapon(girl.getMainHandItem());
        if (b.survival && b.fightBack && canFight(worst) && health > FIGHT_MIN_HEALTH
                && (armed || near < 4) && power() > near * FIGHT_MARGIN) {
            return Tactic.FIGHT;
        }
        if (!b.fleeMonsters) return Tactic.NONE;

        double closest = threats.nearest() == null ? 99 : threats.nearest().distanceTo(girl.position());
        boolean cornered = fleeFailures >= 2;
        boolean helpless = health < WEAK_HEALTH || !armed && !hasFood() && health < 0.7F;
        if (b.survival && closest < 7 && (cornered || helpless)) {
            if (b.pillarUp && canPillar() && countBuildingBlocks() >= 3) return Tactic.PILLAR;
            if (b.digIn && girl.level().getGameTime() >= digBlockedUntil && canDigIn(worst)) return Tactic.DIG_IN;
        }
        return Tactic.FLEE;
    }

    private static boolean canFight(Threats.Threat t) {
        return t.kind == Threats.Kind.MELEE || t.kind == Threats.Kind.RANGED;
    }

    /** About how well she'd do in a fight: her hits, her health and armor, a shield. */
    public double power() {
        double attack = girl.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double health = girl.getHealth() / 20;
        boolean shield = girl.getOffhandItem().getItem() instanceof ShieldItem;
        return attack * Math.sqrt(Math.max(0.1, health)) * (1 + girl.getArmorValue() / 20.0) * (shield ? 1.3 : 1) * 1.5;
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

    private boolean isStealthPlace() {
        BlockPos pos = girl.blockPosition();
        if (girl.level().getBiome(pos).is(Biomes.DEEP_DARK)) return true;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -4, -8), pos.offset(8, 4, 8))) {
            var state = girl.level().getBlockState(p);
            if (state.is(Blocks.SCULK_SHRIEKER) || state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.CALIBRATED_SCULK_SENSOR)) return true;
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
        SimpleContainer bag = girl.getBag();
        for (int i = 0; i < girl.getBagCapacity(); i++) {
            ItemStack stack = bag.getItem(i);
            if (stack.isEmpty()) continue;
            EquipmentSlot slot = null;
            if (stack.getItem() instanceof ArmorItem) slot = girl.getEquipmentSlotForItem(stack);
            else if (GirlLoot.isWeapon(stack)) slot = EquipmentSlot.MAINHAND;
            else if (stack.getItem() instanceof ShieldItem) slot = EquipmentSlot.OFFHAND;
            if (slot == null) continue;
            ItemStack current = girl.getItemBySlot(slot);
            boolean replace = current.isEmpty()
                    || (slot.isArmor() || slot == EquipmentSlot.MAINHAND && GirlLoot.isWeapon(current)) && girl.isBetterGear(stack, current);
            // with a totem in the off hand the shield waits
            if (slot == EquipmentSlot.OFFHAND && !current.isEmpty()) replace = false;
            if (!replace) continue;
            girl.setItemSlot(slot, stack.split(1));
            bag.setItem(i, stack.isEmpty() ? current : stack);
            if (!stack.isEmpty() && !current.isEmpty()) {
                ItemStack rest = GirlLoot.addToBag(girl, current);
                if (!rest.isEmpty()) girl.spawnAtLocation(rest);
            }
            bag.setChanged();
        }
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
