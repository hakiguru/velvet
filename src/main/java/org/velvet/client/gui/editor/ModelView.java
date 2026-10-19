package org.velvet.client.gui.editor;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.render.AbstractGirlRenderer;
import org.velvet.entity.girl.AbstractGirlEntity;

/**
 * Draws a girl into a box of the GUI with a camera of its own: turned by yaw and pitch around a point of her
 * body, zoomed and shifted. Optionally with a floor grid (red line = X, blue line = Z, one cell per block),
 * and with a {@link ModelPicker} collecting where her bones end up.
 */
public final class ModelView {

    private ModelView() {}

    private static final int GRID = 0x60FFFFFF, GRID_AXIS_X = 0xC0E05050, GRID_AXIS_Z = 0xC05070E0;

    /**
     * @param focus how high up her body the camera turns around, 0 = feet, 1 = top of her head
     * @param fill  how much of the box's height her whole body takes at zoom 1
     */
    public static void render(GuiGraphics g, AbstractGirlEntity girl, int x0, int y0, int x1, int y1,
                              float yaw, float pitch, float zoom, float panX, float panY, float focus, float fill,
                              boolean grid, @Nullable ModelPicker picker, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float height = girl.getBbHeight();
        float scale = (y1 - y0) * fill / height * zoom;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0F + panX, (y0 + y1) / 2.0F + panY, 400);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(0, -height * focus, 0);

        if (grid) drawGrid(g, pose.last());

        // face the same way whatever she was doing; restored afterwards like the vanilla inventory preview
        float bodyRot = girl.yBodyRot, bodyRotO = girl.yBodyRotO, yRot = girl.getYRot(), xRot = girl.getXRot();
        float headRot = girl.yHeadRot, headRotO = girl.yHeadRotO;
        girl.yBodyRot = girl.yBodyRotO = 180;
        girl.setYRot(180);
        girl.setXRot(0);
        girl.yHeadRot = girl.yHeadRotO = 180;
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        AbstractGirlRenderer.renderingInGui = true;
        ModelPicker.active = picker;
        if (picker != null) picker.clear();
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(girl, 0, 0, 0, 0, partialTick, pose, g.bufferSource(), 0xF000F0));
            g.flush();
        } finally {
            ModelPicker.active = null;
            AbstractGirlRenderer.renderingInGui = false;
            dispatcher.setRenderShadow(true);
            girl.yBodyRot = bodyRot;
            girl.yBodyRotO = bodyRotO;
            girl.setYRot(yRot);
            girl.setXRot(xRot);
            girl.yHeadRot = headRot;
            girl.yHeadRotO = headRotO;
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    private static void drawGrid(GuiGraphics g, PoseStack.Pose pose) {
        VertexConsumer lines = g.bufferSource().getBuffer(RenderType.lines());
        int n = 2;
        for (int i = -n; i <= n; i++) {
            line(lines, pose, -n, i, n, i, i == 0 ? GRID_AXIS_X : GRID);  // along X
            line(lines, pose, i, -n, i, n, i == 0 ? GRID_AXIS_Z : GRID);  // along Z
        }
        g.flush();
    }

    /** The lines shader widens a line across its normal, which has to be turned like the line itself. */
    private static void line(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, int colour) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        vc.addVertex(pose, x0, 0, z0).setColor(colour).setNormal(pose, dx / len, 0, dz / len);
        vc.addVertex(pose, x1, 0, z1).setColor(colour).setNormal(pose, dx / len, 0, dz / len);
    }

    /** A screen-space line of some thickness, for handles drawn over the view. */
    public static void screenLine(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int colour) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.5F) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        var m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.addVertex(m, x0 + nx, y0 + ny, 0).setColor(colour);
        vc.addVertex(m, x0 - nx, y0 - ny, 0).setColor(colour);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(colour);
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(colour);
        // and the other way round, whichever way the faces are culled
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(colour);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(colour);
        vc.addVertex(m, x0 - nx, y0 - ny, 0).setColor(colour);
        vc.addVertex(m, x0 + nx, y0 + ny, 0).setColor(colour);
    }

    /** A filled screen-space triangle (both windings, whichever way the faces are culled). */
    public static void screenTriangle(GuiGraphics g, float ax, float ay, float bx, float by, float cx, float cy, int colour) {
        var m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.addVertex(m, ax, ay, 0).setColor(colour);
        vc.addVertex(m, bx, by, 0).setColor(colour);
        vc.addVertex(m, cx, cy, 0).setColor(colour);
        vc.addVertex(m, cx, cy, 0).setColor(colour);
        vc.addVertex(m, cx, cy, 0).setColor(colour);
        vc.addVertex(m, bx, by, 0).setColor(colour);
        vc.addVertex(m, ax, ay, 0).setColor(colour);
        vc.addVertex(m, ax, ay, 0).setColor(colour);
    }
}
