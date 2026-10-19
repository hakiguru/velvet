package org.velvet.client;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.psyche.Psyche;
import org.velvet.registry.VelvetVoices;

/**
 * What can be seen and heard of a strong emotion, client side (the poses are the "emote" animations): crying
 * now and then a tear from either eye and a quiet sob, never a fit; overjoyed a heart and a giggle; furious
 * a huff. Rare on purpose, so it stays touching rather than tiresome.
 */
public final class EmoteEffects {

    private EmoteEffects() {}

    public static void tick(AbstractGirlEntity girl) {
        Psyche.Emote emote = girl.getEmote();
        if (emote == Psyche.Emote.NONE) return;
        RandomSource random = girl.getRandom();
        switch (emote) {
            case CRY -> {
                if (random.nextInt(45) == 0) tear(girl, random);
                if (random.nextInt(260) == 0) voice(girl, 0.45F, "sadoh", "sigh", "lightbreathing");
            }
            case JOY -> {
                if (random.nextInt(25) == 0) particle(girl, ParticleTypes.HEART, 0.35);
                if (random.nextInt(140) == 0) voice(girl, 0.8F, "giggle", "happyoh");
            }
            case ANGRY -> {
                if (random.nextInt(60) == 0) particle(girl, ParticleTypes.ANGRY_VILLAGER, 0.25);
                if (random.nextInt(200) == 0) voice(girl, 0.7F, "hmph");
            }
            default -> {}
        }
    }

    /** A drop from one eye, running down. */
    private static void tear(AbstractGirlEntity girl, RandomSource random) {
        Vec3 forward = Vec3.directionFromRotation(10, girl.yHeadRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
        Vec3 eye = girl.getEyePosition().add(forward.scale(0.24)).add(right.scale(random.nextBoolean() ? 0.09 : -0.09)).add(0, -0.03, 0);
        girl.level().addParticle(ParticleTypes.FALLING_WATER, eye.x, eye.y, eye.z, 0, 0, 0);
    }

    private static void particle(AbstractGirlEntity girl, net.minecraft.core.particles.SimpleParticleType type, double above) {
        Vec3 head = girl.getEyePosition().add(0, above, 0);
        RandomSource r = girl.getRandom();
        girl.level().addParticle(type, head.x + (r.nextDouble() - 0.5) * 0.4, head.y, head.z + (r.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
    }

    private static void voice(AbstractGirlEntity girl, float volume, String... kinds) {
        SoundEvent sound = VelvetVoices.random(girl.getGirlType().voice(), kinds);
        if (sound != null) {
            girl.level().playLocalSound(girl.getX(), girl.getY(), girl.getZ(), sound, SoundSource.NEUTRAL, volume, 1.0F, false);
        }
    }
}
