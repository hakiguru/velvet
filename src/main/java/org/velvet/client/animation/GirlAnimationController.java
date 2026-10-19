package org.velvet.client.animation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlAction;
import org.velvet.entity.girl.GirlMode;
import org.velvet.entity.girl.GirlType;
import org.velvet.registry.VelvetSounds;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GirlAnimationController {

    // "<type>|<name>|<play>" -> animation, empty if the type's animation file doesn't have it
    private static final Map<String, Optional<RawAnimation>> CACHE = new ConcurrentHashMap<>();

    private GirlAnimationController() {}

    // ---- Predicates ----

    public static PlayState movementPredicate(AnimationState<AbstractGirlEntity> state) {
        AbstractGirlEntity girl = state.getAnimatable();
        GirlAction action = girl.getCurrentAction();
        // the girl stands still during actions; only walking to the bed uses the normal gait
        if (girl.clientPreviewAnimation != null || action.isBusy() && action.bed() != GirlAction.Bed.WALK) {
            return PlayState.STOP;
        }

        GirlType type = girl.getGirlType();
        Optional<RawAnimation> anim;
        // sneaking: her own crouch animation if she has one, else the renderer bends her usual one (CrouchPose)
        boolean crouch = girl.isCrouching() && hasCrouchAnimation(girl);
        if (isWalking(girl)) {
            Gait gait = crouch ? Gait.WALK : Gait.choose(girl.clientGait, girl.getMode() == GirlMode.RUN, girl.clientGroundSpeed);
            girl.clientGait = gait;
            anim = crouch ? find(type, Mode.LOOP, "crouchwalk", "crouchidle")
                    : walkingBackwards(girl) ? find(type, Mode.LOOP, "backwards_walk", "walk", "idle") : switch (gait) {
                case WALK      -> find(type, Mode.LOOP, "walk", "tail", "idle");
                case FAST_WALK -> find(type, Mode.LOOP, "fastwalk", "walk", "tail", "idle");
                case RUN       -> find(type, Mode.LOOP, "run", "running", "fastwalk", "walk", "tail", "idle");
            };
        } else {
            girl.clientGait = null;
            anim = crouch ? find(type, Mode.LOOP, "crouchidle") : find(type, Mode.LOOP, "idle", "tail");
        }
        return anim.map(state::setAndContinue).orElse(PlayState.STOP);
    }

    /**
     * Walk cycles, with how far the legs carry her in one tick at normal playback (stride per loop divided by
     * the loop length). Whether she runs is up to her AI (she runs to catch up, or along with a sprinting
     * owner) or when she's clearly faster than walking (panic, fleeing); walking, she takes the brisk cycle
     * once she's faster than halfway between the two walk cycles. Either way the cycle then plays as fast as
     * she actually moves.
     */
    public enum Gait {
        WALK(0.053), FAST_WALK(0.08), RUN(0.147);

        /** Blocks per tick the animation shows at speed 1. */
        final double pace;

        Gait(double pace) {
            this.pace = pace;
        }

        /** Keeps the current cycle until the speed is clearly past the switch point, so it doesn't flicker. */
        private static final double HYSTERESIS = 0.015;
        private static final double BRISK_FROM = (WALK.pace + FAST_WALK.pace) / 2;

        /** Faster than any walk her AI asks for (panic, fleeing): she runs whatever her mode says. */
        private static final double RUN_FROM = 0.17;

        static Gait choose(Gait current, boolean running, double speed) {
            if (running || speed > RUN_FROM + (current == RUN ? -HYSTERESIS : HYSTERESIS)) return RUN;
            double threshold = BRISK_FROM + (current == FAST_WALK ? -HYSTERESIS : HYSTERESIS);
            return speed > threshold ? FAST_WALK : WALK;
        }
    }

    /** Playback speed of the walk cycle: the legs move as fast as she actually goes. */
    static double gaitSpeed(AbstractGirlEntity girl) {
        Gait gait = girl.clientGait;
        if (gait == null) return 1.0;
        return Math.clamp(girl.clientGroundSpeed / gait.pace, 0.4, 2.5);
    }

    /** Square of the horizontal distance per tick above which she counts as walking. */
    private static final double WALK_THRESHOLD_SQR = 0.015 * 0.015;

    /**
     * GeckoLib's isMoving() looks at the limb swing, which stays tiny at slow speeds, so she would glide
     * in the idle pose. Use the actual movement since the last tick instead.
     */
    private static boolean isWalking(AbstractGirlEntity girl) {
        double dx = girl.getX() - girl.xo;
        double dz = girl.getZ() - girl.zo;
        return dx * dx + dz * dz > WALK_THRESHOLD_SQR || girl.getNavigation().isInProgress() && girl.walkAnimation.isMoving();
    }

    /** Presets that come with their own sneaking animations (the others get CrouchPose). */
    public static boolean hasCrouchAnimation(AbstractGirlEntity girl) {
        return find(girl.getGirlType(), Mode.LOOP, "crouchidle").isPresent();
    }

    /** Movement animations carry instruction keyframes too; they need no handling. */
    public static void ignoreKeyframe(SoundKeyframeEvent<AbstractGirlEntity> event) {}

    public static PlayState actionPredicate(AnimationState<AbstractGirlEntity> state) {
        // GeckoLib remembers what it was asked to play even when it couldn't start it (left stopped with nothing
        // queued), and then won't try again while it's asked for the same: make it try again
        var controller = state.getController();
        if (controller.getAnimationState() == software.bernie.geckolib.animation.AnimationController.State.STOPPED
                && controller.getCurrentAnimation() == null) {
            controller.forceAnimationReset();
        }
        String preview = state.getAnimatable().clientPreviewAnimation;
        if (preview != null) {
            // the editor's pose buttons: any animation of any preset ("animation.<preset>.<name>"), looped
            return shared(preview, Mode.LOOP).map(state::setAndContinue).orElse(PlayState.STOP);
        }
        GirlAction action = state.getAnimatable().getCurrentAction();
        if (action.animation == null) {
            return life(state);
        }
        // ONCE stages hold their last frame until the server switches to the next stage
        Mode mode = action.play == GirlAction.Play.LOOP ? Mode.LOOP : Mode.HOLD;
        return stage(state.getAnimatable().getGirlType(), action, mode)
                .map(state::setAndContinue)
                .orElse(PlayState.STOP);
    }

    /** How long a swing's attack animation is given before her arms go back to what they were doing. */
    private static final int ATTACK_TICKS = 9;

    /**
     * Outside scenes: what she's doing with her whole body, for the presets that have an animation of it
     * (most have some): riding, gliding, drawing a bow, a blow.
     */
    private static PlayState life(AnimationState<AbstractGirlEntity> state) {
        AbstractGirlEntity girl = state.getAnimatable();
        GirlType type = girl.getGirlType();
        // a new swing: one of her blows, from its start
        if (girl.swinging && girl.swingTime == 0 && girl.tickCount - girl.clientSwingAt > 2) {
            girl.clientSwingAt = girl.tickCount;
            girl.clientSwings++;
            state.getController().forceAnimationReset();
        }
        Optional<RawAnimation> anim = Optional.empty();
        if (girl.tickCount - girl.clientSwingAt < ATTACK_TICKS) {
            anim = find(type, Mode.HOLD, "attack" + girl.clientSwings % 3, "attack0");
        }
        if (anim.isEmpty() && girl.isPassenger()) anim = find(type, Mode.LOOP, "ride", "sit");
        if (anim.isEmpty() && girl.isFallFlying()) anim = find(type, Mode.LOOP, "fly", "fly2");
        if (anim.isEmpty() && girl.isUsingItem() && (girl.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem
                || girl.getUseItem().getItem() instanceof net.minecraft.world.item.CrossbowItem)) {
            anim = find(type, Mode.LOOP, "bowcharge");
        }
        return anim.map(state::setAndContinue).orElse(PlayState.STOP);
    }

    /** Stepping back from where she faces (backing off a creeper, say). */
    private static boolean walkingBackwards(AbstractGirlEntity girl) {
        double dx = girl.getX() - girl.xo, dz = girl.getZ() - girl.zo;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.03) return false;
        float yaw = girl.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        double forward = (-net.minecraft.util.Mth.sin(yaw) * dx + net.minecraft.util.Mth.cos(yaw) * dz) / len;
        return forward < -0.5;
    }

    /**
     * Animations carry instruction keyframes in their sound track ("paymentMSG1", "pound", ...). Stage
     * timing is handled by the server; here the keyframes only play their sounds, client-side: noises from
     * the shared ones, voice from whoever plays the scene (her preset's voice, whichever preset the scene
     * comes from). Keyframes not in {@link #SOUND_CUES} are instructions without a sound.
     */
    public static void onSoundKeyframe(SoundKeyframeEvent<AbstractGirlEntity> event) {
        AbstractGirlEntity girl = event.getAnimatable();
        SoundCue cue = SOUND_CUES.get(event.getKeyframeData().getSound());
        if (cue != null) cue.play(girl, girl.getRandom());
    }

    /**
     * Voices don't all have every kind of sound: each kind falls back to the closest ones a voice may have
     * instead.
     */
    private static final Map<String, String[]> SIMILAR = Map.ofEntries(
            Map.entry("moan", new String[]{"moan", "ahh", "mmm", "bjmoan"}),
            Map.entry("ahh", new String[]{"ahh", "moan", "mmm"}),
            Map.entry("mmm", new String[]{"mmm", "moan", "ahh"}),
            Map.entry("bjmoan", new String[]{"bjmoan", "mmm", "moan"}),
            Map.entry("lightbreathing", new String[]{"lightbreathing", "breath", "breathing", "sigh"}),
            Map.entry("heavybreathing", new String[]{"heavybreathing", "breath", "breathing", "lightbreathing", "ahh"}),
            Map.entry("aftersessionmoan", new String[]{"aftersessionmoan", "moan", "mmm", "heavybreathing", "breath"}),
            Map.entry("lipsound", new String[]{"lipsound", "mmm", "bjmoan"}),
            Map.entry("horninya", new String[]{"horninya", "cutenya", "moan", "mmm"}),
            Map.entry("giggle", new String[]{"giggle", "happyoh", "hehe"}),
            Map.entry("scawy", new String[]{"scawy", "huh", "ahh"}),
            Map.entry("cutenya", new String[]{"cutenya", "giggle", "happyoh"}));

    /** Whose takes fill in for a preset without a voice, or for the kinds of sounds its voice doesn't have. */
    private static final String FALLBACK_VOICE = "elsie";

    /** Her voice: the first take of {@code kind} (or the closest kind) it has; Jenny's when it has none. */
    private static SoundCue voice(String kind) {
        return voice(kind, 1.0F);
    }

    private static SoundCue voice(String kind, float volume) {
        String[] kinds = SIMILAR.getOrDefault(kind, new String[]{kind});
        return (girl, random) -> {
            SoundEvent sound = org.velvet.registry.VelvetVoices.random(girl.getGirlType().voice(), kinds);
            if (sound == null) sound = org.velvet.registry.VelvetVoices.random(FALLBACK_VOICE, kinds);
            if (sound != null) {
                girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), sound, SoundSource.NEUTRAL, volume, 1.0F, false);
            }
        };
    }

    @FunctionalInterface
    private interface SoundCue {
        void play(AbstractGirlEntity girl, RandomSource random);
    }

    private static SoundCue sound(DeferredHolder<SoundEvent, SoundEvent>[] pool) {
        return sound(pool, 1.0F);
    }

    private static SoundCue sound(DeferredHolder<SoundEvent, SoundEvent>[] pool, float volume) {
        return (girl, random) -> {
            SoundEvent sound = VelvetSounds.random(pool);
            if (sound != null) {
                girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), sound, SoundSource.NEUTRAL,
                        volume, 1.0F, false);
            }
        };
    }

    private static SoundCue vanilla(SoundEvent sound, float volume) {
        return (girl, random) -> girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), sound,
                SoundSource.NEUTRAL, volume, 0.9F + random.nextFloat() * 0.2F, false);
    }

    private static SoundCue all(SoundCue... cues) {
        return (girl, random) -> {
            for (SoundCue cue : cues) cue.play(girl, random);
        };
    }

    /** Plays {@code rare} with a 1 in {@code chance} chance, {@code usual} otherwise. */
    private static SoundCue oneIn(int chance, SoundCue rare, SoundCue usual) {
        return (girl, random) -> (random.nextInt(chance) == 0 ? rare : usual).play(girl, random);
    }

    // a few cues several scenes share
    private static final SoundCue SOFT_THRUST = all(sound(VelvetSounds.MISC_POUNDING, 0.33F),
            oneIn(3, oneIn(2, voice("mmm"), voice("moan")), voice("heavybreathing")));
    private static final SoundCue HARD_THRUST = all(sound(VelvetSounds.MISC_POUNDING, 0.75F),
            oneIn(3, voice("ahh"), oneIn(2, voice("moan"), voice("heavybreathing"))));
    private static final SoundCue FINISH = all(sound(VelvetSounds.MISC_CUMINFLATION), sound(VelvetSounds.MISC_POUNDING),
            voice("moan"));
    private static final SoundCue PANTING = voice("heavybreathing");
    private static final SoundCue EASY_BREATH = voice("lightbreathing");

    /**
     * Which sound each keyframe plays, for every scene: bed creaks when someone gets on the bed, touches and
     * slaps, the rhythm on every thrust (softer in the slow loops), moans and breathing.
     */
    private static final Map<String, SoundCue> SOUND_CUES = Map.ofEntries(
            // shared: strip, payment
            Map.entry("stripMSG1", voice("giggle")),
            Map.entry("paymentMSG1", voice("huh")),
            Map.entry("paymentMSG2", sound(VelvetSounds.MISC_PLOB, 0.5F)),
            Map.entry("paymentMSG3", voice("giggle")),
            Map.entry("paymentMSG4", sound(VelvetSounds.MISC_PLOB, 0.25F)),
            Map.entry("eatPay", sound(VelvetSounds.MISC_EAT, 0.6F)),
            Map.entry("burp", vanilla(net.minecraft.sounds.SoundEvents.PLAYER_BURP, 0.5F)),
            // short ones the presets' animations scatter around
            Map.entry("lipsound", voice("lipsound")),
            Map.entry("hmph", voice("hmph")),
            Map.entry("happyOh", voice("happyoh")),
            Map.entry("cutenya3", voice("cutenya")),
            Map.entry("scream", voice("scawy")),
            Map.entry("eat", sound(VelvetSounds.MISC_EAT, 0.6F)),
            Map.entry("flap", vanilla(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.25F)),
            Map.entry("tpSound", vanilla(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.5F)),
            Map.entry("rod_shoot", vanilla(net.minecraft.sounds.SoundEvents.FISHING_BOBBER_THROW, 0.5F)),

            // Jenny: blowjob
            Map.entry("bjiMSG1", voice("mmm")),
            Map.entry("bjiMSG2", EASY_BREATH),
            Map.entry("bjiMSG3", voice("aftersessionmoan")),
            Map.entry("bjiMSG4", sound(VelvetSounds.MISC_BELLJINGLE)),
            Map.entry("bjiMSG5", voice("hmph", 0.5F)),
            Map.entry("bjiMSG6", EASY_BREATH),
            Map.entry("bjiMSG7", voice("giggle")),
            Map.entry("bjiMSG8", sound(VelvetSounds.MISC_PLOB, 0.5F)),
            Map.entry("bjiMSG9", voice("giggle")),
            Map.entry("bjiMSG11", voice("lipsound")),
            Map.entry("bjiMSG12", oneIn(5, voice("bjmoan"), voice("lipsound"))),
            Map.entry("bjtMSG1", all(voice("mmm"), voice("lipsound"))),
            // the finales of blowjob and paizuri share these
            Map.entry("bjcMSG1", voice("bjmoan")),
            Map.entry("bjcMSG2", voice("bjmoan")),
            Map.entry("bjcMSG3", voice("aftersessionmoan")),
            Map.entry("bjcMSG4", EASY_BREATH),
            Map.entry("bjcMSG5", EASY_BREATH),
            Map.entry("bjcMSG6", EASY_BREATH),
            Map.entry("bjcMSG7", EASY_BREATH),

            // Jenny: paizuri
            Map.entry("paizuriStartMSG1", sound(VelvetSounds.MISC_POUNDING)),
            Map.entry("paizuriSlowMSG1", sound(VelvetSounds.MISC_POUNDING)),
            Map.entry("paizuriFastMSG1", all(sound(VelvetSounds.MISC_POUNDING), oneIn(2, voice("mmm"), voice("ahh")))),

            // Jenny and Slime: doggy
            Map.entry("doggyGoOnBedMSG1", sound(VelvetSounds.MISC_BEDRUSTLE)),
            Map.entry("doggyGoOnBedMSG2", EASY_BREATH),
            Map.entry("doggyGoOnBedMSG3", voice("giggle")),
            Map.entry("doggyGoOnBedMSG4", sound(VelvetSounds.MISC_SLAP, 0.75F)),
            Map.entry("doggystartMSG1", sound(VelvetSounds.MISC_TOUCH)),
            Map.entry("doggystartMSG2", sound(VelvetSounds.MISC_TOUCH)),
            Map.entry("doggystartMSG3", sound(VelvetSounds.MISC_BEDRUSTLE, 0.5F)),
            Map.entry("doggystartMSG4", all(sound(VelvetSounds.MISC_SMALLINSERTS), voice("mmm"))),
            Map.entry("doggystartMSG5", all(sound(VelvetSounds.MISC_POUNDING, 0.33F), voice("moan"))),
            Map.entry("doggyslowMSG1", SOFT_THRUST),
            Map.entry("doggyslowMSG2", voice("lightbreathing", 0.5F)),
            Map.entry("doggyfastMSG1", HARD_THRUST),
            Map.entry("doggycumMSG1", FINISH),
            Map.entry("doggycumMSG2", PANTING),
            Map.entry("doggycumMSG3", PANTING),
            Map.entry("doggycumMSG4", PANTING),
            Map.entry("doggycumMSG5", PANTING),

            // Ellie: sitting down on the bed, cowgirl, missionary
            Map.entry("sitdownMSG1", sound(VelvetSounds.MISC_BEDRUSTLE)),
            Map.entry("cowgirlStartMSG0", sound(VelvetSounds.MISC_BEDRUSTLE)),
            Map.entry("cowgirlStartMSG1", all(sound(VelvetSounds.MISC_SMALLINSERTS), voice("mmm"))),
            Map.entry("cowgirlStartMSG2", SOFT_THRUST),
            Map.entry("cowgirlfastMSG1", HARD_THRUST),
            Map.entry("cowgirlcumMSG1", FINISH),
            Map.entry("cowgirlcumMSG2", voice("moan")),
            Map.entry("cowgirlcumMSG3", PANTING),
            Map.entry("cowgirlcumMSG4", PANTING),
            Map.entry("cowgirlcumMSG5", voice("aftersessionmoan")),
            Map.entry("cowgirlcumMSG6", EASY_BREATH),
            Map.entry("bedRustle", sound(VelvetSounds.MISC_BEDRUSTLE)),
            Map.entry("missionary_slowMSG1", SOFT_THRUST),
            Map.entry("missionary_fastMSG1", HARD_THRUST),
            Map.entry("missionary_cumMSG1", FINISH),
            Map.entry("missionary_cumMSG2", PANTING),

            // Bia: anal, prone
            Map.entry("anal_prepareMSG1", voice("giggle")),
            Map.entry("anal_prepareMSG2", sound(VelvetSounds.MISC_TOUCH)),
            Map.entry("anal_startMSG1", all(sound(VelvetSounds.MISC_SMALLINSERTS), voice("mmm"))),
            Map.entry("anal_startMSG2", voice("ahh")),
            Map.entry("anal_slowMSG1", SOFT_THRUST),
            Map.entry("anal_fastMSG1", HARD_THRUST),
            Map.entry("anal_cumMSG2", oneIn(2, voice("moan"), PANTING)),
            Map.entry("slide", sound(VelvetSounds.MISC_SLIDE, 0.6F)),
            Map.entry("pound", sound(VelvetSounds.MISC_POUNDING, 0.5F)),
            Map.entry("doggyMoan", oneIn(2, voice("moan"), voice("ahh"))),
            Map.entry("cum", sound(VelvetSounds.MISC_CUMINFLATION)),
            Map.entry("orgasm1", voice("moan")),
            Map.entry("orgasm2", PANTING),

            // Allie: deepthroat, reverse cowgirl
            Map.entry("deepthroat_prepareMSG1", voice("giggle")),
            Map.entry("deepthroat_prepareMSG2", voice("lipsound")),
            Map.entry("deepthroat_slowMSG1", oneIn(3, voice("bjmoan"), voice("lipsound"))),
            Map.entry("deepthroat_fastMSG1", all(voice("lipsound"), oneIn(2, voice("bjmoan"), voice("mmm")))),
            Map.entry("deepthroat_cumMSG1", all(voice("bjmoan"), sound(VelvetSounds.MISC_PLOB, 0.4F))),
            Map.entry("giggle", voice("giggle")),
            Map.entry("pounding", sound(VelvetSounds.MISC_POUNDING, 0.5F)),
            Map.entry("moan", voice("moan")),
            Map.entry("mmm", voice("mmm")),
            Map.entry("slowMoan", oneIn(2, voice("moan"), voice("mmm"))),
            Map.entry("fastMoan", oneIn(2, voice("ahh"), voice("moan"))),
            Map.entry("aftermoan", voice("aftersessionmoan")),

            // Luna: sitting, touching
            Map.entry("jump", sound(VelvetSounds.MISC_BEDRUSTLE, 0.6F)),
            Map.entry("horninya2", voice("horninya")),
            Map.entry("horninya", voice("horninya")),
            Map.entry("sitting_slowMSG1", oneIn(2, voice("moan"), voice("horninya"))),
            Map.entry("sitting_fastMSG1", oneIn(2, voice("ahh"), voice("moan"))),
            Map.entry("sitting_cumMSG1", FINISH),
            Map.entry("breath", EASY_BREATH),
            Map.entry("hehe", voice("giggle")),
            Map.entry("singing", voice("singing")),
            Map.entry("touch", sound(VelvetSounds.MISC_TOUCH)),
            Map.entry("touch_boobsMSG1", voice("mmm")),
            Map.entry("moanOrNya", oneIn(2, voice("moan"), voice("horninya"))),
            Map.entry("touch_boobs_cumMSG1", all(sound(VelvetSounds.MISC_CUMINFLATION), voice("moan"))),
            Map.entry("touch_boobs_cumMSG3", PANTING),

            // Galath: bed, the show
            Map.entry("lick", voice("mmm", 0.6F)),
            Map.entry("giggle0", voice("giggle")),
            Map.entry("orgasm", voice("moan")),
            Map.entry("masterbateCumming", PANTING),
            Map.entry("huh", voice("huh")));

    // ---- Lookup ----

    /**
     * The animation of a stage: from the preset the stage names; stages of her own (strip, wave, payment)
     * from her preset, else Jenny's.
     */
    private static Optional<RawAnimation> stage(GirlType type, GirlAction action, Mode mode) {
        if (action.source != null) return shared("animation." + action.source + "." + action.animation, mode);
        Optional<RawAnimation> own = find(type, mode, action.animation);
        return own.isPresent() ? own : find(GirlType.JENNY, mode, action.animation);
    }

    /** The full name of the animation a stage plays on her preset (as {@link #stage} picks it), for the editor. */
    public static String stageAnimation(GirlType type, GirlAction action) {
        if (action.source != null) return "animation." + action.source + "." + action.animation;
        GirlType from = find(type, Mode.LOOP, action.animation).isPresent() ? type : GirlType.JENNY;
        return "animation." + from.animationPrefix() + "." + action.animation;
    }

    /** Any animation by its full name, from whichever preset's file has it. */
    private static Optional<RawAnimation> shared(String full, Mode mode) {
        return CACHE.computeIfAbsent("*|" + full + '|' + mode, key -> {
            if (sharedAnimation(full) == null) return Optional.empty();
            RawAnimation raw = RawAnimation.begin();
            return Optional.of(switch (mode) {
                case LOOP -> raw.thenLoop(full);
                case ONCE -> raw.thenPlay(full);
                case HOLD -> raw.thenPlayAndHold(full);
            });
        });
    }

    private enum Mode { LOOP, ONCE, HOLD }

    /** First of {@code names} that exists in the type's animation file, as "animation.<folder>.<name>". */
    private static Optional<RawAnimation> find(GirlType type, Mode mode, String... names) {
        for (String name : names) {
            Optional<RawAnimation> anim = CACHE.computeIfAbsent(type.id() + '|' + name + '|' + mode,
                    key -> lookup(type, name, mode));
            if (anim.isPresent()) return anim;
        }
        return Optional.empty();
    }

    private static Optional<RawAnimation> lookup(GirlType type, String name, Mode mode) {
        if (type.isCustom()) org.velvet.client.preset.PresetCache.ensureBaked(type);
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(type.animations());
        String full = "animation." + type.animationPrefix() + "." + name;
        // a custom preset without an animation of its own walks (and so on) like its base preset
        if ((baked == null || baked.getAnimation(full) == null) && type.isCustom()) return lookup(type.base(), name, mode);
        if (baked == null || baked.getAnimation(full) == null) return Optional.empty();
        RawAnimation raw = RawAnimation.begin();
        return Optional.of(switch (mode) {
            case LOOP -> raw.thenLoop(full);
            case ONCE -> raw.thenPlay(full);
            case HOLD -> raw.thenPlayAndHold(full);
        });
    }

    /**
     * An animation by its full name from whichever preset's file it is in ("animation.<preset>.<name>"),
     * so any model can play another preset's scene or pose; null if there's no such animation.
     */
    @org.jetbrains.annotations.Nullable
    public static software.bernie.geckolib.animation.Animation sharedAnimation(String fullName) {
        if (!fullName.startsWith("animation.")) return null;
        int dot = fullName.indexOf('.', "animation.".length());
        if (dot < 0) return null;
        String prefix = fullName.substring("animation.".length(), dot);
        ResourceLocation extra = SHARED_FILES.get(prefix);
        if (extra != null) {
            BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(extra);
            return baked == null ? null : baked.getAnimation(fullName);
        }
        for (GirlType type : GirlType.values()) {
            if (!type.animationPrefix().equals(prefix)) continue;
            if (type.isCustom()) org.velvet.client.preset.PresetCache.ensureBaked(type);
            BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(type.animations());
            return baked == null ? null : baked.getAnimation(fullName);
        }
        return null;
    }

    /** Animation files that belong to no preset, shared by all: "animation.<key>.<name>". */
    private static final Map<String, ResourceLocation> SHARED_FILES = Map.of(
            "emotes", ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "animations/emotes/emotes.animation.json"));

    /** Her strong emotions shown on the upper body (crying, the happy hop, hands on hips), over whatever she's doing. */
    public static PlayState emotePredicate(AnimationState<AbstractGirlEntity> state) {
        AbstractGirlEntity girl = state.getAnimatable();
        if (girl.clientPreviewAnimation != null || girl.getCurrentAction().isBusy()) return PlayState.STOP;
        String name = switch (girl.getEmote()) {
            case CRY -> "cry";
            case JOY -> "joy";
            case ANGRY -> "angry";
            case NONE -> null;
        };
        if (name == null) return PlayState.STOP;
        return shared("animation.emotes." + name, Mode.LOOP).map(state::setAndContinue).orElse(PlayState.STOP);
    }

    /** Call after a resource reload so newly added animations are picked up. */
    public static void clearCache() {
        CACHE.clear();
    }
}
