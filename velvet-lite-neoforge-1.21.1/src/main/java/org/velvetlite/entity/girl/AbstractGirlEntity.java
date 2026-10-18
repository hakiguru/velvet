package org.velvetlite.entity.girl;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import org.velvetlite.item.NpcEditorWandItem;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.menu.GirlMenu;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.ai.GirlFollowGoal;
import org.velvetlite.entity.girl.ai.GirlGoHomeGoal;
import org.velvetlite.entity.girl.ai.GirlLookAtPlayerGoal;
import org.velvetlite.entity.girl.ai.GirlOpenDoorGoal;
import org.velvetlite.entity.girl.ai.GirlCollectItemsGoal;
import org.velvetlite.entity.girl.survival.GirlDigInGoal;
import org.velvetlite.entity.girl.survival.GirlFightGoal;
import org.velvetlite.entity.girl.survival.GirlFleeGoal;
import org.velvetlite.entity.girl.survival.GirlPillarGoal;
import org.velvetlite.entity.girl.survival.GirlStealthGoal;
import org.velvetlite.entity.girl.survival.SurvivalBrain;
import org.velvetlite.entity.girl.scene.GirlSceneController;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


public abstract class AbstractGirlEntity extends PathfinderMob implements GeoAnimatable, OwnableEntity {

    // ---- Synched data ----

    private static final EntityDataAccessor<String> DATA_GIRL_ID =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_PARTNER =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_ACTION =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODE =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_MODEL_VARIANT =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_CUSTOM_MODEL =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_CUSTOM_NAME =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_SHOULD_BE_AT_TARGET =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_TARGET_POS =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    /** Follows her owner around ("Follow me" in her menu); otherwise she stays or wanders. */
    private static final EntityDataAccessor<Boolean> DATA_FOLLOWING =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FOOD =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.INT);
    /** Bone changes from the NPC editor, see {@link BoneTweaks}. */
    private static final EntityDataAccessor<CompoundTag> DATA_BONE_TWEAKS =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** A custom texture from the editor, see {@link org.velvetlite.skin.GirlSkin}; empty for none. */
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    /** 0..1: how far the current scene is; it ends when this is full. */
    private static final EntityDataAccessor<Float> DATA_SCENE_PROGRESS =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Integer>[] DATA_SKILL_XP = defineSkillData();

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Integer>[] defineSkillData() {
        EntityDataAccessor<Integer>[] data = new EntityDataAccessor[GirlSkill.values().length];
        for (int i = 0; i < data.length; i++) {
            data[i] = SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.INT);
        }
        return data;
    }

    // ---- Non-synced state ----

    /** Model variants: geo/<type>/<type>.geo.json, <type>_dressed, <type>_armored. */
    public static final int VARIANT_NUDE = 0;
    public static final int VARIANT_DRESSED = 1;
    public static final int VARIANT_ARMORED = 2;

    private static final ResourceLocation ENDURANCE_BONUS = ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "endurance");
    private static final ResourceLocation AGILITY_BONUS = ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "agility");
    /** Agility experience: 1 point per this many blocks walked. */
    private static final double BLOCKS_PER_AGILITY_XP = 10.0;
    private double walkedSinceXp;

    /** Out-of-combat regeneration: 1 HP every this many ticks once not hurt for {@link #REGEN_DELAY}. */
    private static final int REGEN_INTERVAL = 100;
    private static final int REGEN_DELAY = 200;

    /**
     * Open inventory slots at each capacity level: 2 more per level at first, then 3, and 4 for the last
     * one, up to a chest's 27.
     */
    private static final int[] BAG_SLOTS_AT_LEVEL = {1, 3, 5, 7, 9, 11, 14, 17, 20, 23, 27};
    public static final int BAG_SIZE = BAG_SLOTS_AT_LEVEL[GirlSkill.MAX_LEVEL];
    /** How often she checks whether she's in danger (the threats themselves are watched by her survival brain). */
    private static final int DANGER_CHECK_INTERVAL = 10;
    /** Hurt within this many ticks counts as danger. */
    private static final int HURT_DANGER_TICKS = 100;
    /** She only waits for a player with her menu or editor open within this distance. */
    private static final double WAIT_FOR_VIEWER = 16;
    private static final ResourceLocation CROUCHING_SPEED = ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "crouching");
    /** Like a sneaking player: under a third of the speed. */
    private static final double CROUCHING_SLOWDOWN = -0.65;
    private static final float CROUCHING_HEIGHT = 1.5F / 1.8F;

    /**
     * Set by the spawn egg while it spawns her, so she belongs to the player who used it (the egg doesn't
     * hand the player on to the new entity).
     */
    public static final ThreadLocal<Player> SPAWNING_PLAYER = new ThreadLocal<>();

    protected final GirlType girlType;
    private final SimpleContainer bag = new SimpleContainer(BAG_SIZE);
    private final GirlNeeds needs = new GirlNeeds(this);
    private final SurvivalBrain survival = new SurvivalBrain(this);
    private final org.velvetlite.entity.girl.brain.GirlBrain brain = new org.velvetlite.entity.girl.brain.GirlBrain(this);
    private GirlBehavior behavior = new GirlBehavior();
    /** Server: players with her menu or her editor open; she waits for them, unless she's in danger. */
    private final Set<UUID> menuViewers = new HashSet<>();
    private boolean inDanger;
    /** When she was last hurt by anything but hunger. */
    private int lastHurtTick = -HURT_DANGER_TICKS;
    private BoneTweaks boneTweaks = BoneTweaks.NONE;
    @Nullable private CompoundTag boneTweaksTag;
    /** Client only: the editor's unsaved bone changes, drawn instead of the saved ones while it's open. */
    @Nullable public BoneTweaks clientPreviewTweaks;
    /** Client only: an animation the editor plays on its stand-in of her ("walk", "sit"...), instead of her own. */
    @Nullable public String clientPreviewAnimation;
    /** Client only: the bone picked in the editor, drawn tinted. */
    @Nullable public String clientSelectedBone;
    /**
     * Client only: where the partner's eyes are in the current frame (the "steve" head bone), written by
     * the renderer. Lets the riding partner's camera sit exactly in the model's head.
     */
    @Nullable public Vec3 clientPartnerEyes;
    /** Client only: the point between her and the partner's body in the current frame, for the side views. */
    @Nullable public Vec3 clientSceneFocus;
    /** Client only: smoothed horizontal speed in blocks per tick and the walk cycle chosen for it. */
    public double clientGroundSpeed;
    @Nullable public org.velvetlite.client.animation.GirlAnimationController.Gait clientGait;
    private final AnimatableInstanceCache animationCache = new SingletonAnimatableInstanceCache(this);
    private final GirlSceneController scene = new GirlSceneController(this);

    @Nullable private BlockPos homePos;

    // ---- Constructors ----

    protected AbstractGirlEntity(EntityType<? extends AbstractGirlEntity> type, Level level, GirlType girlType) {
        super(type, level);
        this.girlType = girlType;
        this.setPersistenceRequired();

        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanPassDoors(true);
        }
        this.getNavigation().setCanFloat(true);
        // Same preferences as villagers: walk around hazards, only swim when there's no other way
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_OTHER, 8.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, 8.0F);
        this.setPathfindingMalus(PathType.WATER, 8.0F);

        // everything the player gave her comes back when she dies
        for (EquipmentSlot slot : EquipmentSlot.values()) this.setDropChance(slot, 2.0F);
    }

    // ---- Registration ----

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                // goal speed modifiers are multiplied with this; 0.25 is about a zombie's pace
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                // a bare fist, like the player's; also what Mob compares weapons by when she picks one up
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                // the player's: weapons bring their own modifiers, a sword ends up at 1.6 hits a second
                .add(Attributes.ATTACK_SPEED, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // Empty by default: a random default would never be synced to the client (only non-default
        // values are sent), leaving client and server with different ids.
        builder.define(DATA_GIRL_ID,         "");
        builder.define(DATA_OWNER,           Optional.empty());
        builder.define(DATA_PARTNER,         Optional.empty());
        builder.define(DATA_ACTION,          GirlAction.NULL.name());
        builder.define(DATA_MODE,            GirlMode.WALK.name());
        builder.define(DATA_MODEL_VARIANT,   VARIANT_DRESSED);
        builder.define(DATA_CUSTOM_MODEL,    "");
        builder.define(DATA_CUSTOM_NAME,     "");
        builder.define(DATA_SHOULD_BE_AT_TARGET, false);
        builder.define(DATA_TARGET_POS,      "0|0|0");
        builder.define(DATA_SCENE_PROGRESS,  0.0F);
        builder.define(DATA_FOLLOWING,       false);
        builder.define(DATA_FOOD,            GirlNeeds.MAX_FOOD);
        builder.define(DATA_BONE_TWEAKS,     new CompoundTag());
        builder.define(DATA_SKIN,            "");
        for (EntityDataAccessor<Integer> skill : DATA_SKILL_XP) builder.define(skill, 0);
    }

    @Override
    protected void registerGoals() {
        // what to do about danger is up to her survival brain; these carry it out
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GirlDigInGoal(this));
        this.goalSelector.addGoal(1, new GirlPillarGoal(this));
        this.goalSelector.addGoal(2, new GirlFleeGoal(this));
        this.goalSelector.addGoal(2, new GirlFightGoal(this));
        // hurt with her instincts switched off
        this.goalSelector.addGoal(3, new PanicGoal(this, 1.5) {
            @Override
            public boolean canUse() {
                return survival.tactic() == SurvivalBrain.Tactic.NONE && super.canUse();
            }
        });
        this.goalSelector.addGoal(3, new GirlStealthGoal(this));
        this.goalSelector.addGoal(4, new GirlGoHomeGoal(this, 1.0));
        this.goalSelector.addGoal(5, new GirlFollowGoal(this, 1.0));
        this.goalSelector.addGoal(6, new GirlCollectItemsGoal(this));
        this.goalSelector.addGoal(6, new TemptGoal(this, 1.0, Ingredient.of(
                net.minecraft.world.item.Items.GOLD_INGOT,
                net.minecraft.world.item.Items.GOLD_BLOCK), false));
        this.goalSelector.addGoal(7, new GirlOpenDoorGoal(this, true));
        this.goalSelector.addGoal(8, new GirlStrollGoal(this, 0.6));
        this.goalSelector.addGoal(9, new GirlLookAtPlayerGoal(this, Player.class, 6.0f, 0.05f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // ---- Tick ----

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            scene.tick();
            double moved = this.isPassenger() ? 0 : Math.sqrt(Mth.square(getX() - xo) + Mth.square(getZ() - zo));
            tickSkills(moved);
            if (this.isAlive()) {
                needs.tick(moved);
                survival.tick();
                brain.tick();
            }
            if (this.tickCount % DANGER_CHECK_INTERVAL == 0) updateDanger();
            // where she is and how she looks, for her owner's editor
            if (this.tickCount % 200 == 0 && getOwnerUUID() != null && this.getServer() != null) {
                OwnedGirls.get(this.getServer()).update(this);
            }
        } else {
            double moved = Math.sqrt(Mth.square(getX() - xo) + Mth.square(getZ() - zo));
            // positions arrive from the server in uneven steps: average over about half a second
            clientGroundSpeed += (moved - clientGroundSpeed) * 0.15;
        }
    }

    @Override
    protected void customServerAiStep() {
        // During an action the scene controller moves and turns the girl (navigation still ticks);
        // JUMP stays enabled so FloatGoal keeps her afloat. With her menu open she waits for the player
        boolean busy = getCurrentAction().isBusy();
        Player viewer = menuViewer();
        boolean waiting = !busy && viewer != null && !inDanger && viewer.distanceTo(this) < WAIT_FOR_VIEWER;
        for (Goal.Flag flag : EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)) {
            if (busy || waiting) this.goalSelector.disableControlFlag(flag);
            else this.goalSelector.enableControlFlag(flag);
        }
        super.customServerAiStep();
        if (waiting) {
            this.getNavigation().stop();
            this.getLookControl().setLookAt(viewer, 30.0F, 30.0F);
        }

        if (this.tickCount % REGEN_INTERVAL == 0 && this.getHealth() < this.getMaxHealth() && getFood() >= GirlNeeds.REGEN_FOOD
                && this.tickCount - this.getLastHurtByMobTimestamp() > REGEN_DELAY && this.hurtTime == 0) {
            this.heal(1.0F);
            needs.addExhaustion(GirlNeeds.EXHAUSTION_PER_HEAL);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isAlive() && !getCurrentAction().isBusy() && behavior.pickUpItems) {
            for (ItemEntity item : this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.0, 0.5, 1.0))) {
                if (!item.isRemoved() && !item.getItem().isEmpty() && !item.hasPickUpDelay()) GirlLoot.pickUp(this, item);
            }
        }
    }

    /** Mob's rules for whether a weapon or armor piece is an upgrade. */
    public boolean isBetterGear(ItemStack candidate, ItemStack current) {
        return canReplaceCurrentItem(candidate, current);
    }

    // ---- Danger and open menus ----

    /** Hunted by monsters, hurt a moment ago, or burning. */
    public boolean isInDanger() {
        return inDanger;
    }

    private void updateDanger() {
        boolean hurt = this.tickCount - lastHurtTick < HURT_DANGER_TICKS;
        boolean danger = this.isOnFire() || hurt || survival.inDanger();
        if (danger && !inDanger) closeMenus(); // the only reason to walk away from a player with her menu open
        inDanger = danger;
    }

    // ---- Crouching ----

    /**
     * Crouches like a sneaking player: the game treats her as sneaking (no vibrations for sculk and the warden,
     * a hidden name tag), she's smaller and much slower.
     */
    public void setCrouching(boolean crouching) {
        if (crouching == this.isShiftKeyDown()) return;
        this.setShiftKeyDown(crouching);
        if (!this.isSleeping() && !this.isSwimming() && !this.isDeadOrDying()) this.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (crouching) speed.addOrUpdateTransientModifier(new AttributeModifier(CROUCHING_SPEED, CROUCHING_SLOWDOWN,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else speed.removeModifier(CROUCHING_SPEED);
    }

    @Override
    protected @NotNull EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        return pose == Pose.CROUCHING ? dimensions.scale(1, CROUCHING_HEIGHT) : dimensions;
    }

    public SurvivalBrain survival() {
        return survival;
    }

    public org.velvetlite.entity.girl.brain.GirlBrain brain() {
        return brain;
    }

    public void addMenuViewer(Player player) {
        menuViewers.add(player.getUUID());
    }

    public void removeMenuViewer(Player player) {
        menuViewers.remove(player.getUUID());
    }

    @Nullable
    private Player menuViewer() {
        if (menuViewers.isEmpty()) return null;
        // gone, or looking at another menu by now (a close the menu didn't hear about)
        menuViewers.removeIf(id -> !(this.level().getPlayerByUUID(id) instanceof Player p)
                || !(p.containerMenu instanceof GirlMenu m && m.getGirl() == this
                     || p.containerMenu instanceof GirlEditorMenu e && e.getGirl() == this));
        return menuViewers.isEmpty() ? null : this.level().getPlayerByUUID(menuViewers.iterator().next());
    }

    private void closeMenus() {
        for (UUID id : List.copyOf(menuViewers)) {
            if (this.level().getPlayerByUUID(id) instanceof ServerPlayer player) player.closeContainer();
        }
        menuViewers.clear();
    }

    // ---- Synched accessors ----

    public UUID getGirlUUID() {
        String id = this.entityData.get(DATA_GIRL_ID);
        return id.isEmpty() ? this.getUUID() : UUID.fromString(id);
    }

    public void setGirlUUID(UUID id) {
        this.entityData.set(DATA_GIRL_ID, id.toString());
    }

    @Override
    public @Nullable UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
    }

    /** The player taking part in the current action, if any. */
    public @Nullable UUID getPartnerUUID() {
        return this.entityData.get(DATA_PARTNER).orElse(null);
    }

    public void setPartnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_PARTNER, Optional.ofNullable(uuid));
    }

    public GirlAction getCurrentAction() {
        return GirlAction.byName(this.entityData.get(DATA_ACTION));
    }

    /** Only the scene controller should call this; it keeps stage timing in sync. */
    public void setCurrentAction(GirlAction action) {
        this.entityData.set(DATA_ACTION, action.name());
    }

    public float getSceneProgress() {
        return this.entityData.get(DATA_SCENE_PROGRESS);
    }

    /** Only the scene controller should call this. */
    public void setSceneProgress(float progress) {
        this.entityData.set(DATA_SCENE_PROGRESS, progress);
    }

    // ---- Skills ----

    public int getSkillXp(GirlSkill skill) {
        return this.entityData.get(DATA_SKILL_XP[skill.ordinal()]);
    }

    public int getSkillLevel(GirlSkill skill) {
        return GirlSkill.levelFor(getSkillXp(skill));
    }

    /** Open inventory slots at her capacity level: from 1 at level 0 to 27 at the last one. */
    public int getBagCapacity() {
        return bagSlotsAt(getSkillLevel(GirlSkill.CAPACITY));
    }

    public static int bagSlotsAt(int capacityLevel) {
        return BAG_SLOTS_AT_LEVEL[Math.clamp(capacityLevel, 0, GirlSkill.MAX_LEVEL)];
    }

    /** The capacity level that opens inventory slot {@code index}. */
    public static int levelOpeningSlot(int index) {
        int level = 0;
        while (level < GirlSkill.MAX_LEVEL && bagSlotsAt(level) <= index) level++;
        return level;
    }

    /** Server side; applies the level bonuses when a level is reached. */
    public void addSkillXp(GirlSkill skill, int amount) {
        if (amount <= 0 || this.level().isClientSide) return;
        int before = getSkillLevel(skill);
        int cap = GirlSkill.xpForLevel(GirlSkill.MAX_LEVEL);
        this.entityData.set(DATA_SKILL_XP[skill.ordinal()], Math.min(cap, getSkillXp(skill) + amount));
        if (getSkillLevel(skill) != before) applySkillBonuses();
    }

    private void applySkillBonuses() {
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrUpdateTransientModifier(new AttributeModifier(ENDURANCE_BONUS,
                    2.0 * getSkillLevel(GirlSkill.ENDURANCE), AttributeModifier.Operation.ADD_VALUE));
            if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
        }
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(AGILITY_BONUS,
                    0.03 * getSkillLevel(GirlSkill.AGILITY), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private void tickSkills(double moved) {
        if (!getCurrentAction().isBusy()) {
            walkedSinceXp += moved;
            if (walkedSinceXp >= BLOCKS_PER_AGILITY_XP) {
                walkedSinceXp -= BLOCKS_PER_AGILITY_XP;
                addSkillXp(GirlSkill.AGILITY, 1);
            }
        }
        if (this.tickCount % 1200 == 0 && getOwnerUUID() != null) {
            Player owner = this.level().getPlayerByUUID(getOwnerUUID());
            if (owner != null && owner.distanceToSqr(this) < 16 * 16) addSkillXp(GirlSkill.AFFECTION, 2);
        }
    }

    public GirlSceneController scene() {
        return scene;
    }

    public GirlNeeds needs() {
        return needs;
    }

    public GirlBehavior behavior() {
        return behavior;
    }

    public void setBehavior(GirlBehavior behavior) {
        this.behavior = behavior;
    }

    public boolean isFollowing() {
        return this.entityData.get(DATA_FOLLOWING);
    }

    public void setFollowing(boolean following) {
        this.entityData.set(DATA_FOLLOWING, following);
    }

    /** Food points, 0..{@link GirlNeeds#MAX_FOOD}; synced for the menu and the icons above her head. */
    public int getFood() {
        return this.entityData.get(DATA_FOOD);
    }

    public void setFood(int food) {
        this.entityData.set(DATA_FOOD, food);
    }

    /** The saved bone changes (parsed once per sync). */
    public BoneTweaks getBoneTweaks() {
        CompoundTag tag = this.entityData.get(DATA_BONE_TWEAKS);
        if (tag != boneTweaksTag) {
            boneTweaksTag = tag;
            boneTweaks = BoneTweaks.fromTag(tag);
        }
        return boneTweaks;
    }

    public void setBoneTweaks(BoneTweaks tweaks) {
        this.entityData.set(DATA_BONE_TWEAKS, tweaks.toTag());
    }

    @Nullable
    public org.velvetlite.skin.GirlSkin getSkin() {
        String skin = this.entityData.get(DATA_SKIN);
        return skin.isEmpty() ? null : org.velvetlite.skin.GirlSkin.decode(skin);
    }

    public void setSkin(@Nullable org.velvetlite.skin.GirlSkin skin) {
        this.entityData.set(DATA_SKIN, skin == null ? "" : skin.encode());
    }

    /**
     * How she looks, for the editor's NPC list and its model view when she's far away: model variant, custom
     * model, bone changes, skin, name and what she wears and holds. Also her health, food and home.
     */
    public CompoundTag appearance() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", getCustomNameString());
        tag.putInt("model_variant", getModelVariant());
        tag.putString("custom_model", getCustomModelCode());
        tag.put("bone_tweaks", getBoneTweaks().toTag());
        tag.putString("skin", this.entityData.get(DATA_SKIN));
        net.minecraft.nbt.ListTag gear = new net.minecraft.nbt.ListTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            gear.add(getItemBySlot(slot).saveOptional(this.registryAccess()));
        }
        tag.put("equipment", gear);
        tag.putFloat("health", getHealth());
        tag.putFloat("max_health", getMaxHealth());
        tag.putInt("food", getFood());
        if (homePos != null) tag.putLong("home", homePos.asLong());
        return tag;
    }

    /** Client side, for a stand-in of her in the editor: takes over how she looks. */
    public void applyAppearance(CompoundTag tag) {
        setCustomNameString(tag.getString("name"));
        setModelVariant(tag.getInt("model_variant"));
        setCustomModelCode(tag.getString("custom_model"));
        setBoneTweaks(BoneTweaks.fromTag(tag.getCompound("bone_tweaks")));
        this.entityData.set(DATA_SKIN, tag.getString("skin"));
        net.minecraft.nbt.ListTag gear = tag.getList("equipment", Tag.TAG_COMPOUND);
        int i = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            if (i < gear.size()) setItemSlot(slot, ItemStack.parseOptional(this.registryAccess(), gear.getCompound(i)));
            i++;
        }
        setFood(tag.getInt("food"));
    }

    public GirlMode getMode() {
        return GirlMode.valueOf(this.entityData.get(DATA_MODE));
    }

    public void setMode(GirlMode mode) {
        this.entityData.set(DATA_MODE, mode.name());
    }

    public int getModelVariant() {
        return this.entityData.get(DATA_MODEL_VARIANT);
    }

    public void setModelVariant(int variant) {
        this.entityData.set(DATA_MODEL_VARIANT, variant);
    }

    public String getCustomModelCode() {
        return this.entityData.get(DATA_CUSTOM_MODEL);
    }

    public void setCustomModelCode(String code) {
        this.entityData.set(DATA_CUSTOM_MODEL, code == null ? "" : code);
    }

    public String getCustomNameString() {
        return this.entityData.get(DATA_CUSTOM_NAME);
    }

    public void setCustomNameString(String name) {
        this.entityData.set(DATA_CUSTOM_NAME, name == null ? "" : name);
    }

    public boolean shouldBeAtTarget() {
        return this.entityData.get(DATA_SHOULD_BE_AT_TARGET);
    }

    public void setShouldBeAtTarget(boolean value) {
        this.entityData.set(DATA_SHOULD_BE_AT_TARGET, value);
    }

    public Vec3 getTargetVec() {
        String[] parts = this.entityData.get(DATA_TARGET_POS).split("\\|");
        return new Vec3(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]));
    }

    public void setTargetVec(Vec3 vec) {
        this.entityData.set(DATA_TARGET_POS, vec.x + "|" + vec.y + "|" + vec.z);
    }

    // ---- Home ----

    @Nullable
    public BlockPos getHomePos() { return homePos; }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    // ---- NBT ----

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("girl_id", getGirlUUID().toString());
        UUID owner = getOwnerUUID();
        if (owner != null) tag.putString("owner", owner.toString());
        tag.putBoolean("following", isFollowing());
        needs.save(tag);
        survival.save(tag);
        brain.save(tag);
        tag.putString("behavior", behavior.toJson());
        tag.put("bone_tweaks", getBoneTweaks().toTag());
        tag.putString("skin", this.entityData.get(DATA_SKIN));
        // scenes aren't saved: after a reload the partner is gone anyway
        tag.putString("mode", getMode().name());
        tag.putInt("model_variant", getModelVariant());
        tag.putString("custom_model", getCustomModelCode());
        tag.putString("custom_name", getCustomNameString());
        tag.put("bag", bag.createTag(this.registryAccess()));
        CompoundTag skills = new CompoundTag();
        for (GirlSkill skill : GirlSkill.values()) skills.putInt(skill.name().toLowerCase(), getSkillXp(skill));
        tag.put("skills", skills);
        if (homePos != null) {
            tag.putInt("home_x", homePos.getX());
            tag.putInt("home_y", homePos.getY());
            tag.putInt("home_z", homePos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("girl_id"))    setGirlUUID(UUID.fromString(tag.getString("girl_id")));
        if (tag.contains("owner"))      setOwnerUUID(UUID.fromString(tag.getString("owner")));
        // before 1.2 having an owner meant following him
        setFollowing(tag.contains("following") ? tag.getBoolean("following") : tag.contains("owner"));
        needs.load(tag);
        survival.load(tag);
        brain.load(tag);
        if (tag.contains("behavior")) behavior = GirlBehavior.fromJsonOrDefault(tag.getString("behavior"));
        if (tag.contains("bone_tweaks")) setBoneTweaks(BoneTweaks.fromTag(tag.getCompound("bone_tweaks")));
        if (tag.contains("skin")) this.entityData.set(DATA_SKIN, tag.getString("skin"));
        if (tag.contains("mode"))       setMode(GirlMode.valueOf(tag.getString("mode")));
        if (tag.contains("model_variant")) setModelVariant(tag.getInt("model_variant"));
        if (tag.contains("custom_model")) setCustomModelCode(tag.getString("custom_model"));
        if (tag.contains("custom_name"))  setCustomNameString(tag.getString("custom_name"));
        if (tag.contains("bag")) bag.fromTag(tag.getList("bag", Tag.TAG_COMPOUND), this.registryAccess());
        CompoundTag skills = tag.getCompound("skills");
        for (GirlSkill skill : GirlSkill.values()) {
            this.entityData.set(DATA_SKILL_XP[skill.ordinal()], skills.getInt(skill.name().toLowerCase()));
        }
        applySkillBonuses();
        if (tag.contains("home_x")) {
            homePos = new BlockPos(tag.getInt("home_x"), tag.getInt("home_y"), tag.getInt("home_z"));
        }
    }

    public SimpleContainer getBag() {
        return bag;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : bag.removeAllItems()) this.spawnAtLocation(stack);
    }

    // ---- Interaction ----

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, @NotNull InteractionHand hand) {
        // the editor wand has its own use on her
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || getCurrentAction().isBusy()
                || player.getItemInHand(hand).getItem() instanceof NpcEditorWandItem) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer serverPlayer) openMenu(serverPlayer);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public void openMenu(ServerPlayer player) {
        if (getCurrentAction().isBusy() || !GirlManager.canCommand(player, this)) return;
        claimBy(player);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GirlMenu(id, inv, this), getName()),
                buf -> buf.writeVarInt(this.getId()));
    }

    public void openEditor(ServerPlayer player) {
        GirlEditorMenu.open(player, this);
    }

    /** Every girl belongs to someone: one without an owner (summoned, from older saves) to whoever deals with her first. */
    public void claimBy(Player player) {
        if (getOwnerUUID() != null) return;
        setOwnerUUID(player.getUUID());
        if (this.getServer() != null) OwnedGirls.get(this.getServer()).update(this);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                                  @NotNull MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        Player spawner = SPAWNING_PLAYER.get();
        if (spawnType == MobSpawnType.SPAWN_EGG && spawner != null) setOwnerUUID(spawner.getUUID());
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    // ---- Riding: the scene partner is a passenger, so the girl positions him every tick ----

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().isEmpty() && passenger.getUUID().equals(getPartnerUUID());
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull Entity.MoveFunction callback) {
        if (!passenger.getUUID().equals(getPartnerUUID())) {
            super.positionRider(passenger, callback);
            return;
        }
        Vec3 pos;
        if (this.level().isClientSide && clientPartnerEyes != null) {
            // eyes exactly in the animated head of the "steve" bone
            pos = clientPartnerEyes.subtract(0, passenger.getEyeHeight(), 0);
        } else {
            pos = GirlSceneController.partnerAnchor(this);
        }
        callback.accept(passenger, pos.x, pos.y, pos.z);
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        return GirlSceneController.dismountPosition(this, passenger);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Random wandering, only while she isn't following anyone, has no home order and nothing to do. */
    private static final class GirlStrollGoal extends WaterAvoidingRandomStrollGoal {
        private final AbstractGirlEntity girl;

        GirlStrollGoal(AbstractGirlEntity girl, double speed) {
            super(girl, speed);
            this.girl = girl;
        }

        @Override
        public boolean canUse() {
            return girl.behavior().wander && !girl.isFollowing() && !girl.shouldBeAtTarget() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return girl.behavior().wander && !girl.isFollowing() && !girl.shouldBeAtTarget() && super.canContinueToUse();
        }
    }

    @Override
    public boolean isPushable() {
        return !getCurrentAction().isBusy();
    }

    /** During a scene she doesn't shove anyone either: in doggy the partner has to step onto her spot. */
    @Override
    protected void pushEntities() {
        if (!getCurrentAction().isBusy()) super.pushEntities();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // Owned girls can't be hurt by players (no accidental hits, no griefing), but mobs, fire and
        // falls still hurt them, so panic and fleeing matter
        if (getOwnerUUID() != null && source.getEntity() instanceof Player) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            if (getCurrentAction().isBusy()) scene.stop();
            // starving doesn't toughen her up
            if (this.isAlive() && !source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)) {
                lastHurtTick = this.tickCount;
                addSkillXp(GirlSkill.ENDURANCE, Math.max(1, Math.round(amount * 3)));
            }
        }
        return hurt;
    }

    @Override
    public void die(@NotNull DamageSource source) {
        if (!this.level().isClientSide) scene.stop();
        super.die(source);
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new org.velvetlite.client.animation.GaitAnimationController(
                this, "movement", 5, org.velvetlite.client.animation.GirlAnimationController::movementPredicate)
                .setSoundKeyframeHandler(org.velvetlite.client.animation.GirlAnimationController::ignoreKeyframe));
        controllers.add(new AnimationController<>(
                this, "action", 2, org.velvetlite.client.animation.GirlAnimationController::actionPredicate)
                .setSoundKeyframeHandler(org.velvetlite.client.animation.GirlAnimationController::onSoundKeyframe));
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public double getTick(Object entity) {
        return this.tickCount;
    }

    // ---- Helpers ----

    public GirlType getGirlType() {
        return this.girlType;
    }

    @Override
    public @NotNull Component getName() {
        String custom = getCustomNameString();
        return custom.isEmpty() ? this.getType().getDescription() : Component.literal(custom);
    }

    public ResourceLocation getTextureLocation() {
        return ResourceLocation.fromNamespaceAndPath(
                VelvetLite.MODID, "textures/entity/" + girlType.id() + "/" + girlType.id() + ".png");
    }
}
