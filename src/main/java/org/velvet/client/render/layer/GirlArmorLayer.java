package org.velvet.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.velvet.entity.girl.AbstractGirlEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worn armor, drawn with the vanilla armor textures (dye, trims and enchantment glint included).
 * <p>
 * Each piece is a box fitted to a part of the girl's body and attached to that body bone, so it follows
 * her animations. Arms and legs are split at the elbow and knee: the upper box takes the upper half of the
 * vanilla limb texture, the lower box the lower half. Body bones exist in every model variant, so armor
 * shows whether she's dressed or not.
 */
public class GirlArmorLayer<T extends AbstractGirlEntity> extends GeoRenderLayer<T> {

    private static final int LEATHER_DEFAULT = -6265536;

    /**
     * One armor box. The bounds are in Blockbench pixels of the girl's model (y up, front = -z); the
     * texture comes from a vanilla box of size w x h x d at (u, v) of the 64x32 armor texture.
     */
    private record Piece(EquipmentSlot slot, ModelPart part, float w, float h, float d,
                         float x0, float y0, float z0, float x1, float y1, float z1) {}

    /** Bone name -> its pieces. */
    private static final Map<String, List<Piece>> PIECES = new HashMap<>();

    private static void piece(String bone, EquipmentSlot slot, int u, int v, int w, int h, int d, boolean mirror,
                              float x0, float y0, float z0, float x1, float y1, float z1, float grow) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("box", CubeListBuilder.create().texOffs(u, v).mirror(mirror)
                .addBox(0, 0, 0, w, h, d), PartPose.ZERO);
        ModelPart part = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("box");
        PIECES.computeIfAbsent(bone, b -> new ArrayList<>()).add(new Piece(slot, part, w, h, d,
                x0 - grow, y0 - grow, z0 - grow, x1 + grow, y1 + grow, z1 + grow));
    }

    static {
        EquipmentSlot head = EquipmentSlot.HEAD, chest = EquipmentSlot.CHEST, legs = EquipmentSlot.LEGS, feet = EquipmentSlot.FEET;
        float outer = 0.45F;
        float inner = 0.25F;

        piece("head", head, 0, 0, 8, 8, 8, false, -4.0F, 23.9F, -4.6F, 4.0F, 31.4F, 3.4F, 0.6F);

        // chestplate: the upper 8 rows of the vanilla body on the chest (deep enough for the bust),
        // the lower 4 rows on the hips, and the arms split at the elbows
        piece("torso", chest, 16, 16, 8, 8, 4, false, -3.9F, 16.1F, -6.2F, 3.9F, 24.0F, 2.0F, outer);
        piece("hip", chest, 16, 24, 8, 4, 4, false, -3.9F, 12.4F, -2.6F, 3.9F, 16.1F, 3.4F, outer);
        piece("armR", chest, 40, 16, 4, 6, 4, false, -5.4F, 17.2F, -1.35F, -2.7F, 23.7F, 1.35F, outer);
        piece("lowerArmR", chest, 40, 22, 4, 6, 4, false, -5.4F, 11.7F, -1.35F, -2.7F, 17.2F, 1.35F, outer);
        piece("armL", chest, 40, 16, 4, 6, 4, true, 2.7F, 17.2F, -1.35F, 5.4F, 23.7F, 1.35F, outer);
        piece("lowerArmL", chest, 40, 22, 4, 6, 4, true, 2.7F, 11.7F, -1.35F, 5.4F, 17.2F, 1.35F, outer);

        // leggings (inner layer): the belt rows on the hips, then thighs and shins
        piece("hip", legs, 16, 24, 8, 4, 4, false, -3.8F, 12.4F, -2.5F, 3.8F, 16.1F, 3.3F, inner);
        piece("legR", legs, 0, 16, 4, 6, 4, false, -3.6F, 6.9F, -2.0F, -0.1F, 12.4F, 2.0F, inner);
        piece("shinR", legs, 0, 22, 4, 6, 4, false, -3.4F, 0.0F, -1.9F, 0.0F, 6.9F, 1.9F, inner);
        piece("legL", legs, 0, 16, 4, 6, 4, true, 0.1F, 6.9F, -2.0F, 3.6F, 12.4F, 2.0F, inner);
        piece("shinL", legs, 0, 22, 4, 6, 4, true, 0.0F, 0.0F, -1.9F, 3.4F, 6.9F, 1.9F, inner);

        // boots: the lower half of the vanilla leg on the shins
        piece("shinR", feet, 0, 22, 4, 6, 4, false, -3.4F, 0.0F, -1.9F, 0.0F, 6.9F, 1.9F, outer);
        piece("shinL", feet, 0, 22, 4, 6, 4, true, 0.0F, 0.0F, -1.9F, 3.4F, 6.9F, 1.9F, outer);
    }

    public GirlArmorLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    /** Poses of the armored bones, recorded while the model is drawn and used right after it. */
    private final Map<String, BonePose> bonePoses = new HashMap<>();

    private record BonePose(Matrix4f pose, Matrix3f normal) {}

    /**
     * Called for each bone with the pose stack moved and rotated with it. Only records the pose: switching
     * render types here would end the buffer the model is still being drawn into.
     */
    @Override
    public void renderForBone(PoseStack poseStack, T girl, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (PIECES.containsKey(bone.getName())) bonePoses.put(bone.getName(), new BonePose(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal())));
    }

    @Override
    public void render(PoseStack poseStack, T girl, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        for (Map.Entry<String, BonePose> bone : bonePoses.entrySet()) {
            for (Piece piece : PIECES.get(bone.getKey())) {
                ItemStack stack = girl.getItemBySlot(piece.slot());
                if (!(stack.getItem() instanceof ArmorItem armor)) continue;
                poseStack.pushPose();
                poseStack.last().pose().set(bone.getValue().pose());
                poseStack.last().normal().set(bone.getValue().normal());
                renderPiece(poseStack, piece, stack, armor.getMaterial(), bufferSource, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }
        bonePoses.clear();
    }

    private static void renderPiece(PoseStack poseStack, Piece piece, ItemStack stack, Holder<ArmorMaterial> material,
                                    MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // model space -> vanilla model space (y down), then stretch the vanilla box over the body part
        poseStack.scale(-1, -1, 1);
        poseStack.translate(piece.x0() / 16, -piece.y1() / 16, piece.z0() / 16);
        poseStack.scale((piece.x1() - piece.x0()) / piece.w(), (piece.y1() - piece.y0()) / piece.h(),
                (piece.z1() - piece.z0()) / piece.d());

        boolean inner = piece.slot() == EquipmentSlot.LEGS;
        for (ArmorMaterial.Layer layer : material.value().layers()) {
            int color = stack.is(ItemTags.DYEABLE)
                    ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, LEATHER_DEFAULT)) : -1;
            VertexConsumer vc = bufferSource.getBuffer(RenderType.armorCutoutNoCull(layer.texture(inner)));
            piece.part().render(poseStack, vc, packedLight, packedOverlay, color);
        }
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                    .getSprite(inner ? trim.innerTexture(material) : trim.outerTexture(material));
            VertexConsumer vc = sprite.wrap(bufferSource.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
            piece.part().render(poseStack, vc, packedLight, packedOverlay);
        }
        if (stack.hasFoil()) {
            piece.part().render(poseStack, bufferSource.getBuffer(RenderType.armorEntityGlint()), packedLight, packedOverlay);
        }
        poseStack.popPose();
    }
}
