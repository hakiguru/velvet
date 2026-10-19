package org.velvet.entity.girl;

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
import org.velvet.item.NpcEditorWandItem;
import org.velvet.menu.GirlEditorMenu;
import org.velvet.menu.GirlMenu;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.entity.girl.psyche.Psyche;
import org.velvet.entity.girl.ai.GirlFollowGoal;
import org.velvet.entity.girl.ai.GirlGoHomeGoal;
import org.velvet.entity.girl.ai.GirlLookAtPlayerGoal;
import org.velvet.entity.girl.ai.GirlOpenDoorGoal;
import org.velvet.entity.girl.ai.GirlCollectItemsGoal;
import org.velvet.entity.girl.survival.GirlDigInGoal;
import org.velvet.entity.girl.survival.GirlFightGoal;
import org.velvet.entity.girl.survival.GirlFleeGoal;
import org.velvet.entity.girl.survival.GirlPillarGoal;
import org.velvet.entity.girl.survival.GirlStealthGoal;
import org.velvet.entity.girl.survival.SurvivalBrain;
import org.velvet.entity.girl.scene.GirlSceneController;
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
    /** A custom texture from the editor, see {@link org.velvet.skin.GirlSkin}; empty for none. */
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);
    /** 0..1: how far the current scene is; it ends when this is full. */
    private static final EntityDataAccessor<Float> DATA_SCENE_PROGRESS =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.FLOAT);
    /** Her model preset ({@link GirlType} id); empty for the default of her entity type. */
    private static final EntityDataAccessor<String> DATA_PRESET =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.STRING);

    /** Her emotions in tenths, 4 bits each (see {@link Psyche#packEmotions}). */
    private static final EntityDataAccessor<Integer> DATA_EMOTIONS =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.INT);
    /** Adrenaline in tenths. */
    private static final EntityDataAccessor<Byte> DATA_ADRENALINE =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.BYTE);
    /** How she shows a strong emotion right now ({@link Psyche.Emote}). */
    private static final EntityDataAccessor<Byte> DATA_EMOTE =
            SynchedEntityData.defineId(AbstractGirlEntity.class, EntityDataSerializers.BYTE);

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

    private static final ResourceLocation ENDURANCE_BONUS = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "endurance");
    private static final ResourceLocation AGILITY_BONUS = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "agility");
    /** Agility experience: 1 point per this many blocks walked. */
    private static final double BLOCKS_PER_AGILITY_XP = 10.0;
    private double walkedSinceXp;


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
    private static final ResourceLocation CROUCHING_SPEED = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "crouching");
    private static final ResourceLocation ADRENALINE_SPEED = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "adrenaline");
    /**
     * Like a sneaking player: about a third of her walking pace. A mob's ground speed grows with the square
     * of its speed attribute, so 0.7 of it gives about half of the pace, and sneaking she goes at a slow walk
     * besides: about 1.3 blocks a second, a sneaking player's speed.
     */
    private static final double CROUCHING_SLOWDOWN = -0.3;
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
    private final org.velvet.entity.girl.brain.GirlBrain brain = new org.velvet.entity.girl.brain.GirlBrain(this);
    /** Server: her character, emotions and adrenaline. */
    private final Psyche psyche = new Psyche(this);
    private GirlBehavior behavior = new GirlBehavior();
    /** Server: her instincts and debug switches, from her own file (see {@link NpcSettings}). */
    private NpcSettings settings = new NpcSettings();
    private boolean settingsLoaded;
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
    /**
     * Client only: the scene the editor fits to her (model tab, "Scene"): its fit is drawn and the partner shows
     * (with the player's skin), playing whatever pose the editor picked.
     */
    @Nullable public org.velvet.entity.girl.scene.SceneType clientPreviewScene;
    /** Client only: when her last swing started (her tick) and how many there were, for the attack animations. */
    public int clientSwingAt = -100, clientSwings;
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
    @Nullable public org.velvet.client.animation.GirlAnimationController.Gait clientGait;
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
                // how far her paths reach (and what she notices is up to her senses)
                .add(Attributes.FOLLOW_RANGE, 48.0)
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
        builder.define(DATA_PRESET,          "");
        builder.define(DATA_EMOTIONS,        0);
        builder.define(DATA_ADRENALINE,      (byte) 0);
        builder.define(DATA_EMOTE,           (byte) 0);
        for (EntityDataAccessor<Integer> skill : DATA_SKILL_XP) builder.define(skill, 0);
    }

    @Override
    protected void registerGoals() {
        // what to do about danger is up to her survival brain; these carry it out
        // out of lava (or water she's stuck in) before anything else, swimming included
        this.goalSelector.addGoal(0, new org.velvet.entity.girl.ai.GirlEscapeFluidGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GirlDigInGoal(this));
        this.goalSelector.addGoal(1, new GirlPillarGoal(this));
        this.goalSelector.addGoal(1, new org.velvet.entity.girl.survival.GirlShelterGoal(this));
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
        this.goalSelector.addGoal(6, new org.velvet.entity.girl.ai.GirlMineGoal(this));
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
            if (!settingsLoaded) loadSettings();
            scene.tick();
            double moved = this.isPassenger() ? 0 : Math.sqrt(Mth.square(getX() - xo) + Mth.square(getZ() - zo));
            tickSkills(moved);
            if (this.isAlive()) {
                needs.tick(moved);
                // with her AI switched off (debug tool) she only stands there
                if (settings.ai) {
                    psyche.tick();
                    survival.tick();
                    brain.tick();
                }
                if (this.tickCount % Psyche.UPDATE_INTERVAL == 0) syncPsyche();
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
            org.velvet.client.EmoteEffects.tick(this);
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

        int heal = needs.healInterval();
        if (heal > 0 && this.tickCount % heal == 0 && this.getHealth() < this.getMaxHealth() && this.hurtTime == 0) {
            this.heal(1.0F);
            needs.addExhaustion(GirlNeeds.EXHAUSTION_PER_HEAL);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.tickCount % 100 == 0) emptyClosedSlots();
        if (!this.level().isClientSide && this.isAlive() && !getCurrentAction().isBusy() && behavior.pickUpItems) {
            for (ItemEntity item : this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.0, 0.5, 1.0))) {
                if (!item.isRemoved() && !item.getItem().isEmpty() && !item.hasPickUpDelay()) GirlLoot.pickUp(this, item);
            }
        }
    }

    /**
     * Items left in inventory slots her capacity doesn't open (from older versions): into the open ones, or
     * dropped if there's no room. Her menu only shows the open slots.
     */
    private void emptyClosedSlots() {
        for (int i = getBagCapacity(); i < BAG_SIZE; i++) {
            ItemStack stack = bag.getItem(i);
            if (stack.isEmpty()) continue;
            bag.setItem(i, ItemStack.EMPTY);
            ItemStack rest = GirlLoot.addToBag(this, stack);
            if (!rest.isEmpty()) this.spawnAtLocation(rest);
        }
    }

    /**
     * A hit the way a player lands one: the weapon's damage with its enchantments, half again as much for a
     * critical hit (falling, from a jump), knockback, the weapon's after-hit effects (fire, ...) and one point
     * of wear on it. True if it hurt.
     */
    public boolean strike(LivingEntity target, boolean critical) {
        this.swing(InteractionHand.MAIN_HAND);
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource source = this.damageSources().mobAttack(this);
        ItemStack weapon = this.getMainHandItem();
        if (this.level() instanceof ServerLevel server) {
            damage = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(server, weapon, target, source, damage);
        }
        if (critical) damage *= 1.5F;
        boolean hurt = target.hurt(source, damage);
        if (!hurt) return false;
        float knockback = this.getKnockback(target, source) + (this.isSprinting() ? 1 : 0);
        if (knockback > 0) {
            target.knockback(knockback * 0.5F, Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(this.getYRot() * Mth.DEG_TO_RAD));
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
        }
        if (this.level() instanceof ServerLevel server) {
            net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffects(server, target, source);
            if (critical) server.getChunkSource().broadcastAndSend(this, new net.minecraft.network.protocol.game.ClientboundAnimatePacket(target, 4));
        }
        if (!weapon.isEmpty()) weapon.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
        this.setLastHurtMob(target);
        return true;
    }

    /** Like a player eating: the food goes into her, on top of the sound and effects. */
    @Override
    public @NotNull ItemStack eat(@NotNull Level level, @NotNull ItemStack food, @NotNull net.minecraft.world.food.FoodProperties props) {
        if (!level.isClientSide) {
            needs.feed(props);
            psyche.onAte(food);
        }
        return super.eat(level, food, props);
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

    /** A player's width and her preset's height; crouching, lower. */
    @Override
    protected @NotNull EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        EntityDimensions base = super.getDefaultDimensions(pose);
        if (girlType == null) return base; // asked while still being constructed
        EntityDimensions dimensions = base.scale(1, getGirlType().height() / base.height());
        return pose == Pose.CROUCHING ? dimensions.scale(1, CROUCHING_HEIGHT) : dimensions;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PRESET.equals(key)) refreshDimensions();
    }

    public SurvivalBrain survival() {
        return survival;
    }

    public Psyche psyche() {
        return psyche;
    }

    private void syncPsyche() {
        // the rush: she's quicker on her feet the more adrenaline she has
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (psyche.adrenaline() > 0.05F) speed.addOrUpdateTransientModifier(new AttributeModifier(ADRENALINE_SPEED,
                    0.15 * psyche.adrenaline(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else speed.removeModifier(ADRENALINE_SPEED);
        }
        this.entityData.set(DATA_EMOTIONS, psyche.packEmotions());
        this.entityData.set(DATA_ADRENALINE, (byte) Math.round(psyche.adrenaline() * 10));
        this.entityData.set(DATA_EMOTE, (byte) psyche.emote().ordinal());
    }

    /** Her emotion in tenths, on both sides (synced). */
    public float getEmotion(org.velvet.entity.girl.psyche.Emotion e) {
        return Psyche.unpack(this.entityData.get(DATA_EMOTIONS), e);
    }

    public float getAdrenaline() {
        return this.entityData.get(DATA_ADRENALINE) / 10.0F;
    }

    public Psyche.Emote getEmote() {
        int i = this.entityData.get(DATA_EMOTE);
        return i >= 0 && i < Psyche.Emote.values().length ? Psyche.Emote.values()[i] : Psyche.Emote.NONE;
    }

    public org.velvet.entity.girl.brain.GirlBrain brain() {
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
        if (getSkillLevel(skill) != before) {
            applySkillBonuses();
            psyche.onLevelUp();
        }
    }

    /** Sets the skill to the start of {@code level} (the debug tool's starting levels). */
    public void setSkillLevel(GirlSkill skill, int level) {
        if (this.level().isClientSide) return;
        this.entityData.set(DATA_SKILL_XP[skill.ordinal()], GirlSkill.xpForLevel(Math.clamp(level, 0, GirlSkill.MAX_LEVEL)));
        applySkillBonuses();
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

    /** Until when her owner told her to mine (ores) or gather blocks, game time; see the mining goal. */
    private long miningOrderUntil;
    private boolean miningOrderOres;

    public void orderMining(boolean ores, int ticks) {
        miningOrderUntil = this.level().getGameTime() + ticks;
        miningOrderOres = ores;
    }

    /** Told to mine: 1 for ores, 2 for blocks to build with, 0 for no order. */
    public int miningOrder() {
        if (this.level().getGameTime() > miningOrderUntil) return 0;
        return miningOrderOres ? 1 : 2;
    }

    public NpcSettings settings() {
        return settings;
    }

    /** New settings (from the debug tool): applied and written to her file. */
    public void setSettings(NpcSettings settings) {
        this.settings = settings;
        settingsLoaded = true;
        applySettings();
        writeSettings();
    }

    /** Her settings file, with what else the debug editor sets copied in (name, levels, character). */
    public void writeSettings() {
        if (this.getServer() == null) return;
        settings.name = getCustomNameString();
        settings.preset = getGirlType().id();
        for (GirlSkill skill : GirlSkill.values()) settings.levels[skill.ordinal()] = getSkillLevel(skill);
        settings.personality = psyche.personality().toJson();
        settings.write(this.getServer(), getGirlUUID());
    }

    /** Her file, read once she's in the world (her id is known by then); written if she has none yet. */
    private void loadSettings() {
        settingsLoaded = true;
        if (this.getServer() == null) return;
        // every NPC has a name of her own
        if (getCustomNameString().isBlank() && getGirlType() != GirlType.UNKNOWN) {
            setCustomNameString(Names.freeName(this.getServer(), getGirlType(), getGirlUUID()));
        }
        NpcSettings file = NpcSettings.read(this.getServer(), getGirlUUID());
        if (file != null) {
            settings = file;
        } else if (behavior.legacySurvival != null) {
            settings.readSurvival(behavior.legacySurvival);
        }
        behavior.legacySurvival = null;
        applySettings();
        // her character and how she felt: from her personality file if there is one (it may have been
        // edited by hand), else she keeps what was saved with her or what she was born with
        com.google.gson.JsonObject mind = org.velvet.entity.girl.psyche.PsycheStore.read(getGirlUUID());
        if (mind != null) psyche.readJson(mind);
        else org.velvet.entity.girl.psyche.PsycheStore.write(getGirlUUID(), psyche);
        if (file != null) {
            // levels changed by hand in her settings file
            for (GirlSkill skill : GirlSkill.values()) {
                int level = file.levels[skill.ordinal()];
                if (level >= 0 && level != getSkillLevel(skill)) setSkillLevel(skill, level);
            }
            // and her character, when that file was changed after her personality file
            if (file.personality != null && newer(NpcSettings.file(this.getServer(), getGirlUUID()),
                    org.velvet.VelvetPaths.personality(getGirlUUID()))) {
                psyche.setPersonality(org.velvet.entity.girl.psyche.Personality.fromJson(file.personality, getRandom()));
                org.velvet.entity.girl.psyche.PsycheStore.write(getGirlUUID(), psyche);
            }
        }
        writeSettings();
    }

    private static boolean newer(java.nio.file.Path a, java.nio.file.Path b) {
        try {
            return java.nio.file.Files.getLastModifiedTime(a).compareTo(java.nio.file.Files.getLastModifiedTime(b)) > 0;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private void applySettings() {
        this.setNoAi(!settings.ai);
        this.setInvulnerable(settings.invulnerable);
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
    public org.velvet.skin.GirlSkin getSkin() {
        String skin = this.entityData.get(DATA_SKIN);
        return skin.isEmpty() ? null : org.velvet.skin.GirlSkin.decode(skin);
    }

    public void setSkin(@Nullable org.velvet.skin.GirlSkin skin) {
        this.entityData.set(DATA_SKIN, skin == null ? "" : skin.encode());
    }

    /**
     * How she looks, for the editor's NPC list and its model view when she's far away: model variant, custom
     * model, bone changes, skin, name and what she wears and holds. Also her health, food and home.
     */
    public CompoundTag appearance() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", getCustomNameString());
        tag.putString("preset", getGirlType().id());
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
        GirlType preset = GirlType.byId(tag.getString("preset"));
        if (preset != null) setGirlType(preset);
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
        tag.putString("preset", getGirlType().id());
        UUID owner = getOwnerUUID();
        if (owner != null) tag.putString("owner", owner.toString());
        tag.putBoolean("following", isFollowing());
        needs.save(tag);
        survival.save(tag);
        brain.save(tag);
        psyche.save(tag);
        if (!this.level().isClientSide && settingsLoaded) {
            org.velvet.entity.girl.psyche.PsycheStore.write(getGirlUUID(), psyche);
            writeSettings();
        }
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
        GirlType preset = GirlType.byId(tag.getString("preset"));
        if (preset != null) setGirlType(preset);
        if (tag.contains("owner"))      setOwnerUUID(UUID.fromString(tag.getString("owner")));
        // before 1.2 having an owner meant following him
        setFollowing(tag.contains("following") ? tag.getBoolean("following") : tag.contains("owner"));
        needs.load(tag);
        survival.load(tag);
        brain.load(tag);
        psyche.load(tag);
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
        // the editor wand and the debug tool have their own use on her
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || getCurrentAction().isBusy()
                || player.getItemInHand(hand).getItem() instanceof NpcEditorWandItem
                || player.getItemInHand(hand).getItem() instanceof org.velvet.item.NpcDebugToolItem) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer serverPlayer) openMenu(serverPlayer);
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public void openMenu(ServerPlayer player) {
        if (getCurrentAction().isBusy() || !GirlManager.canCommand(player, this)) return;
        claimBy(player);
        psyche.attention(player.getUUID().equals(getOwnerUUID()));
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
        // players hurt her like any mob, unless the debug tool switched that off for her
        if (!settings.damageFromPlayers && source.getEntity() instanceof Player) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            if (getCurrentAction().isBusy()) scene.stop();
            if (!source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)) psyche.onHurt(source, amount);
            // starving doesn't toughen her up
            if (this.isAlive() && !source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)) {
                lastHurtTick = this.tickCount;
                addSkillXp(GirlSkill.ENDURANCE, Math.max(1, Math.round(amount * 3)));
            }
        }
        return hurt;
    }

    /** Something she killed: a victory to be glad about, once it's safe. */
    @Override
    public boolean killedEntity(@NotNull ServerLevel level, @NotNull LivingEntity victim) {
        psyche.onKill(victim);
        return super.killedEntity(level, victim);
    }

    @Override
    public void die(@NotNull DamageSource source) {
        if (!this.level().isClientSide) scene.stop();
        super.die(source);
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new org.velvet.client.animation.GaitAnimationController(
                this, "movement", 5, org.velvet.client.animation.GirlAnimationController::movementPredicate)
                .setSoundKeyframeHandler(org.velvet.client.animation.GirlAnimationController::ignoreKeyframe));
        controllers.add(new org.velvet.client.animation.ScrubAnimationController(
                this, "action", 2, org.velvet.client.animation.GirlAnimationController::actionPredicate)
                .setSoundKeyframeHandler(org.velvet.client.animation.GirlAnimationController::onSoundKeyframe));
        controllers.add(new AnimationController<>(
                this, "emote", 8, org.velvet.client.animation.GirlAnimationController::emotePredicate));
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

    /** Her model preset: the one picked in the editor, else her entity type's own. */
    public GirlType getGirlType() {
        String id = this.entityData.get(DATA_PRESET);
        if (id.isEmpty()) return this.girlType;
        GirlType preset = GirlType.byId(id);
        return preset != null ? preset : this.girlType;
    }

    public void setGirlType(GirlType preset) {
        this.entityData.set(DATA_PRESET, preset == this.girlType ? "" : preset.id());
    }

    @Override
    public @NotNull Component getName() {
        String custom = getCustomNameString();
        return custom.isEmpty() ? getGirlType().displayName() : Component.literal(custom);
    }

    public ResourceLocation getTextureLocation() {
        return getGirlType().texture();
    }
}
