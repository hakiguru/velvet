package org.velvetlite.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import org.velvetlite.VelvetLite;

public class HornyMeterOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            VelvetLite.MODID, "textures/gui/hornymeter.png");

    public static boolean active = false;
    public static double meter = 0.0;

    public static void activate() { active = true; }
    public static void deactivate() { active = false; meter = 0.0; }
    public static void add(double value) { meter = Math.min(1.0, meter + value); }

    public static void update(float value, float max, int action) {
        meter = value;
        if (max > 0) meter = Math.min(1.0, value / max);
    }

    @Override
    public void render(GuiGraphics gfx, DeltaTracker delta) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenW = gfx.guiWidth();
        int screenH = gfx.guiHeight();
        int barW = 100;
        int barH = 8;
        int x = screenW / 2 - barW / 2;
        int y = screenH - 60;

        gfx.blit(TEX, x, y, 0, 0, barW, barH, barW, barH * 2);
        int filled = (int) (meter * barW);
        gfx.blit(TEX, x, y, 0, barH, filled, barH, barW, barH * 2);
    }
}