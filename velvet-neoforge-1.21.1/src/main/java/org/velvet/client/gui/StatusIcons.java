package org.velvet.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.velvet.entity.girl.GirlNeeds;

/**
 * Her health and food with the player's own HUD icons (hearts and drumsticks): in her menu and floating
 * above her head.
 */
public final class StatusIcons {
    private StatusIcons() {}

    public static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    public static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    public static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    public static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    public static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");
    public static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    public static final ResourceLocation ARMOR_FULL = ResourceLocation.withDefaultNamespace("hud/armor_full");

    public static final int ICON = 9;
    private static final int STEP = 8;
    private static final int PER_ROW = 10;
    private static final int ROW = 10;
    /** The filling sits this far in front of its outline (in icon pixels), so it's never hidden behind it. */
    private static final float FILL_Z = 0.1F;
    /** World size of one icon pixel; the name tag uses 0.025. */
    private static final float WORLD_SCALE = 0.014F;

    // ---- Menu ----

    public static void heart(GuiGraphics g, int x, int y) {
        g.blitSprite(HEART_CONTAINER, x, y, ICON, ICON);
        g.blitSprite(HEART_FULL, x, y, ICON, ICON);
    }

    public static void food(GuiGraphics g, int x, int y) {
        g.blitSprite(FOOD_FULL, x, y, ICON, ICON);
    }

    public static void armor(GuiGraphics g, int x, int y) {
        g.blitSprite(ARMOR_FULL, x, y, ICON, ICON);
    }

    // ---- Above her head ----

    /** World height the icons take, to stack them above the name tag. */
    public static double worldHeight(float maxHealth) {
        return (heartRows(maxHealth) + 1) * ROW * WORLD_SCALE;
    }

    private static int heartRows(float maxHealth) {
        return Math.max(1, Mth.ceil(maxHealth / 2 / PER_ROW));
    }

    /**
     * Rows of hearts with the drumsticks on top, centred on the pose's origin (its bottom edge), facing the
     * camera like a name tag.
     */
    public static void renderInWorld(PoseStack poseStack, MultiBufferSource buffers, Quaternionf cameraOrientation,
                                     float health, float maxHealth, int food, int packedLight) {
        TextureAtlasSprite container = sprite(HEART_CONTAINER);
        VertexConsumer vc = buffers.getBuffer(RenderType.text(container.atlasLocation()));
        poseStack.pushPose();
        poseStack.mulPose(cameraOrientation);
        poseStack.scale(WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
        Matrix4f pose = poseStack.last().pose();
        float left = -(STEP * (PER_ROW - 1) + ICON) / 2.0F;

        int containers = Mth.ceil(maxHealth / 2);
        int hp = Mth.ceil(health);
        int rows = heartRows(maxHealth);
        for (int i = 0; i < containers; i++) {
            float x = left + i % PER_ROW * STEP;
            float y = -ICON - i / PER_ROW * ROW;
            quad(vc, pose, container, x, y, 0, packedLight);
            if (i * 2 + 1 < hp) quad(vc, pose, sprite(HEART_FULL), x, y, FILL_Z, packedLight);
            else if (i * 2 + 1 == hp) quad(vc, pose, sprite(HEART_HALF), x, y, FILL_Z, packedLight);
        }
        // like the player's food bar: the first points sit on the right
        float foodY = -ICON - rows * ROW;
        for (int i = 0; i < GirlNeeds.MAX_FOOD / 2; i++) {
            float x = left + (PER_ROW - 1 - i) * STEP;
            quad(vc, pose, sprite(FOOD_EMPTY), x, foodY, 0, packedLight);
            if (i * 2 + 1 < food) quad(vc, pose, sprite(FOOD_FULL), x, foodY, FILL_Z, packedLight);
            else if (i * 2 + 1 == food) quad(vc, pose, sprite(FOOD_HALF), x, foodY, FILL_Z, packedLight);
        }
        poseStack.popPose();
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getGuiSprites().getSprite(id);
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, TextureAtlasSprite s, float x, float y, float z, int light) {
        float x1 = x + ICON, y1 = y + ICON;
        vc.addVertex(pose, x, y, z).setColor(-1).setUv(s.getU0(), s.getV0()).setLight(light);
        vc.addVertex(pose, x, y1, z).setColor(-1).setUv(s.getU0(), s.getV1()).setLight(light);
        vc.addVertex(pose, x1, y1, z).setColor(-1).setUv(s.getU1(), s.getV1()).setLight(light);
        vc.addVertex(pose, x1, y, z).setColor(-1).setUv(s.getU1(), s.getV0()).setLight(light);
    }
}
