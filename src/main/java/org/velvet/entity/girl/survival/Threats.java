package org.velvet.entity.girl.survival;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything around a girl that could hurt her, server side, noticed the way a player would: monsters she
 * can see (not through walls) up to {@link #SIGHT} blocks, ones close enough to hear, anything that hit her
 * and a warden (it's loud). Each one is remembered for a while after she loses sight of it (a warden
 * longer), with where it was last seen, so she doesn't stop running the moment it's behind a wall. For
 * fights she also keeps track of when each one last struck or shot, and whether it's drawing a bow at her.
 */
public final class Threats {

    public enum Kind {
        /** Hits up close. */
        MELEE,
        /** Shoots or casts from a distance. */
        RANGED,
        /** Creepers: stay well away, never fight up close. */
        EXPLOSIVE,
        /** Warden, wither, dragon: never fight, run or hide. */
        APEX
    }

    public static final class Threat {
        public final LivingEntity entity;
        public final Kind kind;
        public Vec3 lastSeenAt;
        public long lastSeen;
        /** It's after her: targeting her, angry at her, or it hit her. */
        public boolean hunting;
        /** It can see her right now. */
        public boolean visible;
        public double danger;
        /** Game time of its last swing or shot (-1: none seen). */
        public long lastAttack = -1;
        /** Drawing a bow or loading a crossbow, at her. */
        public boolean aiming;
        boolean wasSwinging;

        Threat(LivingEntity entity, Kind kind) {
            this.entity = entity;
            this.kind = kind;
            this.lastSeenAt = entity.position();
        }

        public double distanceTo(Vec3 pos) {
            return lastSeenAt.distanceTo(pos);
        }
    }

    /** How far she sees monsters (only with nothing in the way). */
    public static final double SIGHT = 32;
    /** This close she hears them through walls. */
    private static final double HEARING = 5;
    /** A warden is noticed from this far, and anything hunting her too. */
    private static final double FAR_SCAN_RADIUS = 40;
    /** A warden's heartbeat and footsteps carry this far through walls. */
    private static final double WARDEN_HEARING = 20;
    private static final int FORGET_AFTER = 200;
    private static final int FORGET_APEX_AFTER = 600;
    private static final int SCAN_INTERVAL = 5;

    private final AbstractGirlEntity girl;
    private final Map<Integer, Threat> known = new HashMap<>();
    private List<Threat> sorted = List.of();

    public Threats(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public void tick() {
        long now = girl.level().getGameTime();
        watchAttacks(now);
        if (girl.tickCount % SCAN_INTERVAL != 0) return;
        Vec3 pos = girl.position();
        AABB far = girl.getBoundingBox().inflate(FAR_SCAN_RADIUS, 20, FAR_SCAN_RADIUS);
        for (LivingEntity e : girl.level().getEntitiesOfClass(LivingEntity.class, far, e -> e != girl && e.isAlive())) {
            boolean hunting = isHunting(e);
            boolean warden = e instanceof Warden;
            if (!(hunting || warden || isHostile(e))) continue;
            double dist = e.distanceTo(girl);
            Threat t = known.get(e.getId());
            boolean visible = dist <= SIGHT && girl.hasLineOfSight(e);
            boolean heard = dist <= HEARING || warden && dist <= WARDEN_HEARING;
            // what she can't see or hear she doesn't know about, unless she knew before
            if (t == null && !visible && !heard && !(hunting && dist <= 16)) continue;
            if (t == null) {
                t = new Threat(e, kindOf(e));
                known.put(e.getId(), t);
            }
            t.visible = visible;
            t.hunting = hunting || t.hunting && now - t.lastSeen < 60;
            if (visible || heard || hunting && dist < 16) {
                t.lastSeenAt = e.position();
                t.lastSeen = now;
            }
        }
        // whatever hurt her (an arrow from out of sight) is hunting her
        LivingEntity attacker = girl.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && !(attacker instanceof Player)
                && girl.tickCount - girl.getLastHurtByMobTimestamp() < 100) {
            Threat t = known.computeIfAbsent(attacker.getId(), id -> new Threat(attacker, kindOf(attacker)));
            t.hunting = true;
            t.lastSeenAt = attacker.position();
            t.lastSeen = now;
        }
        known.values().removeIf(t -> !t.entity.isAlive() || t.entity.isRemoved() || t.entity.level() != girl.level()
                || now - t.lastSeen > (t.kind == Kind.APEX ? FORGET_APEX_AFTER : FORGET_AFTER)
                || t.kind == Kind.APEX && !t.hunting && t.distanceTo(pos) > FAR_SCAN_RADIUS);
        List<Threat> list = new ArrayList<>(known.values());
        for (Threat t : list) t.danger = danger(t, pos);
        list.sort((a, b) -> Double.compare(b.danger, a.danger));
        sorted = Collections.unmodifiableList(list);
    }

    /** Every tick for the ones close by: when they strike, and whether they're aiming at her. */
    private void watchAttacks(long now) {
        for (Threat t : sorted) {
            LivingEntity e = t.entity;
            if (e.distanceToSqr(girl) > 24 * 24) continue;
            if (e.swinging && !t.wasSwinging) t.lastAttack = now;
            t.wasSwinging = e.swinging;
            boolean drawing = e.isUsingItem() && (e.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem
                    || e.getUseItem().getItem() instanceof net.minecraft.world.item.CrossbowItem);
            boolean atHer = e instanceof Mob m && m.getTarget() == girl;
            // the moment the string is let go
            if (t.aiming && !drawing) t.lastAttack = now;
            t.aiming = drawing && atHer;
        }
    }

    /** Ticks since it last struck or shot; a large number if never seen. */
    public static long sinceAttack(Threat t, long now) {
        return t.lastAttack < 0 ? 1000 : now - t.lastAttack;
    }

    /** Most dangerous first. */
    public List<Threat> all() {
        return sorted;
    }

    public boolean isEmpty() {
        return sorted.isEmpty();
    }

    @Nullable
    public Threat worst() {
        return sorted.isEmpty() ? null : sorted.getFirst();
    }

    @Nullable
    public Threat nearest() {
        Vec3 pos = girl.position();
        Threat best = null;
        for (Threat t : sorted) if (best == null || t.distanceTo(pos) < best.distanceTo(pos)) best = t;
        return best;
    }

    /** Within {@code radius} blocks (where last seen). */
    public List<Threat> within(double radius) {
        Vec3 pos = girl.position();
        List<Threat> out = new ArrayList<>();
        for (Threat t : sorted) if (t.distanceTo(pos) <= radius) out.add(t);
        return out;
    }

    public boolean anyHunting() {
        for (Threat t : sorted) if (t.hunting) return true;
        return false;
    }

    /** A warden she knows of, and whether it's after her. */
    @Nullable
    public Threat warden() {
        for (Threat t : sorted) if (t.entity instanceof Warden) return t;
        return null;
    }

    /** How much the threats within {@code radius} could hurt her in a fight, summed up. */
    public double dangerWithin(double radius) {
        double sum = 0;
        for (Threat t : within(radius)) sum += strength(t.entity);
        return sum;
    }

    public void clear() {
        known.clear();
        sorted = List.of();
    }

    // ---- Judging ----

    private boolean isHunting(LivingEntity e) {
        if (e instanceof Warden w) {
            return w.getEntityAngryAt().orElse(null) == girl || w.getAngerManagement().getActiveAnger(girl) >= 40;
        }
        return e instanceof Mob mob && mob.getTarget() == girl;
    }

    /** Monsters that attack on sight; neutral ones only once they're angry, piglins only when after her. */
    private static boolean isHostile(LivingEntity e) {
        if (!(e instanceof Enemy) || e.isSpectator()) return false;
        if (e instanceof NeutralMob neutral && !neutral.isAngry()) return false;
        return !(e instanceof AbstractPiglin);
    }

    public static Kind kindOf(LivingEntity e) {
        if (e instanceof Warden || e instanceof WitherBoss || e instanceof EnderDragon) return Kind.APEX;
        if (e instanceof Creeper) return Kind.EXPLOSIVE;
        if (e instanceof net.minecraft.world.entity.monster.RangedAttackMob || e instanceof CrossbowAttackMob
                || e instanceof Ghast || e instanceof Blaze || e instanceof Shulker || e instanceof Guardian
                || e instanceof Breeze || e instanceof Evoker || e instanceof Illusioner) {
            return Kind.RANGED;
        }
        return Kind.MELEE;
    }

    /** About how much damage a second it does to her before armor: melee monsters strike about once a second. */
    public static double dps(LivingEntity e) {
        Kind kind = kindOf(e);
        if (kind == Kind.APEX) return 40;
        if (kind == Kind.EXPLOSIVE) return 10;
        double attack = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 3;
        // an arrow or a fireball every couple of seconds
        if (kind == Kind.RANGED) return Math.max(3, attack) * 0.6;
        return Math.max(1, attack);
    }

    /** About how hard it hits, scaled by how long it lasts in a fight. */
    public static double strength(LivingEntity e) {
        Kind kind = kindOf(e);
        if (kind == Kind.APEX) return 1000;
        double attack = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 3;
        if (kind == Kind.RANGED) attack = Math.max(attack, 4);
        if (kind == Kind.EXPLOSIVE) attack = 20;
        return attack * Math.sqrt(Math.max(1, e.getHealth()) / 20) * (1 + e.getArmorValue() / 20.0);
    }

    /** Ranking for the most dangerous threat: strength, closeness and whether it's after her. */
    private static double danger(Threat t, Vec3 pos) {
        double d = Math.max(1, t.distanceTo(pos));
        double s = strength(t.entity) * (t.hunting ? 2 : 1) / d;
        if (t.kind == Kind.EXPLOSIVE && t.entity instanceof Creeper c && c.getSwellDir() > 0) s *= 4;
        return s;
    }
}
