package org.velvet.client.gui.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlRegistry;
import org.velvet.entity.girl.GirlType;
import org.velvet.entity.girl.scene.SceneType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The preset tab: every model she can wear, as cards with the model turning slowly. A click puts it on her
 * (in the editor's view right away, on her once saved); her name, bone changes and skin stay. Each preset
 * comes with its own animations, voice and scenes.
 */
public final class PresetTab implements EditorTab {

    private static final int COLUMNS = 4, GAP = 6, LABEL_H = 22;

    private Host host;
    private Font font;
    private int x, y, w, h;
    private final Map<GirlType, AbstractGirlEntity> standIns = new EnumMap<>(GirlType.class);
    private float spin;

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y + 12;
        this.w = w;
        this.h = h - 12;
        if (standIns.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            for (GirlType t : GirlType.values()) {
                if (mc.level == null) break;
                AbstractGirlEntity e = GirlRegistry.NPC.get().create(mc.level);
                if (e == null) continue;
                e.setGirlType(t);
                standIns.put(t, e);
            }
        }
    }

    private int rows() {
        return (GirlType.values().length + COLUMNS - 1) / COLUMNS;
    }

    private int cardW() {
        return (w - (COLUMNS - 1) * GAP) / COLUMNS;
    }

    private int cardH() {
        return (h - (rows() - 1) * GAP) / rows();
    }

    @Nullable
    private GirlType at(double mx, double my) {
        int cw = cardW(), ch = cardH();
        for (int i = 0; i < GirlType.values().length; i++) {
            int cx = x + i % COLUMNS * (cw + GAP), cy = y + i / COLUMNS * (ch + GAP);
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) return GirlType.values()[i];
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        GirlType t = button == 0 ? at(mx, my) : null;
        if (t == null) return false;
        host.state().setPreset(t);
        return true;
    }

    @Override
    public void tick() {
        spin += 2.5F;
        for (AbstractGirlEntity e : standIns.values()) e.tickCount++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet.preset.hint").getString(), w), x, y - 12, MUTED, false);
        int cw = cardW(), ch = cardH();
        GirlType hovered = at(mouseX, mouseY);
        for (int i = 0; i < GirlType.values().length; i++) {
            GirlType t = GirlType.values()[i];
            int cx = x + i % COLUMNS * (cw + GAP), cy = y + i / COLUMNS * (ch + GAP);
            boolean selected = host.state().preset == t;
            int bg = selected ? 0xFF5A5A9A : t == hovered ? 0xFF7A7A7A : 0xFF6B6B6B;
            drawInset(g, cx, cy, cw, ch, bg);
            AbstractGirlEntity e = standIns.get(t);
            int viewBottom = cy + ch - LABEL_H;
            if (e != null) {
                g.enableScissor(cx, cy, cx + cw, viewBottom);
                ModelView.render(g, e, cx, cy + 2, cx + cw, viewBottom, 25 + (t == hovered || selected ? spin : 0), 8, 1,
                        0, 0, 0.5F, 0.9F, false, null, partialTick);
                g.disableScissor();
            }
            Component name = t.displayName();
            g.drawCenteredString(font, font.plainSubstrByWidth(name.getString(), cw - 4), cx + cw / 2, viewBottom + 2,
                    selected ? 0xFFFFFFFF : 0xFFEFEFEF);
            List<SceneType> scenes = SceneType.of(t);
            Component sub = Component.translatable("editor.velvet.preset.scenes", scenes.size());
            g.drawCenteredString(font, font.plainSubstrByWidth(sub.getString(), cw - 4), cx + cw / 2, viewBottom + 12,
                    selected ? 0xFFD8D8FF : 0xFFB8B8B8);
            if (selected) {
                g.fill(cx - 1, cy - 1, cx + cw + 1, cy, 0xFFFFFFFF);
                g.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, 0xFFFFFFFF);
                g.fill(cx - 1, cy, cx, cy + ch, 0xFFFFFFFF);
                g.fill(cx + cw, cy, cx + cw + 1, cy + ch, 0xFFFFFFFF);
            }
        }
        spin = Mth.wrapDegrees(spin);
    }
}
