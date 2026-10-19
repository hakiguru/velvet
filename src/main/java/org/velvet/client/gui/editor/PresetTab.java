package org.velvet.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.gui.RangeSlider;
import org.velvet.client.preset.PresetEditing;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlRegistry;
import org.velvet.entity.girl.GirlType;
import org.velvet.preset.PresetMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The preset tab: every model she can wear, as cards with the model turning slowly, the custom ones after
 * the built-in ones, and a card to make a new one. A click puts it on her (in the editor's view right away,
 * on her once saved); her name, bone changes and skin stay.
 * <p>
 * A custom preset has a name, a built-in preset it's made from (whatever it doesn't have of its own, model,
 * texture, animations, voice, comes from there), a height, a voice and whether it undresses; its model,
 * texture, animations and sounds are made on the model, texture, animation and sound tabs. With a custom
 * preset picked, its settings can be changed or it can be deleted here.
 */
public final class PresetTab implements EditorTab {

    private static final int COLUMNS = 4, GAP = 6, LABEL_H = 22, BAR = 20;
    private static final List<String> VOICES = List.of("", "elsie", "ellie", "bia", "luna", "allie", "own");

    private Host host;
    private Font font;
    private int x, y, w, h;
    private final Map<GirlType, AbstractGirlEntity> standIns = new HashMap<>();
    private float spin;

    /** The form for a new preset (or the settings of the picked custom one) instead of the cards. */
    private boolean form;
    @Nullable private GirlType editing;
    private String formName = "";
    private GirlType formBase = GirlType.JENNY;
    private float formHeight = 1.8F;
    private String formVoice = "elsie";
    private boolean formStrips = true, formTranslucent;
    private long deleteArmedUntil;

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y + 12;
        this.w = w;
        this.h = h - 12;
        Minecraft mc = Minecraft.getInstance();
        for (GirlType t : GirlType.values()) {
            if (mc.level == null || standIns.containsKey(t)) continue;
            AbstractGirlEntity e = GirlRegistry.NPC.get().create(mc.level);
            if (e == null) continue;
            e.setGirlType(t);
            standIns.put(t, e);
        }
        if (form) initForm();
        else initBar();
    }

    private List<GirlType> presets() {
        return GirlType.values();
    }

    /** Cards: the presets and the "new" one. */
    private int count() {
        return presets().size() + 1;
    }

    private int rows() {
        return (count() + COLUMNS - 1) / COLUMNS;
    }

    private int gridH() {
        return h - (host.state().preset.isCustom() ? BAR : 0);
    }

    private int cardW() {
        return (w - (COLUMNS - 1) * GAP) / COLUMNS;
    }

    private int cardH() {
        return Math.max(30, (gridH() - (rows() - 1) * GAP) / rows());
    }

    /** Index of the card under the mouse (the last one is "new"), or -1. */
    private int at(double mx, double my) {
        int cw = cardW(), ch = cardH();
        for (int i = 0; i < count(); i++) {
            int cx = x + i % COLUMNS * (cw + GAP), cy = y + i / COLUMNS * (ch + GAP);
            if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) return i;
        }
        return -1;
    }

    // ---- The picked custom preset: settings, delete ----

    private void initBar() {
        GirlType picked = host.state().preset;
        if (!picked.isCustom()) return;
        int by = y + h - BAR + 3, bw = 90;
        host.add(Button.builder(Component.translatable("editor.velvet.preset.settings"), b -> openForm(picked))
                .bounds(x + w - 2 * bw - 3, by, bw, 16).build());
        boolean armed = System.currentTimeMillis() < deleteArmedUntil;
        host.add(Button.builder(Component.translatable(armed ? "editor.velvet.debug.delete.sure" : "editor.velvet.preset.delete")
                .withStyle(armed ? ChatFormatting.RED : ChatFormatting.RESET), b -> {
            if (System.currentTimeMillis() < deleteArmedUntil) {
                PresetEditing.delete(picked);
                host.state().setPreset(picked.base());
                deleteArmedUntil = 0;
            } else {
                deleteArmedUntil = System.currentTimeMillis() + 3000;
            }
            host.rebuild();
        }).bounds(x + w - bw, by, bw, 16).build());
    }

    // ---- The form ----

    private void openForm(@Nullable GirlType custom) {
        form = true;
        editing = custom;
        if (custom != null) {
            formName = custom.displayName().getString();
            formBase = custom.base();
            formHeight = custom.height();
            String v = custom.voice();
            formVoice = v == null ? "" : v.startsWith("custom:") ? "own" : v;
            formStrips = custom.strips();
            formTranslucent = custom.translucent();
        } else {
            GirlType from = host.state().preset.base();
            formName = "";
            formBase = from;
            formHeight = from.height();
            formVoice = from.voice() == null ? "" : from.voice();
            formStrips = from.strips();
            formTranslucent = from.translucent();
        }
        host.rebuild();
    }

    private void initForm() {
        int fx = x, fw = Math.min(w, 260), row = 22, fy = y + 4;
        EditBox name = host.add(new EditBox(font, fx + 80, fy, fw - 80, 16, Component.translatable("editor.velvet.preset.name")));
        name.setMaxLength(32);
        name.setValue(formName);
        name.setHint(Component.translatable("editor.velvet.preset.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        name.setResponder(v -> formName = v);
        List<GirlType> builtIns = new ArrayList<>();
        for (GirlType t : GirlType.values()) if (!t.isCustom()) builtIns.add(t);
        host.add(Button.builder(Component.translatable("editor.velvet.preset.base", formBase.displayName()), b -> {
            formBase = builtIns.get((builtIns.indexOf(formBase) + 1) % builtIns.size());
            host.rebuild();
        }).bounds(fx, fy + row, fw, 16).build());
        host.add(new RangeSlider(fx, fy + 2 * row, fw, 16, PresetMeta.MIN_HEIGHT, PresetMeta.MAX_HEIGHT, 0.02,
                () -> formHeight, v -> formHeight = (float) v,
                v -> Component.translatable("editor.velvet.preset.height", String.format(java.util.Locale.ROOT, "%.2f", v))));
        host.add(Button.builder(Component.translatable("editor.velvet.preset.voice", voiceName(formVoice)), b -> {
            formVoice = VOICES.get((VOICES.indexOf(formVoice) + 1) % VOICES.size());
            host.rebuild();
        }).bounds(fx, fy + 3 * row, fw, 16).build());
        host.add(Checkbox.builder(Component.translatable("editor.velvet.preset.strips"), font).pos(fx, fy + 4 * row)
                .selected(formStrips).onValueChange((cb, v) -> formStrips = v).build());
        host.add(Checkbox.builder(Component.translatable("editor.velvet.preset.translucent"), font).pos(fx + fw / 2, fy + 4 * row)
                .selected(formTranslucent).onValueChange((cb, v) -> formTranslucent = v).build());
        int by = fy + 5 * row + 6;
        host.add(Button.builder(Component.translatable(editing == null ? "editor.velvet.preset.create" : "editor.velvet.preset.apply"), b -> submit())
                .bounds(fx, by, fw / 2 - 2, 18).build());
        host.add(Button.builder(Component.translatable("gui.cancel"), b -> {
            form = false;
            host.rebuild();
        }).bounds(fx + fw / 2 + 2, by, fw / 2 - 2, 18).build());
    }

    private Component voiceName(String voice) {
        return voice.isEmpty() ? Component.translatable("editor.velvet.preset.voice.none")
                : voice.equals("own") ? Component.translatable("editor.velvet.preset.voice.own")
                : Component.translatable("editor.velvet.preset.voice.of", GirlType.values().stream()
                        .filter(t -> !t.isCustom() && voice.equals(t.voice())).findFirst().map(GirlType::displayName)
                        .orElse(Component.literal(voice)));
    }

    private void submit() {
        String name = formName.isBlank() ? formBase.displayName().getString() + " 2" : formName.trim();
        if (editing != null) {
            PresetEditing.update(editing, new PresetMeta(editing.id(), name, formBase.id(), formHeight, formVoice, formStrips, formTranslucent));
        } else {
            PresetEditing.create(name, formBase, formHeight, formVoice, formStrips, formTranslucent, made -> {
                host.state().setPreset(made);
                host.rebuild();
            });
        }
        form = false;
        host.rebuild();
    }

    // ---- Input, drawing ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (form || button != 0) return false;
        int i = at(mx, my);
        if (i < 0) return false;
        if (i == presets().size()) {
            openForm(null);
            return true;
        }
        host.state().setPreset(presets().get(i));
        host.rebuild();
        return true;
    }

    @Override
    public void tick() {
        spin += 2.5F;
        for (AbstractGirlEntity e : standIns.values()) e.tickCount++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (form) {
            g.drawString(font, Component.translatable(editing == null ? "editor.velvet.preset.new_title" : "editor.velvet.preset.edit_title"),
                    x, y - 12, LABEL, false);
            g.drawString(font, Component.translatable("editor.velvet.preset.name"), x, y + 8, LABEL, false);
            int ty = y + 5 * 22 + 34;
            for (var line : font.split(Component.translatable("editor.velvet.preset.form_hint"), w)) {
                if (ty > y + h - 9) break;
                g.drawString(font, line, x, ty, MUTED, false);
                ty += 10;
            }
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet.preset.hint").getString(), w), x, y - 12, MUTED, false);
        int cw = cardW(), ch = cardH();
        int hovered = at(mouseX, mouseY);
        List<GirlType> all = presets();
        for (int i = 0; i < count(); i++) {
            int cx = x + i % COLUMNS * (cw + GAP), cy = y + i / COLUMNS * (ch + GAP);
            if (i == all.size()) {
                renderNewCard(g, cx, cy, cw, ch, i == hovered);
                continue;
            }
            GirlType t = all.get(i);
            boolean selected = host.state().preset == t;
            int bg = selected ? 0xFF5A5A9A : i == hovered ? 0xFF7A7A7A : t.isCustom() ? 0xFF64706B : 0xFF6B6B6B;
            drawInset(g, cx, cy, cw, ch, bg);
            AbstractGirlEntity e = standIns.get(t);
            int viewBottom = cy + ch - LABEL_H;
            if (e != null && viewBottom > cy + 8) {
                g.enableScissor(cx, cy, cx + cw, viewBottom);
                ModelView.render(g, e, cx, cy + 2, cx + cw, viewBottom, 25 + (i == hovered || selected ? spin : 0), 8, 1,
                        0, 0, 0.5F, 0.9F, false, null, partialTick);
                g.disableScissor();
            }
            g.drawCenteredString(font, font.plainSubstrByWidth(t.displayName().getString(), cw - 4), cx + cw / 2, viewBottom + 2,
                    selected ? 0xFFFFFFFF : 0xFFEFEFEF);
            Component sub = t.isCustom() ? Component.translatable("editor.velvet.preset.custom_of", t.base().displayName())
                    : Component.translatable("editor.velvet.preset.built_in");
            g.drawCenteredString(font, font.plainSubstrByWidth(sub.getString(), cw - 4), cx + cw / 2, viewBottom + 12,
                    selected ? 0xFFD8D8FF : 0xFFB8B8B8);
            if (selected) {
                g.fill(cx - 1, cy - 1, cx + cw + 1, cy, 0xFFFFFFFF);
                g.fill(cx - 1, cy + ch, cx + cw + 1, cy + ch + 1, 0xFFFFFFFF);
                g.fill(cx - 1, cy, cx, cy + ch, 0xFFFFFFFF);
                g.fill(cx + cw, cy, cx + cw + 1, cy + ch, 0xFFFFFFFF);
            }
        }
        if (host.state().preset.isCustom()) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet.preset.custom_hint").getString(), w - 190),
                    x, y + h - BAR + 7, MUTED, false);
        }
        spin = Mth.wrapDegrees(spin);
    }

    private void renderNewCard(GuiGraphics g, int cx, int cy, int cw, int ch, boolean hover) {
        drawInset(g, cx, cy, cw, ch, hover ? 0xFF8FC48F : 0xFF7DAE7D);
        g.drawCenteredString(font, "+", cx + cw / 2, cy + ch / 2 - 10, 0xFFFFFFFF);
        g.drawCenteredString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet.preset.new").getString(), cw - 4),
                cx + cw / 2, cy + ch / 2 + 2, 0xFF1E3A1E);
    }
}
