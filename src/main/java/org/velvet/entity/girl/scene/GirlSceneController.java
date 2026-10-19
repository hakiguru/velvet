package org.velvet.entity.girl.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlAction;
import org.velvet.entity.girl.GirlManager;
import org.velvet.entity.girl.GirlSkill;

/**
 * Server-side driver of the girl's {@link GirlAction} stages: starting paid scenes, timing stage
 * transitions, walking to the bed, keeping both participants in place, filling the progress bar and
 * ending the scene safely.
 */
public final class GirlSceneController {

    /** Max distance between player and girl to start a scene. */
    private static final double START_DISTANCE = 6.0;
    /** The scene ends if the partner gets further away than this (teleport, death, walking off, etc.). */
    private static final double LOST_DISTANCE = 12.0;
    /** Give up walking to the bed after this many ticks. */
    private static final int BED_WALK_TIMEOUT = 20 * 20;
    /** She's at the bed once this close (horizontally) to the spot's centre. */
    private static final double BED_ARRIVE_DISTANCE = 0.6;
    /** Closer than this, she walks straight to the spot instead of following a path. */
    private static final double BED_NEAR_DISTANCE = 2.5;
    /** Give up waiting on the bed for the partner after this many ticks. */
    private static final int PARTNER_WAIT_TIMEOUT = 60 * 20;
    /** The partner has to step this close (horizontally) to where she climbed onto the bed. */
    private static final double PARTNER_ARRIVE_DISTANCE = 0.6;
    /** STRIP switches to the nude model at the "becomeNude" keyframe (3.0 s). */
    private static final int STRIP_NUDE_TICK = 60;
    /** Where the partner stands, in blocks in front of the girl; matches the "steve" bone (z = -20px). */
    private static final double PARTNER_DISTANCE = 1.25;
    private static final int SCENE_AFFECTION_XP = 40;
    private static final int SCENE_LUST_XP = 60;
    private static final int SCENE_CAPACITY_XP = 30;
    private static final int SCENE_ENDURANCE_XP = 20;
    /** How long the slow loop alone takes to fill the bar at endurance 0; each level adds a tenth of it. */
    private static final float SLOW_SCENE_TICKS = 60 * 20;
    private static final float SCENE_TICKS_PER_ENDURANCE = 0.1F;
    /** The fast loop fills the bar this many times quicker than the slow one. */
    private static final float FAST_FILL_RATE = 4;

    private final AbstractGirlEntity girl;
    /** The scene being played (from the payment on), server side. */
    @Nullable private SceneType scene;
    /** Where she's held during the attached part of a bed scene. */
    @Nullable private Vec3 pinned;
    @Nullable private SceneType pending;
    @Nullable private BedFinder.Bed bed;
    private int stageTicks;
    private float sceneYaw;
    /** Whether the partner has been seated on the girl in this stage; leaving the seat ends the scene. */
    private boolean seated;
    /** The partner holds the speed key. */
    private boolean fast;
    private float progress;

    public GirlSceneController(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    // ---- Commands ----

    /** Starts a paid scene with {@code player}; on failure tells the player why and changes nothing. */
    public void start(ServerPlayer player, SceneType type) {
        if (girl.getCurrentAction().isBusy()) {
            say(player, "busy");
            return;
        }
        if (!SceneType.of(girl.getGirlType()).contains(type)) return; // not one hers offers
        if (girl.distanceTo(player) > START_DISTANCE) {
            say(player, "too_far");
            return;
        }
        if (girl.psyche().notInTheMood()) {
            say(player, "not_in_the_mood");
            return;
        }
        if (!player.onGround()) {
            say(player, "stand_on_ground");
            return;
        }
        if (GirlManager.partnerOf(player) != null) {
            say(player, "already_in_scene");
            return;
        }
        BedFinder.Bed foundBed = null;
        SceneType.Place where = type.flow(girl.getGirlType()).place();
        if (where.bed()) {
            // checked before taking the payment so nothing is lost when there's no bed
            foundBed = BedFinder.find(girl, where.spot());
            if (foundBed == null) {
                say(player, where.spot() == SceneType.Spot.FOOT ? "no_bed_foot" : "no_bed");
                return;
            }
        }
        boolean pay = !player.getAbilities().instabuild;
        if (pay && !takePayment(player.getInventory(), type.cost())) {
            say(player, "cant_afford");
            return;
        }

        girl.setPartnerUUID(player.getUUID());
        girl.getNavigation().stop();
        this.bed = foundBed;
        this.pending = type;
        this.scene = type;
        this.fast = false;
        setProgress(0);
        faceTowards(player.position());
        if (pay) {
            setStage(GirlAction.PAYMENT);
        } else {
            beginScene();
        }
    }

    /** The partner pressed or released the speed key. */
    public void setFast(ServerPlayer player, boolean fast) {
        if (player.getUUID().equals(girl.getPartnerUUID())) this.fast = fast;
    }

    /** Ends whatever the girl is doing with a partner. Safe to call at any time. */
    public void stop() {
        pending = null;
        scene = null;
        pinned = null;
        bed = null;
        seated = false;
        fast = false;
        setProgress(0);
        girl.ejectPassengers();
        girl.setPartnerUUID(null);
        if (girl.getCurrentAction() != GirlAction.NULL) setStage(GirlAction.NULL);
    }

    public void strip() {
        if (girl.getCurrentAction().isBusy() || girl.getModelVariant() == AbstractGirlEntity.VARIANT_NUDE) return;
        setStage(GirlAction.STRIP);
    }

    // ---- Tick ----

    public void tick() {
        GirlAction action = girl.getCurrentAction();
        if (action == GirlAction.NULL) return;
        stageTicks++;

        ServerPlayer partner = null;
        if (action.hasPartner()) {
            partner = partner();
            if (partner == null) {
                stop();
                return;
            }
            if (action.isAttached() && !holdInPlace(partner)) return;
        }

        switch (action) {
            case STRIP -> {
                if (stageTicks == STRIP_NUDE_TICK) girl.setModelVariant(AbstractGirlEntity.VARIANT_NUDE);
            }
            case SCENE_STRIP -> {
                if (stageTicks == STRIP_NUDE_TICK) girl.setModelVariant(AbstractGirlEntity.VARIANT_NUDE);
                if (stageTicks >= action.length) beginScene();
                return;
            }
            case PAYMENT -> {
                if (stageTicks >= action.length) beginScene();
                return;
            }
            default -> {}
        }
        switch (action.bed()) {
            case WALK -> {
                tickWalkToBed(partner);
                return;
            }
            case CLIMB, WAIT -> {
                if (!tickOnBed(action, partner)) return;
                if (action.bed() == GirlAction.Bed.CLIMB && stageTicks >= action.length) {
                    setStage(action.next() != null ? action.next() : flow().place().waiting());
                }
                return;
            }
            default -> {}
        }

        if (action.play == GirlAction.Play.ONCE && stageTicks >= action.length) {
            nextStage(action);
        } else if (action.isPaced()) {
            tickPaced(action);
        }
    }

    /**
     * Main part of a scene: holding the speed key switches to the fast loop right away, releasing it goes
     * back to the slow one when the fast cycle ends. Each finished cycle fills the bar, and a full bar starts
     * the finale at the end of a cycle so the animation isn't cut off.
     */
    private void tickPaced(GirlAction action) {
        if (action.pace == GirlAction.Pace.SLOW && fast && progress < 1) {
            setStage(action.next());
            return;
        }
        boolean cycleEnded = Mth.floor(stageTicks / action.cycle) != Mth.floor((stageTicks - 1) / action.cycle);
        if (!cycleEnded) return;
        setProgress(Math.min(1, progress + fillPerCycle(action)));
        if (progress >= 1) {
            setStage(action.finish());
        } else if (action.pace == GirlAction.Pace.FAST && !fast) {
            setStage(action.next());
        }
    }

    /** How much one cycle of a paced loop fills the bar: the whole scene lasts longer the more endurance she has. */
    private float fillPerCycle(GirlAction action) {
        float sceneTicks = SLOW_SCENE_TICKS * (1 + SCENE_TICKS_PER_ENDURANCE * girl.getSkillLevel(GirlSkill.ENDURANCE));
        return action.cycle / sceneTicks * (action.pace == GirlAction.Pace.FAST ? FAST_FILL_RATE : 1);
    }

    // ---- Stages ----

    private void beginScene() {
        SceneType type = pending;
        if (type == null) {
            stop();
            return;
        }
        if (type.undresses(girl.getGirlType()) && girl.getModelVariant() != AbstractGirlEntity.VARIANT_NUDE) {
            setStage(GirlAction.SCENE_STRIP); // comes back here once she's undressed
            return;
        }
        pending = null;
        if (type.needsBed(girl.getGirlType()) && bed != null) {
            // the partner walks along to the bed himself and is seated again once she's waiting there
            setStage(GirlAction.BED_WALK);
            girl.ejectPassengers();
            seated = false;
            walkTo(bed.spot());
        } else {
            setStage(type.flow(girl.getGirlType()).firstStage());
        }
    }

    private void nextStage(GirlAction action) {
        GirlAction next = action.next();
        if (next == null || next == GirlAction.NULL) {
            if (action.hasPartner()) { // finished, not aborted
                girl.psyche().onSceneFinished();
                girl.addSkillXp(GirlSkill.AFFECTION, SCENE_AFFECTION_XP);
                girl.addSkillXp(GirlSkill.LUST, SCENE_LUST_XP);
                girl.addSkillXp(GirlSkill.CAPACITY, SCENE_CAPACITY_XP);
                girl.addSkillXp(GirlSkill.ENDURANCE, SCENE_ENDURANCE_XP);
            }
            stop();
        } else {
            setStage(next);
        }
    }

    /** How the current scene is played on her preset. */
    private SceneType.Flow flow() {
        return scene.flow(girl.getGirlType());
    }

    private void setStage(GirlAction action) {
        stageTicks = 0;
        girl.setCurrentAction(action);
    }

    private void tickWalkToBed(ServerPlayer partner) {
        if (!bedStillFree(partner)) return;
        BlockPos spot = bed.spot();
        double dx = girl.getX() - (spot.getX() + 0.5);
        double dz = girl.getZ() - (spot.getZ() + 0.5);
        double distSqr = dx * dx + dz * dz;
        if (distSqr < BED_ARRIVE_DISTANCE * BED_ARRIVE_DISTANCE && girl.onGround()) {
            SceneType.Place place = flow().place();
            girl.getNavigation().stop();
            if (place.liesDown()) {
                // into the bed, lying with her head towards the spot, where he'll come to her
                Vec3 toBed = Vec3.atLowerCornerOf(bed.toBed().getNormal());
                sceneYaw = bed.toBed().getOpposite().toYRot();
                pinned = Vec3.atBottomCenterOf(spot).add(toBed.scale(place.forward())).add(0, place.up(), 0);
                girl.moveTo(pinned.x, pinned.y, pinned.z, sceneYaw, 0);
            } else {
                // stand on the spot facing the bed; the animation itself climbs onto it
                sceneYaw = bed.toBed().toYRot();
                girl.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, sceneYaw, 0);
            }
            freeze();
            // climbing onto it or sitting down on its edge, else waiting right away
            setStage(place.arrive() != null ? place.arrive() : place.waiting());
            return;
        }
        if (stageTicks > BED_WALK_TIMEOUT) {
            say(partner, "bed_unreachable");
            stop();
            return;
        }
        if (girl.getNavigation().isInProgress()) return;
        if (distSqr < BED_NEAR_DISTANCE * BED_NEAR_DISTANCE) {
            // a path counts as done up to a block or so from its end: walk the last bit straight to the centre
            girl.getMoveControl().setWantedPosition(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        } else if (stageTicks % 20 == 0) {
            walkTo(spot);
        }
    }

    /**
     * On the bed, waiting for the partner: he has to step onto the spot she climbed up from (right behind
     * her) and stand on the ground. Returns false if the stage changed or the scene ended.
     */
    private boolean tickOnBed(GirlAction action, ServerPlayer partner) {
        freeze();
        if (!bedStillFree(partner)) return false;
        if (action.bed() != GirlAction.Bed.WAIT) return true;

        // the spot she came to the bed from (where she still stands, unless she lay down in it)
        Vec3 spot = Vec3.atBottomCenterOf(bed.spot());
        double dx = partner.getX() - spot.x;
        double dz = partner.getZ() - spot.z;
        if (dx * dx + dz * dz < PARTNER_ARRIVE_DISTANCE * PARTNER_ARRIVE_DISTANCE
                && Math.abs(partner.getY() - spot.y) < 0.5 && partner.onGround() && !partner.isPassenger()) {
            // onto the bed (or its edge) for the attached part
            SceneType.Place place = flow().place();
            if (place.liesDown()) {
                // she's there already
            } else if (place.alongBed()) {
                Direction toHead = Direction.fromDelta(bed.head().getX() - bed.foot().getX(), 0, bed.head().getZ() - bed.foot().getZ());
                pinned = Vec3.atBottomCenterOf(bed.foot()).add(Vec3.atLowerCornerOf(toHead.getNormal()).scale(place.along()));
                sceneYaw = toHead.toYRot();
            } else {
                Vec3 toBed = Vec3.atLowerCornerOf(bed.toBed().getNormal());
                pinned = girl.position().add(toBed.scale(place.forward())).add(0, place.up(), 0);
            }
            girl.moveTo(pinned.x, pinned.y, pinned.z, sceneYaw, 0);
            setStage(flow().firstStage());
            holdInPlace(partner);
            return false;
        }
        if (stageTicks > PARTNER_WAIT_TIMEOUT) {
            say(partner, "waited_too_long");
            stop();
            return false;
        }
        return false;
    }

    private boolean bedStillFree(ServerPlayer partner) {
        if (bed != null && girl.level() instanceof ServerLevel level && BedFinder.stillFree(level, bed)) return true;
        // bed broken, someone lay down in it or the spot got blocked
        say(partner, "no_bed");
        stop();
        return false;
    }

    private void walkTo(BlockPos spot) {
        girl.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
    }

    // ---- Positioning ----

    /** Keeps the girl still, facing the scene direction (and on the bed where she was put). */
    private void freeze() {
        girl.getNavigation().stop();
        GirlAction.Bed role = girl.getCurrentAction().bed();
        // on the bed for the attached part, or lying in it already while she waits
        if (pinned != null && (role == GirlAction.Bed.ON || role == GirlAction.Bed.WAIT)) {
            girl.setDeltaMovement(Vec3.ZERO);
            if (girl.position().distanceToSqr(pinned) > 1.0E-4) girl.setPos(pinned);
        } else {
            girl.setDeltaMovement(0, girl.getDeltaMovement().y, 0);
        }
        girl.setYRot(sceneYaw);
        girl.setYBodyRot(sceneYaw);
        girl.setYHeadRot(sceneYaw);
    }

    /**
     * Keeps the girl still and the partner seated on her (as a passenger, so the girl positions him every
     * tick without fighting collisions). Returns false if the scene ended because the partner got off.
     */
    private boolean holdInPlace(ServerPlayer partner) {
        freeze();
        if (partner.getVehicle() != girl) {
            if (seated) {
                // sneaking dismounts a passenger: that's how the partner leaves the scene
                stop();
                return false;
            }
            partner.stopRiding();
            seated = partner.startRiding(girl, true);
        }
        return true;
    }

    /**
     * Where the partner is when no animated bone position is known (server, first client frame): as far
     * ahead of her as the scene has him.
     */
    public static Vec3 partnerAnchor(AbstractGirlEntity girl) {
        SceneType.Flow type = SceneType.flowOfStage(girl.getCurrentAction());
        double ahead = type != null ? type.place().partnerAhead() : PARTNER_DISTANCE;
        Vec3 forward = Vec3.directionFromRotation(0, girl.getYRot());
        return girl.position().add(forward.scale(ahead));
    }

    /**
     * Where the partner stands after the scene: at a bed, on the spot she came from; elsewhere where he
     * stood during it. Somewhere free in any case.
     */
    public static Vec3 dismountPosition(AbstractGirlEntity girl, LivingEntity partner) {
        SceneType.Flow flow = SceneType.flowOfStage(girl.getCurrentAction());
        SceneType.Place type = flow == null ? null : flow.place();
        Vec3 forward = Vec3.directionFromRotation(0, girl.getYRot());
        Vec3 pos;
        if (type != null && type.liesDown()) {
            // she lies with her head towards the spot he stood on: back there
            pos = girl.position().add(forward.scale(type.forward()));
        } else if (type != null && type.alongBed()) {
            // she was put in the middle of the bed: he gets up on her side of it
            Vec3 left = new Vec3(forward.z, 0, -forward.x);
            pos = girl.position().add(left.scale(1.0));
        } else if (type != null && type.bed()) {
            pos = girl.position().subtract(forward.scale(type.forward())).subtract(0, type.up(), 0);
        } else {
            double ahead = type != null ? Math.max(type.partnerAhead(), 0.6) : PARTNER_DISTANCE;
            pos = girl.position().add(forward.scale(ahead + 0.2));
        }
        return girl.level().noCollision(partner, partner.getBoundingBox().move(pos.subtract(partner.position())))
                ? pos : girl.position().add(0, girl.getBbHeight(), 0);
    }

    /** The partner faces the way she does (behind her, as in doggy), not towards her. */
    public static boolean partnerBehind(GirlAction action) {
        SceneType.Flow type = SceneType.flowOfStage(action);
        return type != null && type.place().partnerAhead() <= 0.3;
    }

    /** Dev: the direction the scene is played in. */
    void faceAlong(float yaw) {
        sceneYaw = yaw;
    }

    private void faceTowards(Vec3 target) {
        Vec3 d = target.subtract(girl.position());
        sceneYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
        girl.setYRot(sceneYaw);
        girl.setYBodyRot(sceneYaw);
        girl.setYHeadRot(sceneYaw);
    }

    private void setProgress(float value) {
        progress = value;
        girl.setSceneProgress(value);
    }

    // ---- Helpers ----

    @Nullable
    private ServerPlayer partner() {
        if (girl.getPartnerUUID() == null || !(girl.level() instanceof ServerLevel level)) return null;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(girl.getPartnerUUID());
        if (p == null || !p.isAlive() || p.level() != girl.level() || p.distanceTo(girl) > LOST_DISTANCE) return null;
        return p;
    }

    /** Removes {@code cost} from the inventory if it's all there; otherwise removes nothing. */
    private static boolean takePayment(Inventory inv, ItemStack cost) {
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(cost.getItem())) have += s.getCount();
        }
        if (have < cost.getCount()) return false;
        int left = cost.getCount();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(cost.getItem())) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        inv.setChanged();
        return true;
    }

    private void say(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.literal("<").append(girl.getName()).append("> ")
                .append(Component.translatable("scene.velvet.msg." + key)));
    }
}
