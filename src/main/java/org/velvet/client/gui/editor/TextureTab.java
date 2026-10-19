package org.velvet.client.gui.editor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.gui.FileDialogs;
import org.velvet.client.gui.RangeSlider;
import org.velvet.client.preset.PresetCache;
import org.velvet.client.preset.PresetEditing;
import org.velvet.entity.girl.GirlType;
import org.velvet.preset.PresetFiles;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The texture tab: paint on a custom preset's texture, in the game. The texture on the left (wheel to zoom,
 * right drag to move it, a grid of the model's pixels when close), the tools on the right: pencil, eraser,
 * fill and colour picker, brush size, the colour (red, green, blue, opacity, hex, a palette), undo and redo,
 * and a PNG to load in or save out. The model shows every stroke at once; "Save" sends the texture to the
 * server. A built-in preset can't be painted on: an editable copy of it can.
 */
public final class TextureTab implements EditorTab {

    private enum Tool { PENCIL, ERASER, FILL, PICKER }

    private static final int TOOLS_W = 112;
    private static final int[] PALETTE = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFFFFD6C2, 0xFFE8B090, 0xFF8D5A3B,
            0xFF2B1B14, 0xFFF4E28A, 0xFFE05050, 0xFFE89AC0, 0xFF9A6AE0, 0xFF5A8AE8, 0xFF58C8A0, 0xFF60A040};
    private static final int UNDO_LIMIT = 40;

    private Host host;
    private Font font;
    private int x, y, w, h;
    private Tool tool = Tool.PENCIL;
    private int brush = 1;
    private int colour = 0xFFE05050;
    private float zoom = 0, panX, panY;
    private boolean painting, panning;
    private int lastPx = -1, lastPy = -1;
    /** The stroke going on changed the texture: it goes into the draft when it ends. */
    private boolean changed;
    private EditBox hexBox;

    /** The pixels a stroke changed, with what they were before (to undo it): packed position, colour. */
    private record Patch(long[] positions, int[] pixels) {}

    private final Deque<Patch> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private int sx0, sy0, sx1, sy1;
    /** During a stroke: every pixel it changed and what it was before. */
    private final java.util.LinkedHashMap<Long, Integer> before = new java.util.LinkedHashMap<>();
    @Nullable private GirlType undoFor;

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        GirlType type = host.state().preset;
        if (type != undoFor) {
            undo.clear();
            redo.clear();
            undoFor = type;
            zoom = 0;
        }
        if (!type.isCustom()) {
            host.add(Button.builder(Component.translatable("editor.velvet.copy_preset"), b -> PresetEditing.copyOf(type, made -> {
                host.state().setPreset(made);
                host.rebuild();
            })).bounds(x + (w - 180) / 2, y + h / 2, 180, 20).build());
            return;
        }
        int tx = x + w - TOOLS_W, ty = y;
        int bw = (TOOLS_W - 3) / 4;
        String[] icons = {"✎", "⌫", "◪", "⌖"};
        for (Tool t : Tool.values()) {
            Button b = host.add(Button.builder(Component.literal(icons[t.ordinal()]), btn -> tool = t)
                    .bounds(tx + t.ordinal() * (bw + 1), ty, bw, 16)
                    .tooltip(Tooltip.create(Component.translatable("editor.velvet.paint." + t.name().toLowerCase(Locale.ROOT)))).build());
            b.active = true;
        }
        ty += 20;
        host.add(new RangeSlider(tx, ty, TOOLS_W, 14, 1, 64, 1, () -> brush, v -> brush = (int) v,
                v -> Component.translatable("editor.velvet.paint.brush", (int) v)));
        ty += 18;
        String[] channels = {"red", "green", "blue", "alpha"};
        int[] shifts = {16, 8, 0, 24};
        for (int c = 0; c < 4; c++) {
            int shift = shifts[c];
            String name = channels[c];
            host.add(new RangeSlider(tx, ty, TOOLS_W, 13, 0, 255, 1, () -> (colour >>> shift) & 0xFF, v -> {
                colour = (colour & ~(0xFF << shift)) | ((int) v << shift);
                syncHex();
            }, v -> Component.translatable("editor.velvet.paint." + name, (int) v)));
            ty += 14;
        }
        hexBox = host.add(new EditBox(font, tx + 20, ty + 1, TOOLS_W - 20, 14, Component.literal("#RRGGBB")));
        hexBox.setMaxLength(9);
        syncHex();
        hexBox.setResponder(this::hexTyped);
        ty += 18;
        int sw = TOOLS_W / 8;
        for (int i = 0; i < PALETTE.length; i++) {
            int c = PALETTE[i];
            host.add(new Swatch(tx + i % 8 * sw, ty + i / 8 * sw, sw, c, () -> {
                colour = c;
                host.rebuild();
            }));
        }
        ty += 2 * sw + 4;
        int half = (TOOLS_W - 2) / 2;
        host.add(Button.builder(Component.literal("↶"), b -> undo()).bounds(tx, ty, half, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.undo"))).build());
        host.add(Button.builder(Component.literal("↷"), b -> redo()).bounds(tx + half + 2, ty, half, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.redo"))).build());
        ty += 16;
        host.add(Button.builder(Component.translatable("editor.velvet.import"), b -> importPng()).bounds(tx, ty, half, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.paint.import"))).build());
        host.add(Button.builder(Component.translatable("editor.velvet.export"), b -> exportPng()).bounds(tx + half + 2, ty, half, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.paint.export"))).build());
        ty += 16;
        host.add(Button.builder(Component.translatable("editor.velvet.paint.reset"), b -> resetToBase()).bounds(tx, ty, TOOLS_W, 14)
                .tooltip(Tooltip.create(Component.translatable("editor.velvet.paint.reset.tooltip"))).build());
    }

    private void syncHex() {
        if (hexBox != null) hexBox.setValue(String.format("#%08X", colour));
    }

    private void hexTyped(String text) {
        String t = text.startsWith("#") ? text.substring(1) : text;
        try {
            if (t.length() == 6) colour = 0xFF000000 | Integer.parseInt(t, 16);
            else if (t.length() == 8) colour = (int) Long.parseLong(t, 16);
        } catch (NumberFormatException ignored) {
        }
    }

    // ---- The texture ----

    /** Her own texture to paint on; the base preset's is copied in first if she hasn't got one. */
    @Nullable
    private DynamicTexture texture(boolean create) {
        GirlType type = host.state().preset;
        if (!type.isCustom()) return null;
        if (create && !PresetCache.has(type, PresetFiles.TEXTURE)) {
            PresetCache.draft(type.id(), PresetFiles.TEXTURE, PresetEditing.texturePng(type));
        }
        return PresetCache.texture(type);
    }

    private int[] view() {
        return new int[]{x, y, x + w - TOOLS_W - 6, y + h};
    }

    /** Pixels on screen per texture pixel. */
    private float scale(NativeImage img) {
        int[] v = view();
        float fit = Math.min((v[2] - v[0]) / (float) img.getWidth(), (v[3] - v[1]) / (float) img.getHeight());
        return fit * (float) Math.pow(2, zoom);
    }

    private float originX(NativeImage img) {
        int[] v = view();
        return (v[0] + v[2]) / 2F - img.getWidth() * scale(img) / 2 + panX;
    }

    private float originY(NativeImage img) {
        int[] v = view();
        return (v[1] + v[3]) / 2F - img.getHeight() * scale(img) / 2 + panY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int[] v = view();
        if (!host.state().preset.isCustom() || mx < v[0] || mx >= v[2] || my < v[1] || my >= v[3]) return false;
        if (button == 1 || button == 2) {
            panning = true;
            return true;
        }
        DynamicTexture tex = texture(true);
        if (tex == null || tex.getPixels() == null) return true;
        NativeImage img = tex.getPixels();
        int px = (int) Math.floor((mx - originX(img)) / scale(img)), py = (int) Math.floor((my - originY(img)) / scale(img));
        if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) return true;
        switch (tool) {
            case PICKER -> {
                colour = abgrToArgb(img.getPixelRGBA(px, py));
                host.rebuild();
            }
            case FILL -> {
                beginStroke(img, 0, 0, img.getWidth(), img.getHeight());
                fill(img, px, py);
                endStroke(tex);
            }
            default -> {
                painting = true;
                beginStroke(img, 0, 0, img.getWidth(), img.getHeight());
                dab(img, px, py);
                lastPx = px;
                lastPy = py;
                upload(tex, px, py);
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            panX += (float) dx;
            panY += (float) dy;
            return true;
        }
        if (!painting) return false;
        DynamicTexture tex = texture(false);
        if (tex == null || tex.getPixels() == null) return true;
        NativeImage img = tex.getPixels();
        int px = (int) Math.floor((mx - originX(img)) / scale(img)), py = (int) Math.floor((my - originY(img)) / scale(img));
        // a line from where the brush was, so a fast stroke has no gaps
        int steps = Math.max(Math.abs(px - lastPx), Math.abs(py - lastPy));
        for (int i = 1; i <= steps; i++) dab(img, lastPx + (px - lastPx) * i / steps, lastPy + (py - lastPy) * i / steps);
        lastPx = px;
        lastPy = py;
        upload(tex, px, py);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning) {
            panning = false;
            return true;
        }
        if (!painting) return false;
        painting = false;
        DynamicTexture tex = texture(false);
        if (tex != null) endStroke(tex);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int[] v = view();
        if (mx < v[0] || mx >= v[2] || my < v[1] || my >= v[3]) return false;
        zoom = Mth.clamp(zoom + (float) amount * 0.25F, -1, 7);
        return true;
    }

    private void dab(NativeImage img, int cx, int cy) {
        int r = brush / 2;
        int value = tool == Tool.ERASER ? 0 : argbToAbgr(colour);
        for (int yy = cy - r; yy < cy - r + brush; yy++) {
            for (int xx = cx - r; xx < cx - r + brush; xx++) {
                if (xx < 0 || yy < 0 || xx >= img.getWidth() || yy >= img.getHeight()) continue;
                set(img, xx, yy, value);
            }
        }
        changed = true;
    }

    /** Fills the area of the clicked colour (exactly that colour, joined at the sides). */
    private void fill(NativeImage img, int sx, int sy) {
        int target = img.getPixelRGBA(sx, sy), value = argbToAbgr(colour);
        if (target == value) return;
        int iw = img.getWidth(), ih = img.getHeight();
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        todo.add(new int[]{sx, sy});
        int budget = 1 << 22;
        while (!todo.isEmpty() && budget-- > 0) {
            int[] p = todo.poll();
            int px = p[0], py = p[1];
            if (px < 0 || py < 0 || px >= iw || py >= ih || img.getPixelRGBA(px, py) != target) continue;
            set(img, px, py, value);
            todo.add(new int[]{px + 1, py});
            todo.add(new int[]{px - 1, py});
            todo.add(new int[]{px, py + 1});
            todo.add(new int[]{px, py - 1});
        }
        changed = true;
    }

    private void set(NativeImage img, int px, int py, int value) {
        before.putIfAbsent((long) py << 32 | px, img.getPixelRGBA(px, py));
        img.setPixelRGBA(px, py, value);
        grow(px, py);
    }

    private void grow(int px, int py) {
        sx0 = Math.min(sx0, px);
        sy0 = Math.min(sy0, py);
        sx1 = Math.max(sx1, px + 1);
        sy1 = Math.max(sy1, py + 1);
    }

    private void beginStroke(NativeImage img, int x0, int y0, int x1, int y1) {
        before.clear();
        sx0 = Integer.MAX_VALUE;
        sy0 = Integer.MAX_VALUE;
        sx1 = -1;
        sy1 = -1;
        changed = false;
    }

    private void endStroke(DynamicTexture tex) {
        NativeImage img = tex.getPixels();
        if (img == null || !changed || before.isEmpty()) return;
        long[] positions = new long[before.size()];
        int[] pixels = new int[before.size()];
        int i = 0;
        for (var e : before.entrySet()) {
            positions[i] = e.getKey();
            pixels[i++] = e.getValue();
        }
        undo.push(new Patch(positions, pixels));
        while (undo.size() > UNDO_LIMIT) undo.removeLast();
        redo.clear();
        before.clear();
        tex.upload();
        keep(img);
    }

    /** The part of the texture around the brush, to the graphics card at once (all of it is too slow when big). */
    private void upload(DynamicTexture tex, int px, int py) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        int r = brush + 1;
        int x0 = Mth.clamp(Math.min(px, lastPx) - r, 0, img.getWidth() - 1), y0 = Mth.clamp(Math.min(py, lastPy) - r, 0, img.getHeight() - 1);
        int x1 = Mth.clamp(Math.max(px, lastPx) + r, 1, img.getWidth()), y1 = Mth.clamp(Math.max(py, lastPy) + r, 1, img.getHeight());
        tex.bind();
        img.upload(0, x0, y0, x0, y0, x1 - x0, y1 - y0, false, false);
    }

    /** What's painted goes into the draft that "Save" sends when it's needed: making a PNG of it takes a moment. */
    private boolean unencoded;

    private void keep(NativeImage img) {
        unencoded = true;
        host.state().dirty = true;
    }

    private void encode() {
        if (!unencoded) return;
        unencoded = false;
        DynamicTexture tex = texture(false);
        if (tex == null || tex.getPixels() == null) return;
        try {
            PresetCache.draftQuietly(host.state().preset.id(), PresetFiles.TEXTURE, tex.getPixels().asByteArray());
        } catch (java.io.IOException e) {
            org.velvet.Velvet.LOGGER.warn("Couldn't encode the texture: {}", e.toString());
        }
    }

    @Override
    public void onHide() {
        encode();
    }

    private void undo() {
        swap(undo, redo);
    }

    private void redo() {
        swap(redo, undo);
    }

    private void swap(Deque<Patch> from, Deque<Patch> to) {
        DynamicTexture tex = texture(false);
        if (from.isEmpty() || tex == null || tex.getPixels() == null) return;
        NativeImage img = tex.getPixels();
        Patch p = from.pop();
        int[] now = new int[p.pixels.length];
        for (int i = 0; i < p.positions.length; i++) {
            int px = (int) p.positions[i], py = (int) (p.positions[i] >>> 32);
            now[i] = img.getPixelRGBA(px, py);
            img.setPixelRGBA(px, py, p.pixels[i]);
        }
        to.push(new Patch(p.positions, now));
        tex.upload();
        keep(img);
    }

    private void importPng() {
        GirlType type = host.state().preset;
        FileDialogs.open(Component.translatable("editor.velvet.paint.import").getString(), List.of("*.png"), "PNG", false, paths -> {
            try {
                byte[] bytes = Files.readAllBytes(paths.getFirst());
                NativeImage.read(new java.io.ByteArrayInputStream(bytes)).close(); // is it a PNG at all
                PresetCache.draft(type.id(), PresetFiles.TEXTURE, bytes);
                undo.clear();
                redo.clear();
                host.state().dirty = true;
            } catch (Exception e) {
                status = Component.translatable("editor.velvet.import.failed", e.getMessage()).withStyle(ChatFormatting.RED);
            }
        });
    }

    private void exportPng() {
        GirlType type = host.state().preset;
        try {
            var dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("velvet_skins");
            Files.createDirectories(dir);
            var file = dir.resolve(type.id() + ".png");
            Files.write(file, PresetEditing.texturePng(type));
            status = Component.translatable("editor.velvet.paint.exported", file.toString());
        } catch (Exception e) {
            status = Component.translatable("editor.velvet.import.failed", e.getMessage()).withStyle(ChatFormatting.RED);
        }
    }

    /** Back to the base preset's texture (the preset's own one goes once saved). */
    private void resetToBase() {
        GirlType type = host.state().preset;
        PresetCache.draft(type.id(), PresetFiles.TEXTURE, new byte[0]);
        undo.clear();
        redo.clear();
        host.state().dirty = true;
    }

    @Nullable private Component status;

    @Override
    public void beforeSave() {
        status = null;
        encode();
    }

    // ---- Drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GirlType type = host.state().preset;
        if (!type.isCustom()) {
            int ty = y + 10;
            for (var line : font.split(Component.translatable("editor.velvet.built_in_preset", type.displayName()), w)) {
                g.drawCenteredString(font, line, x + w / 2, ty, LABEL);
                ty += 10;
            }
            return;
        }
        int[] v = view();
        drawInset(g, v[0], v[1], v[2] - v[0], v[3] - v[1], 0xFF2E3138);
        DynamicTexture tex = texture(false);
        NativeImage img = tex == null ? null : tex.getPixels();
        g.enableScissor(v[0], v[1], v[2], v[3]);
        if (img == null) {
            // not painted on yet: the base preset's texture, as it would be copied in
            g.drawCenteredString(font, Component.translatable("editor.velvet.paint.start"), (v[0] + v[2]) / 2, (v[1] + v[3]) / 2, 0xFFE0E0E0);
        } else {
            float s = scale(img), ox = originX(img), oy = originY(img);
            int dw = Math.round(img.getWidth() * s), dh = Math.round(img.getHeight() * s);
            checker(g, Math.round(ox), Math.round(oy), dw, dh);
            g.blit(type.texture(), Math.round(ox), Math.round(oy), dw, dh, 0, 0, img.getWidth(), img.getHeight(), img.getWidth(), img.getHeight());
            // the model's own pixels (its UVs are in 64ths of the texture)
            float cell = dw / 64F;
            if (cell >= 6) {
                for (int i = 0; i <= 64; i++) {
                    int gx = Math.round(ox + i * cell), gy = Math.round(oy + i * cell);
                    g.fill(gx, Math.round(oy), gx + 1, Math.round(oy) + dh, 0x22FFFFFF);
                    g.fill(Math.round(ox), gy, Math.round(ox) + dw, gy + 1, 0x22FFFFFF);
                }
            }
            if (mouseX >= v[0] && mouseX < v[2] && mouseY >= v[1] && mouseY < v[3]) {
                int px = (int) Math.floor((mouseX - ox) / s), py = (int) Math.floor((mouseY - oy) / s);
                int r = brush / 2;
                int bx = Math.round(ox + (px - r) * s), by = Math.round(oy + (py - r) * s), bs = Math.max(1, Math.round(brush * s));
                g.renderOutline(bx, by, bs, bs, 0xFFFFFFFF);
                g.drawString(font, px + ", " + py, v[0] + 3, v[3] - 10, 0xFFB0B0B0, false);
            }
        }
        g.disableScissor();
        int tx = x + w - TOOLS_W;
        // the colour as it is
        int cy = hexBox != null ? hexBox.getY() : y;
        g.fill(tx, cy + 1, tx + 16, cy + 15, 0xFF000000);
        g.fill(tx + 1, cy + 2, tx + 15, cy + 14, colour);
        g.drawString(font, Component.translatable("editor.velvet.paint." + tool.name().toLowerCase(Locale.ROOT)), tx, y + h - 20, LABEL, false);
        if (status != null) {
            g.drawString(font, font.plainSubstrByWidth(status.getString(), w - TOOLS_W - 10), v[0] + 3, v[1] + 3, 0xFFFFFFFF, true);
        }
    }

    private static void checker(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF9A9A9A);
        int c = Math.max(4, Math.min(16, w / 32));
        for (int yy = 0; yy < h; yy += c) {
            for (int xx = (yy / c) % 2 * c; xx < w; xx += 2 * c) {
                g.fill(x + xx, y + yy, Math.min(x + w, x + xx + c), Math.min(y + h, y + yy + c), 0xFFBEBEBE);
            }
        }
    }

    private static int argbToAbgr(int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return a << 24 | b << 16 | gg << 8 | r;
    }

    private static int abgrToArgb(int abgr) {
        int a = abgr >>> 24, b = (abgr >> 16) & 0xFF, gg = (abgr >> 8) & 0xFF, r = abgr & 0xFF;
        return a << 24 | r << 16 | gg << 8 | b;
    }

    /** A colour to pick. */
    private static final class Swatch extends AbstractWidget {
        private final int colour;
        private final Runnable onPress;

        Swatch(int x, int y, int size, int colour, Runnable onPress) {
            super(x, y, size, size, Component.empty());
            this.colour = colour;
            this.onPress = onPress;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF000000);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, colour);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }
}
