package org.velvet.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.velvet.clothing.ClothingData;
import org.velvet.clothing.ClothingRegistry;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.ArrayList;
import java.util.List;

public class ClothingCustomizationScreen extends Screen {

    private final AbstractGirlEntity girl;
    private final List<ClothingData> available;
    private int selectedIndex = 0;
    private final List<String> activeIds = new ArrayList<>();

    public ClothingCustomizationScreen(AbstractGirlEntity girl) {
        super(Component.literal("Customize Outfit"));
        this.girl = girl;
        this.available = ClothingRegistry.forGirl(girl.getGirlType());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1))
                .bounds(cx - 180, cy - 10, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1))
                .bounds(cx - 20, cy - 10, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Toggle"), b -> toggleCurrent())
                .bounds(cx - 60, cy + 20, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 20, cy + 20, 60, 20).build());
    }

    private void cycle(int dir) {
        if (available.isEmpty()) return;
        selectedIndex = (selectedIndex + dir + available.size()) % available.size();
    }

    private void toggleCurrent() {
        if (available.isEmpty()) return;
        ClothingData current = available.get(selectedIndex);
        if (activeIds.contains(current.getId())) activeIds.remove(current.getId());
        else activeIds.add(current.getId());
    }

    @Override
    public void render(GuiGraphics gfx, int mousx, int mouseY, float delta) {
        this.renderBackground(gfx, mousx, mouseY, delta);
        gfx.drawCenteredString(this.font, "Customize: " + girl.getName().getString(),
                this.width / 2, 30, 0xFFFFFF);

        if (!available.isEmpty()) {
            ClothingData current = available.get(selectedIndex);
            String label = current.getId() + " [" + selectedIndex + "/" + (available.size() - 1) + "]";
            gfx.drawCenteredString(this.font, label, this.width / 2, this.height / 2 - 30, 0xFFFF00);
            boolean on = activeIds.contains(current.getId());
            gfx.drawCenteredString(this.font, on ? "ACTIVE" : "inactive",
                    this.width / 2, this.height / 2 + 5, on ? 0x00FF00 : 0x888888);
        } else {
            gfx.drawCenteredString(this.font, "No custom clothing available",
                    this.width / 2, this.height / 2, 0xFF5555);
        }

        super.render(gfx, mousx, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}