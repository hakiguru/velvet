package org.velvet.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.velvet.Velvet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class VelvetSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Velvet.MODID);

    private static final Random RNG = new Random();
    private static final Map<DeferredHolder<SoundEvent, SoundEvent>, Integer> LAST_RANDOM = new HashMap<>();

    // ---- MISC ----
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_PLOB          = make("misc_plob", 1);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_BELLJINGLE    = make("misc_bell_jingle", 1);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_BEDRUSTLE     = make("misc_bed_rustle", 2);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_SLAP          = make("misc_slap", 2);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_TOUCH         = make("misc_touch", 2);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_POUNDING      = make("misc_pounding", 36);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_INSERTS       = make("misc_inserts", 5);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_SMALLINSERTS  = make("misc_small_inserts", 5);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_CUMINFLATION  = make("misc_cum_inflation", 1);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_SCREAM        = make("misc_scream", 2);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_FART          = make("misc_fart", 3);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_JUMP          = make("misc_jump", 1);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_EAT           = make("misc_eat", 3);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_SLIDE         = make("misc_slide", 7);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_FLAP          = make("misc_flap", 4);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_SHATTER       = make("misc_shatter", 1);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_WEOWEO        = make("misc_weoweo", 4);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_BEEW          = make("misc_beew", 3);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_CLAP          = make("misc_clap", 1);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] MISC_PYRO          = make("misc_pyro", 1);

    // ---- ELSIE ----
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_AFTERSSNMN       = make("girls_elsie_after_ssnmn", 5);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_AHH               = make("girls_elsie_ahh", 10);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_BJMOAN            = make("girls_elsie_bj_moan", 13);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_GIGGLE            = make("girls_elsie_giggle", 5);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_HAPPYOH           = make("girls_elsie_happy_oh", 3);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_HEAVYBREATHING    = make("girls_elsie_heavy_breathing", 8);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_HMPH              = make("girls_elsie_hmph", 4);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_HUH               = make("girls_elsie_huh", 2);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_LIGHTBREATHING    = make("girls_elsie_light_breathing", 12);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_LIPSOUND          = make("girls_elsie_lip_sound", 10);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_MMM               = make("girls_elsie_mmm", 9);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_MOAN              = make("girls_elsie_moan", 8);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_SADOH             = make("girls_elsie_sad_oh", 2);
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GIRLS_ELSIE_SIGH              = make("girls_elsie_sigh", 2);
    // ---- Helpers ----

    @SuppressWarnings("unchecked")
    static DeferredHolder<SoundEvent, SoundEvent>[] make(String prefix, int count) {
        DeferredHolder<SoundEvent, SoundEvent>[] arr = new DeferredHolder[count];
        for (int i = 0; i < count; i++) {
            final String name = prefix + "_" + i;
            arr[i] = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(Velvet.MODID, name)));
        }
        return arr;
    }

    public static SoundEvent random(DeferredHolder<SoundEvent, SoundEvent>[] arr) {
        if (arr.length == 0) return null;
        if (arr.length == 1) return arr[0].get();
        int idx;
        int guard = 0;
        do {
            idx = RNG.nextInt(arr.length);
        } while (++guard < 10 && idx == LAST_RANDOM.getOrDefault(arr[0], -69));
        LAST_RANDOM.put(arr[0], idx);
        return arr[idx].get();
    }

    public static SoundEvent get(DeferredHolder<SoundEvent, SoundEvent> holder) {
        return holder.get();
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}