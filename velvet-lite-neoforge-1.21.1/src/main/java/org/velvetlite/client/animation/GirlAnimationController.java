package org.velvetlite.client.animation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlAction;
import org.velvetlite.entity.girl.GirlType;
import org.velvetlite.registry.VelvetSounds;
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
        if (action.isBusy() && action != GirlAction.DOGGY_TO_BED) {
            return PlayState.STOP;
        }

        GirlType type = girl.getGirlType();
        Optional<RawAnimation> anim;
        if (isWalking(girl)) {
            anim = switch (girl.getMode()) {
                case WALK      -> find(type, Mode.LOOP, "walk");
                case FAST_WALK -> find(type, Mode.LOOP, "fastwalk", "walk");
                case RUN       -> find(type, Mode.LOOP, "run", "running", "walk");
            };
        } else {
            anim = find(type, Mode.LOOP, "idle");
        }
        return anim.map(state::setAndContinue).orElse(PlayState.STOP);
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

    /** Movement animations carry instruction keyframes too; they need no handling. */
    public static void ignoreKeyframe(SoundKeyframeEvent<AbstractGirlEntity> event) {}

    public static PlayState actionPredicate(AnimationState<AbstractGirlEntity> state) {
        GirlAction action = state.getAnimatable().getCurrentAction();
        if (action.animation == null) {
            return PlayState.STOP;
        }
        // ONCE stages hold their last frame until the server switches to the next stage
        Mode mode = action.play == GirlAction.Play.LOOP ? Mode.LOOP : Mode.HOLD;
        return find(state.getAnimatable().getGirlType(), mode, action.animation)
                .map(state::setAndContinue)
                .orElse(PlayState.STOP);
    }

    /**
     * Animations carry instruction keyframes in their sound track ("paymentMSG1", "bjiDone", ...). Stage
     * timing is handled by the server; here the keyframes only play their sounds, client-side.
     */
    public static void onSoundKeyframe(SoundKeyframeEvent<AbstractGirlEntity> event) {
        SoundCue cue = SOUND_CUES.get(event.getKeyframeData().getSound());
        if (cue != null) cue.play(event.getAnimatable(), event.getAnimatable().getRandom());
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

    private static SoundCue all(SoundCue... cues) {
        return (girl, random) -> {
            for (SoundCue cue : cues) cue.play(girl, random);
        };
    }

    /** Plays {@code rare} with a 1 in {@code chance} chance, {@code usual} otherwise. */
    private static SoundCue oneIn(int chance, SoundCue rare, SoundCue usual) {
        return (girl, random) -> (random.nextInt(chance) == 0 ? rare : usual).play(girl, random);
    }

    /**
     * Which sounds each animation keyframe plays: the same sounds at the same moments as the original
     * mod (bed creak when she climbs on the bed, touches and slaps, rhythm sounds, breathing and moans).
     */
    private static final Map<String, SoundCue> SOUND_CUES = Map.ofEntries(
            Map.entry("stripMSG1", sound(VelvetSounds.GIRLS_ELSIE_GIGGLE)),

            Map.entry("paymentMSG1", sound(VelvetSounds.GIRLS_ELSIE_HUH)),
            Map.entry("paymentMSG2", sound(VelvetSounds.MISC_PLOB, 0.5F)),
            Map.entry("paymentMSG3", sound(VelvetSounds.GIRLS_ELSIE_GIGGLE)),
            Map.entry("paymentMSG4", sound(VelvetSounds.MISC_PLOB, 0.25F)),

            Map.entry("bjiMSG1", sound(VelvetSounds.GIRLS_ELSIE_MMM)),
            Map.entry("bjiMSG2", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),
            Map.entry("bjiMSG3", sound(VelvetSounds.GIRLS_ELSIE_AFTERSSNMN)),
            Map.entry("bjiMSG4", sound(VelvetSounds.MISC_BELLJINGLE)),
            Map.entry("bjiMSG5", sound(VelvetSounds.GIRLS_ELSIE_HMPH, 0.5F)),
            Map.entry("bjiMSG6", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),
            Map.entry("bjiMSG7", sound(VelvetSounds.GIRLS_ELSIE_GIGGLE)),
            Map.entry("bjiMSG8", sound(VelvetSounds.MISC_PLOB, 0.5F)),
            Map.entry("bjiMSG9", sound(VelvetSounds.GIRLS_ELSIE_GIGGLE)),
            Map.entry("bjiMSG11", sound(VelvetSounds.GIRLS_ELSIE_LIPSOUND)),
            Map.entry("bjiMSG12", oneIn(5, sound(VelvetSounds.GIRLS_ELSIE_BJMOAN), sound(VelvetSounds.GIRLS_ELSIE_LIPSOUND))),
            Map.entry("bjtMSG1", all(sound(VelvetSounds.GIRLS_ELSIE_MMM), sound(VelvetSounds.GIRLS_ELSIE_LIPSOUND))),
            // the finales of blowjob and paizuri share these
            Map.entry("bjcMSG1", sound(VelvetSounds.GIRLS_ELSIE_BJMOAN)),
            Map.entry("bjcMSG2", sound(VelvetSounds.GIRLS_ELSIE_BJMOAN)),
            Map.entry("bjcMSG3", sound(VelvetSounds.GIRLS_ELSIE_AFTERSSNMN)),
            Map.entry("bjcMSG4", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),
            Map.entry("bjcMSG5", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),
            Map.entry("bjcMSG6", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),
            Map.entry("bjcMSG7", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),

            Map.entry("paizuriStartMSG1", sound(VelvetSounds.MISC_POUNDING)),
            Map.entry("paizuriSlowMSG1", sound(VelvetSounds.MISC_POUNDING)),
            Map.entry("paizuriFastMSG1", all(sound(VelvetSounds.MISC_POUNDING),
                    oneIn(2, sound(VelvetSounds.GIRLS_ELSIE_MMM), sound(VelvetSounds.GIRLS_ELSIE_AHH)))),

            Map.entry("doggyGoOnBedMSG1", sound(VelvetSounds.MISC_BEDRUSTLE)),
            Map.entry("doggyGoOnBedMSG2", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING)),
            Map.entry("doggyGoOnBedMSG3", sound(VelvetSounds.GIRLS_ELSIE_GIGGLE)),
            Map.entry("doggyGoOnBedMSG4", sound(VelvetSounds.MISC_SLAP, 0.75F)),
            Map.entry("doggystartMSG1", sound(VelvetSounds.MISC_TOUCH)),
            Map.entry("doggystartMSG2", sound(VelvetSounds.MISC_TOUCH)),
            Map.entry("doggystartMSG3", sound(VelvetSounds.MISC_BEDRUSTLE, 0.5F)),
            Map.entry("doggystartMSG4", all(sound(VelvetSounds.MISC_SMALLINSERTS), sound(VelvetSounds.GIRLS_ELSIE_MMM))),
            Map.entry("doggystartMSG5", all(sound(VelvetSounds.MISC_POUNDING, 0.33F), sound(VelvetSounds.GIRLS_ELSIE_MOAN))),
            Map.entry("doggyslowMSG1", all(sound(VelvetSounds.MISC_POUNDING, 0.33F),
                    oneIn(4, oneIn(2, sound(VelvetSounds.GIRLS_ELSIE_MMM), sound(VelvetSounds.GIRLS_ELSIE_MOAN)),
                            sound(VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING)))),
            Map.entry("doggyslowMSG2", sound(VelvetSounds.GIRLS_ELSIE_LIGHTBREATHING, 0.5F)),
            Map.entry("doggyfastMSG1", all(sound(VelvetSounds.MISC_POUNDING, 0.75F),
                    oneIn(3, sound(VelvetSounds.GIRLS_ELSIE_AHH),
                            oneIn(2, sound(VelvetSounds.GIRLS_ELSIE_MOAN), sound(VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING))))),
            Map.entry("doggycumMSG1", all(sound(VelvetSounds.MISC_CUMINFLATION), sound(VelvetSounds.MISC_POUNDING),
                    sound(VelvetSounds.GIRLS_ELSIE_MOAN))),
            Map.entry("doggycumMSG2", sound(VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING)),
            Map.entry("doggycumMSG3", sound(VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING)),
            Map.entry("doggycumMSG4", sound(VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING)),
            Map.entry("doggycumMSG5", sound(VelvetSounds.GIRLS_ELSIE_HEAVYBREATHING)));

    // ---- Lookup ----

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
        String folder = type.assetFolder();
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(ResourceLocation.fromNamespaceAndPath(
                VelvetLite.MODID, "animations/" + folder + "/" + folder + ".animation.json"));
        String full = "animation." + folder + "." + name;
        if (baked == null || baked.getAnimation(full) == null) return Optional.empty();
        RawAnimation raw = RawAnimation.begin();
        return Optional.of(switch (mode) {
            case LOOP -> raw.thenLoop(full);
            case ONCE -> raw.thenPlay(full);
            case HOLD -> raw.thenPlayAndHold(full);
        });
    }

    /** Call after a resource reload so newly added animations are picked up. */
    public static void clearCache() {
        CACHE.clear();
    }
}
