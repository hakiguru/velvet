package org.velvet.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.psyche.Emotion;
import org.velvet.entity.girl.psyche.Mood;
import org.velvet.entity.girl.psyche.Personality;
import org.velvet.entity.girl.psyche.Trait;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.EditorNpcActionPacket;

import java.util.Arrays;
import java.util.Comparator;

import static org.velvet.client.gui.PanelArt.*;

/**
 * How she feels, live: each emotion as a bar in tenths (as the game keeps them), adrenaline, her mood in a
 * word and what her character is most. In the debug tool's editor the bars can be dragged to set how she
 * feels right now (to try out how she reacts).
 */
public final class EmotionsTab implements EditorTab {

    private static final int BAR_H = 10, STEP = 16, LABEL_W = 70;

    @Nullable private final AbstractGirlEntity girl;
    @Nullable private final Personality personality;
    private Host host;
    private Font font;
    private int x, y, w, h;
    private int dragging = -1;

    public EmotionsTab(@Nullable AbstractGirlEntity girl, @Nullable Personality personality) {
        this.girl = girl;
        this.personality = personality;
    }

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    private int rows() {
        return Emotion.values().length + 1;
    }

    private int barX() {
        return x + LABEL_W;
    }

    private int barW() {
        return Math.max(40, Math.min(w - LABEL_W - 30, 220));
    }

    private int rowY(int i) {
        return y + 16 + i * STEP;
    }

    private float value(int i) {
        if (girl == null) return 0;
        return i < Emotion.values().length ? girl.getEmotion(Emotion.values()[i]) : girl.getAdrenaline();
    }

    private int rowAt(double mx, double my) {
        if (mx < barX() - 2 || mx > barX() + barW() + 2) return -1;
        for (int i = 0; i < rows(); i++) if (my >= rowY(i) - 2 && my < rowY(i) + BAR_H + 2) return i;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!host.state().debug || girl == null || button != 0) return false;
        dragging = rowAt(mx, my);
        if (dragging < 0) return false;
        set(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging < 0) return false;
        set(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging >= 0;
        dragging = -1;
        return was;
    }

    private void set(double mx) {
        float v = Emotion.tenth((float) ((mx - barX()) / barW()));
        NetworkHandler.sendToServer(new EditorNpcActionPacket(EditorNpcActionPacket.EMOTION, girl.getGirlUUID(), dragging, v));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (girl == null) {
            g.drawString(font, Component.translatable("editor.velvet.emotions.far"), x, y + 2, MUTED, false);
            return;
        }
        Mood mood = moodOf(girl);
        g.drawString(font, Component.translatable("editor.velvet.emotions.mood", mood.label()), x, y + 2, LABEL, false);
        int bx = barX(), bw = barW();
        for (int i = 0; i < rows(); i++) {
            boolean adrenaline = i == Emotion.values().length;
            Component name = adrenaline ? Component.translatable("emotion.velvet.adrenaline")
                    : Component.translatable(Emotion.values()[i].translationKey());
            int ry = rowY(i);
            g.drawString(font, name, x, ry + 1, LABEL, false);
            drawInset(g, bx, ry, bw, BAR_H, 0xFF6B6B6B);
            float v = value(i);
            int color = adrenaline ? 0xFFE04040 : Emotion.values()[i].color;
            if (v > 0) g.fill(bx, ry, bx + Math.round(bw * v), ry + BAR_H, color);
            // tenths
            for (int t = 1; t < 10; t++) g.fill(bx + bw * t / 10, ry + BAR_H - 2, bx + bw * t / 10 + 1, ry + BAR_H, 0x50000000);
            g.drawString(font, String.format(java.util.Locale.ROOT, "%.1f", v), bx + bw + 4, ry + 1, LABEL, false);
        }
        int ty = rowY(rows()) + 4;
        if (personality != null && ty + 20 < y + h) {
            g.drawString(font, Component.translatable("editor.velvet.emotions.character"), x, ty, LABEL, false);
            Trait[] sorted = Trait.values().clone();
            Arrays.sort(sorted, Comparator.comparingDouble(t -> -Math.abs(personality.get(t) - 0.5F)));
            StringBuilder line = new StringBuilder();
            int lineY = ty + 11;
            for (int i = 0; i < 8 && lineY + 9 < y + h; i++) {
                Trait t = sorted[i];
                String part = Component.translatable(t.translationKey()).getString() + String.format(java.util.Locale.ROOT, " %.2f", personality.get(t));
                if (font.width(line + (line.isEmpty() ? "" : " · ") + part) > w && !line.isEmpty()) {
                    g.drawString(font, line.toString(), x, lineY, MUTED, false);
                    lineY += 10;
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(" · ");
                line.append(part);
            }
            if (!line.isEmpty() && lineY + 9 < y + h) g.drawString(font, line.toString(), x, lineY, MUTED, false);
        }
        if (host.state().debug && rowAt(mouseX, mouseY) >= 0) {
            g.drawString(font, Component.translatable("editor.velvet.emotions.drag"), x, y + h - 9, MUTED, false);
        }
    }

    /** Her mood from the synced emotions (the client has no Psyche of hers). */
    private static Mood moodOf(AbstractGirlEntity girl) {
        return Mood.ofValues(girl::getEmotion, girl.getAdrenaline());
    }
}
