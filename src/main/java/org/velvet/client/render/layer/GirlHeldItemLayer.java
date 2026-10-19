package org.velvet.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.velvet.entity.girl.AbstractGirlEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
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

    /** Where the forearm's two ends are (in the space the model is drawn in), and what's held. */
    private record Held(Vector3f elbow, Vector3f hand, ItemStack stack, ItemDisplayContext context) {}

    private final List<Held> held = new ArrayList<>();

    public GirlHeldItemLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    /**
     * Only remembers where the forearm is, as it's drawn: its cube turned the way it's drawn (the presets turn
     * their forearm bones and turn the cubes back, Jenny's right one by 140 degrees), then the ends of its box.
     */
    @Override
    public void renderForBone(PoseStack poseStack, T girl, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ItemStack stack = getStackForBone(bone, girl);
        if (stack == null || bone.getCubes().isEmpty()) return;
        GeoCube forearm = bone.getCubes().getFirst();
        PoseStack cube = new PoseStack();
        cube.last().pose().set(poseStack.last().pose());
        RenderUtil.translateToPivotPoint(cube, forearm);
        RenderUtil.rotateMatrixAroundCube(cube, forearm);
        RenderUtil.translateAwayFromPivotPoint(cube, forearm);
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
        for (GeoQuad quad : forearm.quads()) {
            if (quad == null) continue;
            for (GeoVertex v : quad.vertices()) {
                min.min(v.position());
                max.max(v.position());
            }
        }
        float cx = (min.x + max.x) / 2, cz = (min.z + max.z) / 2;
        Vector3f elbow = cube.last().pose().transformPosition(new Vector3f(cx, max.y, cz));
        Vector3f hand = cube.last().pose().transformPosition(new Vector3f(cx, min.y, cz));
        held.add(new Held(elbow, hand, stack, getTransformTypeForStack(bone, stack, girl)));
    }

    /**
     * The items, held the way a player holds them: vanilla's arm space, turned so that "down the arm" is wherever
     * her forearm points now; which way round it is comes from her body (her back stays her back), so twists in
     * how a preset built its arm don't turn the item.
     */
    @Override
    public void render(PoseStack poseStack, T girl, BakedGeoModel model, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        Matrix4f toModel = new Matrix4f(poseStack.last().pose()).invert();
        // her back (the layers are drawn before the model is turned her way, so it comes from her body's yaw;
        // the menu and the editor set that yaw too)
        float yaw = Mth.rotLerp(partialTick, girl.yBodyRotO, girl.yBodyRot) * Mth.DEG_TO_RAD;
        Vector3f body = new Vector3f(Mth.sin(yaw), 0, -Mth.cos(yaw));
        for (Held h : held) {
            Vector3f elbow = toModel.transformPosition(new Vector3f(h.elbow()));
            Vector3f hand = toModel.transformPosition(new Vector3f(h.hand()));
            Vector3f down = new Vector3f(hand).sub(elbow);
            if (down.lengthSquared() < 1.0E-8F) continue;
            down.normalize();
            Vector3f back = new Vector3f(body).sub(new Vector3f(down).mul(down.dot(body)));
            if (back.lengthSquared() < 1.0E-6F) back.set(0, 1, 0).sub(new Vector3f(down).mul(down.y));
            back.normalize();
            Vector3f side = new Vector3f(down).cross(back);
            // vanilla's arm space (x her left, y down the arm, z her back) laid along her forearm
            Quaternionf turn = new Quaternionf().setFromNormalized(new Matrix3f(side, down, back));
            Vector3f grip = new Vector3f(hand).add(new Vector3f(elbow).sub(hand).normalize().mul(GRIP_ABOVE_END));

            poseStack.pushPose();
            poseStack.translate(grip.x, grip.y, grip.z);
            poseStack.mulPose(turn);
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
        if (girl.getCurrentAction().isBusy() || girl.clientPreviewScene != null) return null;
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
