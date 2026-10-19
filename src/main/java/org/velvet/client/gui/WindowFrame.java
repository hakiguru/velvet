package org.velvet.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * A window that can be resized like a desktop one: by its right and bottom edges or the corner between them
 * (the mouse turns into arrows there), within what fits on the screen. Each window, and each of its tabs, has
 * a size of its own that fits what it shows; one the player resized keeps that size (see {@link WindowSizes}),
 * on the screen, whatever the GUI scale. When the game window gets smaller (leaving full screen, a smaller
 * window, a bigger GUI scale) everything is fitted in again; a minimized window changes nothing.
 * <p>
 * The owning screen lays itself out in {@link #x}, {@link #y}, {@link #w}, {@link #h}, calls {@link #fit}
 * from its init, hands it the mouse first and draws the grip with {@link #renderGrip}.
 */
public final class WindowFrame {

    /** How far from an edge the mouse grabs it. */
    private static final int EDGE = 3, CORNER = 7;
    /** Room kept free around a window. */
    private static final int MARGIN = 4;

    private static final int NONE = 0, RIGHT = 1, BOTTOM = 2, BOTH = 3;
    private static long cursorEw, cursorNs, cursorNwse;
    private static long shown;

    public int x, y, w, h;
    private String key = "";
    private int dragging = NONE;
    private double grabW, grabH, grabX, grabY;
    private final Runnable relayout;
    private int lastW, lastH;

    /** {@code relayout}: lays the screen out again for the new size (while dragging, too). */
    public WindowFrame(Runnable relayout) {
        this.relayout = relayout;
    }

    /**
     * Sizes and places the window: what the player gave {@code key} last time, else {@code prefW} × {@code prefH};
     * never smaller than the minimum or bigger than the screen (less what's kept for things around it).
     * {@code extraLeft}/{@code extraTop}: room for things outside the window (tabs, a list), centred with it.
     */
    public void fit(String key, int screenW, int screenH, int prefW, int prefH, int minW, int minH,
                    int extraLeft, int extraTop) {
        this.key = key;
        // minimized: nothing to fit into
        if (screenW <= 0 || screenH <= 0) return;
        int maxW = Math.max(minW, screenW - extraLeft - 2 * MARGIN);
        int maxH = Math.max(minH, screenH - extraTop - 2 * MARGIN);
        int[] saved = WindowSizes.get(key);
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int wantW = saved != null ? (int) Math.round(saved[0] / scale) : prefW;
        int wantH = saved != null ? (int) Math.round(saved[1] / scale) : prefH;
        if (dragging == NONE) {
            w = Mth.clamp(wantW, Math.min(minW, maxW), maxW);
            h = Mth.clamp(wantH, Math.min(minH, maxH), maxH);
        }
        x = (screenW - w + extraLeft) / 2;
        y = (screenH - h + extraTop) / 2;
        minWidth = minW;
        minHeight = minH;
        maxWidth = maxW;
        maxHeight = maxH;
    }

    private int minWidth, minHeight, maxWidth, maxHeight;

    private int zone(double mx, double my) {
        boolean right = mx >= x + w - EDGE && mx <= x + w + EDGE && my >= y && my <= y + h + EDGE;
        boolean bottom = my >= y + h - EDGE && my <= y + h + EDGE && mx >= x && mx <= x + w + EDGE;
        boolean corner = mx >= x + w - CORNER && mx <= x + w + EDGE && my >= y + h - CORNER && my <= y + h + EDGE;
        if (corner || right && bottom) return BOTH;
        if (right) return RIGHT;
        if (bottom) return BOTTOM;
        return NONE;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int z = zone(mx, my);
        if (z == NONE) return false;
        dragging = z;
        grabW = w;
        grabH = h;
        grabX = mx;
        grabY = my;
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (dragging == NONE) return false;
        // the window stays centred, so it grows twice as fast as the mouse moves away from its centre
        if ((dragging & RIGHT) != 0) w = Mth.clamp((int) Math.round(grabW + 2 * (mx - grabX)), minWidth, maxWidth);
        if ((dragging & BOTTOM) != 0) h = Mth.clamp((int) Math.round(grabH + 2 * (my - grabY)), minHeight, maxHeight);
        if (Math.abs(w - lastW) + Math.abs(h - lastH) >= 2) {
            lastW = w;
            lastH = h;
            relayout.run();
        }
        return true;
    }

    public boolean mouseReleased() {
        if (dragging == NONE) return false;
        dragging = NONE;
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        WindowSizes.put(key, (int) Math.round(w * scale), (int) Math.round(h * scale));
        relayout.run();
        return true;
    }

    public boolean isDragging() {
        return dragging != NONE;
    }

    /** Double click on the grip: back to the window's own size. */
    public void reset() {
        WindowSizes.forget(key);
        relayout.run();
    }

    /** The grip in the corner, and the mouse turned into arrows over an edge. */
    public void renderGrip(GuiGraphics g, int mouseX, int mouseY) {
        int gx = x + w - 2, gy = y + h - 2;
        for (int i = 0; i < 3; i++) {
            int d = 3 + i * 3;
            g.fill(gx - d, gy - 1, gx - d + 1, gy, 0xFF8B8B8B);
            g.fill(gx - 1, gy - d, gx, gy - d + 1, 0xFF8B8B8B);
            for (int k = 0; k < d; k++) g.fill(gx - d + k, gy - 1 - k, gx - d + k + 1, gy - k, 0x608B8B8B);
        }
        updateCursor(dragging != NONE ? dragging : zone(mouseX, mouseY));
    }

    private static void updateCursor(int z) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        long want = switch (z) {
            case RIGHT -> cursorEw != 0 ? cursorEw : (cursorEw = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR));
            case BOTTOM -> cursorNs != 0 ? cursorNs : (cursorNs = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR));
            case BOTH -> cursorNwse != 0 ? cursorNwse : (cursorNwse = nwse());
            default -> 0L;
        };
        if (want != shown) {
            GLFW.glfwSetCursor(window, want);
            shown = want;
        }
    }

    /** The diagonal arrows; older GLFW versions don't have them, then the crosshair. */
    private static long nwse() {
        long c = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
        return c != 0 ? c : GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
    }

    /** The normal mouse again (the screen closes). */
    public static void resetCursor() {
        updateCursor(NONE);
    }
}
