package org.velvet.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.velvet.client.SceneCamera;

/** Puts the camera at the scene's side view when one is selected (see SceneCamera). */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void velvetLite$sceneSideView(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                          float partialTick, CallbackInfo ci) {
        SceneCamera.Pose pose = SceneCamera.sideView(entity, partialTick);
        if (pose == null) return;
        // detached: the game then draws the player himself, like in third person
        this.detached = true;
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position());
    }
}
