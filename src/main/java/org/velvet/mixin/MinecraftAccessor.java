package org.velvet.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the game draw a frame into another target for a moment (an NPC's eyes, see EyeCamera). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Mutable
    @Accessor("mainRenderTarget")
    void velvet$setMainRenderTarget(RenderTarget target);
}
