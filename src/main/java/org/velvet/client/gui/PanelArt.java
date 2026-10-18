package org.velvet.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Vanilla looking container art drawn with plain fills (no texture), shared by the girl's menu and the editor:
 * the raised panel, sunken boxes and slots, and tabs along the panel's top edge.
 */
public final class PanelArt {
    private PanelArt() {}

    public static final int PANEL = 0xFFC6C6C6;
    public static final int LIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int DARK = 0xFF373737;
    public static final int SLOT = 0xFF8B8B8B;
    public static final int OUTLINE = 0xFF000000;
    public static final int LABEL = 0xFF404040;
    public static final int MUTED = 0xFF707070;
    private static final int CLOSED_TAB = 0xFFA8A8A8;

    public static final int TAB_W = 28, TAB_H = 24, TAB_GAP = 1;
    /** How far the tabs stick out above the panel. */
    public static final int TAB_RISE = 21;

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, SHADOW);
    }

    /** A slot frame for an item drawn at (x + 1, y + 1). */
    public static void drawSlot(GuiGraphics g, int x, int y) {
        drawInset(g, x + 1, y + 1, 16, 16, SLOT);
    }

    /** Sunken box: dark top-left edge, light bottom-right edge. */
    public static void drawInset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x - 1, y - 1, x + w, y, DARK);
        g.fill(x - 1, y - 1, x, y + h, DARK);
        g.fill(x, y + h, x + w + 1, y + h + 1, LIGHT);
        g.fill(x + w, y, x + w + 1, y + h + 1, LIGHT);
    }

    /**
     * A tab at {@code x} above a panel whose top edge is {@code panelTop}: the open one joins the panel, the
     * others sit behind it a little lower and darker. Returns where its 16x16 icon goes (x, y).
     */
    public static int[] drawTab(GuiGraphics g, int x, int panelTop, boolean open) {
        int y = panelTop - TAB_RISE + (open ? 0 : 3);
        int bottom = panelTop + (open ? 3 : 0);
        int fill = open ? PANEL : CLOSED_TAB;
        g.fill(x + 1, y, x + TAB_W - 1, bottom, OUTLINE);
        g.fill(x, y + 1, x + TAB_W, bottom, OUTLINE);
        g.fill(x + 1, y + 1, x + TAB_W - 1, bottom, fill);
        g.fill(x + 1, y + 1, x + TAB_W - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, bottom, open ? LIGHT : fill);
        g.fill(x + TAB_W - 3, y + 2, x + TAB_W - 1, bottom, open ? SHADOW : fill);
        return new int[]{x + (TAB_W - 16) / 2, y + (TAB_H - 16) / 2 - (open ? 0 : 1)};
    }

    /** Over the open tab's bottom outline, so it joins the panel; call after drawing the panel and that tab. */
    public static void joinOpenTab(GuiGraphics g, int x, int panelTop) {
        g.fill(x + 3, panelTop, x + TAB_W - 3, panelTop + 3, PANEL);
    }

    /** Index of the tab under the mouse, or -1. Tabs start at {@code left} and are {@code count} wide. */
    public static int tabAt(double mouseX, double mouseY, int left, int panelTop, int count) {
        if (mouseY < panelTop - TAB_RISE || mouseY >= panelTop || mouseX < left) return -1;
        int i = (int) ((mouseX - left) / (TAB_W + TAB_GAP));
        return i < count && mouseX - left - i * (TAB_W + TAB_GAP) < TAB_W ? i : -1;
    }

    public static int tabX(int left, int index) {
        return left + index * (TAB_W + TAB_GAP);
    }
}
