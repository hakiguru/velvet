package org.velvetlite.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws what she holds (given through the menu's hand slots) in the model's fists. Nothing during
 * scenes: the animations use her hands there.
 * <p>
 * GeckoLib offers to draw items in the middle of the bone pass, but an item switches the buffer the model
 * is being written to, and the soft-normal wrapper around that buffer would keep writing to the finished
 * one. So the bone pass only remembers where each hand is, and the items are drawn after it.
 */
public class GirlHeldItemLayer<T extends AbstractGirlEntity> extends BlockAndItemGeoLayer<T> {

    private static final String RIGHT_FOREARM = "lowerArmR";
    private static final String LEFT_FOREARM = "lowerArmL";
    /** Where the fist closes: this far up from the end of the forearm, like a vanilla grip. */
    private static final float GRIP_ABOVE_END = 2.0F / 16;

    private record Held(PoseStack.Pose hand, ItemStack stack, ItemDisplayContext context) {}

    private final List<Held> held = new ArrayList<>();

    public GirlHeldItemLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T girl, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ItemStack stack = getStackForBone(bone, girl);
        if (stack == null) return;
        if (bone.getCubes().isEmpty()) return;
        // the pose already carries the bone's rotation (GeckoLib's own helper would apply it a second time).
        // The forearm cubes are turned back against their bone (the right one by 140 degrees), so the fist
        // is found the way the cube itself is drawn: its own rotation, then the lower end of its box
        GeoCube forearm = bone.getCubes().getFirst();
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, forearm);
        RenderUtil.rotateMatrixAroundCube(poseStack, forearm);
        RenderUtil.translateAwayFromPivotPoint(poseStack, forearm);
        Vector3f grip = grip(forearm);
        poseStack.translate(grip.x, grip.y, grip.z);
        // GeckoLib's model space has Y up; vanilla's hand transforms below expect its Y-down model space
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        held.add(new Held(poseStack.last().copy(), stack, getTransformTypeForStack(bone, stack, girl)));
        poseStack.popPose();
    }

    /** The middle of the cube's box, a little up from its lower end. */
    private static Vector3f grip(GeoCube cube) {
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;
            for (GeoVertex vertex : quad.vertices()) {
                min.min(vertex.position());
                max.max(vertex.position());
            }
        }
        return new Vector3f((min.x + max.x) / 2, min.y + GRIP_ABOVE_END, (min.z + max.z) / 2);
    }

    @Override
    public void render(PoseStack poseStack, T girl, BakedGeoModel model, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        for (Held h : held) {
            poseStack.pushPose();
            poseStack.last().pose().set(h.hand().pose());
            poseStack.last().normal().set(h.hand().normal());
            // as vanilla's ItemInHandLayer does it, from the grip instead of the shoulder
            boolean left = h.context() == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1 : 1) / 16.0F, 0.125F, 0);
            Minecraft.getInstance().getItemRenderer().renderStatic(girl, h.stack(), h.context(), left, poseStack,
                    bufferSource, girl.level(), packedLight, packedOverlay, girl.getId());
            poseStack.popPose();
        }
        held.clear();
    }

    @Nullable
    @Override
    protected ItemStack getStackForBone(GeoBone bone, T girl) {
        if (girl.getCurrentAction().isBusy()) return null;
        ItemStack stack = switch (bone.getName()) {
            case RIGHT_FOREARM -> girl.getMainHandItem();
            case LEFT_FOREARM -> girl.getOffhandItem();
            default -> ItemStack.EMPTY;
        };
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T girl) {
        return bone.getName().equals(LEFT_FOREARM) ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
