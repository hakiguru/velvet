package org.velvetlite.client.gui.editor;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One tab of the NPC editor that does more than show widgets: it lays out its widgets in its part of the
 * panel (through {@link Host#add}), draws what isn't a widget and takes the input the widgets don't.
 */
public interface EditorTab {

    interface Host {
        <W extends net.minecraft.client.gui.components.AbstractWidget> W add(W widget);

        net.minecraft.client.gui.Font font();

        EditorState state();

        /** Lays the widgets out again (a panel was shown or hidden). */
        void rebuild();
    }

    /** Lays out the widgets in the box (x, y, w, h); called again when the screen is resized. */
    void init(Host host, int x, int y, int w, int h);

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Drawn after the widgets (tooltips, things over them). */
    default void renderOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** The tab was opened; its widgets have just been made visible. */
    default void onShow() {}

    default void onHide() {}

    default void tick() {}

    default boolean mouseClicked(double x, double y, int button) {
        return false;
    }

    default boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return false;
    }

    default boolean mouseReleased(double x, double y, int button) {
        return false;
    }

    default boolean mouseScrolled(double x, double y, double scroll) {
        return false;
    }

    default boolean keyPressed(int key, int scanCode, int modifiers) {
        return false;
    }
}
