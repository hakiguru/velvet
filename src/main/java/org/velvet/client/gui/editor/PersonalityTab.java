package org.velvet.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.velvet.client.gui.RangeSlider;
import org.velvet.entity.girl.psyche.Personality;
import org.velvet.entity.girl.psyche.Trait;

import java.util.ArrayList;
import java.util.List;

import static org.velvet.client.gui.PanelArt.*;

/**
 * Debug tool only: her character, every trait from 0.01 to 1.00 (saved to her personality file with "Save"),
 * and a button to roll a new one. Scrolls when there isn't room for all of them.
 */
public final class PersonalityTab implements EditorTab {

    private static final int ROW = 18, SLIDER_H = 16;

    private Host host;
    private Font font;
    private int x, y, w, h;
    private final List<RangeSlider> sliders = new ArrayList<>();
    private int scroll;

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        sliders.clear();
        Personality p = host.state().personality;
        if (p == null) return;
        int columns = w >= 420 ? 3 : 2;
        int colW = (w - (columns - 1) * 6) / columns;
        Trait[] traits = Trait.values();
        int rows = (traits.length + columns - 1) / columns;
        int visibleRows = Math.max(1, (h - 30) / ROW);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - visibleRows));
        for (int i = 0; i < traits.length; i++) {
            Trait t = traits[i];
            int col = i / rows, row = i % rows - scroll;
            // only the rows that fit: the wheel scrolls through the others
            if (row < 0 || row >= visibleRows) continue;
            RangeSlider slider = host.add(new RangeSlider(x + col * (colW + 6), y + 14 + row * ROW, colW, SLIDER_H, 0.01, 1.0, 0.01,
                    () -> host.state().personality.get(t), v -> {
                        host.state().personality.set(t, (float) v);
                        host.state().personalityChanged = true;
                        host.state().dirty = true;
                    }, v -> Component.translatable(t.translationKey()).append(String.format(java.util.Locale.ROOT, ": %.2f", v))));
            sliders.add(slider);
        }
        host.add(Button.builder(Component.translatable("editor.velvet.personality.random"), b -> {
            host.state().personality = Personality.random(RandomSource.create());
            host.state().personalityChanged = true;
            host.state().dirty = true;
            host.rebuild();
        }).bounds(x + w - 90, y + h - 16, 90, 16).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx < x || mx > x + w || my < y || my > y + h) return false;
        scroll -= (int) Math.signum(amount);
        host.rebuild();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(font, Component.translatable("editor.velvet.personality.hint"), x, y + 1, MUTED, false);
    }
}
