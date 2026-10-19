package org.velvet.potion;

import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.velvet.Velvet;

/**
 * Changes how big whoever drinks it is, through the {@code generic.scale} attribute: that scales the model and
 * the hitbox together, so a shrunk player really fits through a one-block gap and a grown one really can't.
 * <p>
 * The amount per level is a curve on the amplifier (glowstone bumps the amplifier): growth doubles, then ×4,
 * then ×8; shrinking goes to one block tall, then half a block, then about two pixels. A few coloured sparks
 * trail whoever is under it — blue growing smaller, red growing larger.
 */
public final class SizeEffect extends MobEffect {

    /** The player's normal height, in blocks, that the shrink targets are measured against. */
    private static final float PLAYER_HEIGHT = 1.8F;

    private final boolean growing;

    private SizeEffect(boolean growing, int colour, Int2DoubleFunction curve) {
        super(MobEffectCategory.NEUTRAL, colour);
        this.growing = growing;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, growing ? "growth" : "shrink");
        addAttributeModifier(Attributes.SCALE, id, AttributeModifier.Operation.ADD_VALUE, curve);
    }

    /** Growth: ×2, ×4, ×8. The modifier adds to the base of 1, so the amounts are one less. */
    public static SizeEffect growth() {
        return new SizeEffect(true, 0xE04A3A, amp -> Math.pow(2, amp + 1) - 1);
    }

    /** Shrink: to 1 block, half a block, ~1.8 px tall. */
    public static SizeEffect shrink() {
        double[] heights = {1.0, 0.5, 1.8 / 16.0};
        return new SizeEffect(false, 0x4A7BE0, amp -> heights[Math.min(amp, heights.length - 1)] / PLAYER_HEIGHT - 1);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        spark(entity);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 8 == 0;
    }

    private void spark(LivingEntity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var particle = growing ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.SOUL_FIRE_FLAME;
        level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                6, entity.getBbWidth() * 0.6, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.6, 0.01);
    }
}
