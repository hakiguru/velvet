package org.velvet.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.velvet.Velvet;
import org.velvet.client.gui.TextArea;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.brain.AiProvider;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.FetchModelsPacket;
import org.velvet.network.packet.ModelListPacket;
import org.velvet.network.packet.SetAiKeyPacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The AI tab, in two parts:
 * <ul>
 *   <li>Brain: her brains on the left, each with its icon. "Internal" is her own, nothing to set up; every
 *       other one is a language model service with a short how-to, the player's API key for it (kept on the
 *       server, never shown again), the address for local ones and the model, from a list loaded from the
 *       service itself.</li>
 *   <li>Prompt: who she is, for the language models: her character, how she talks.</li>
 * </ul>
 * Her survival instincts aren't here: they're always on, set per NPC in her settings file (or with the
 * debug tool).
 */
public final class AiTab implements EditorTab {

    private enum Part { BRAIN, PROMPT }

    private static final int LIST_W = 104, ROW = 16, GAP = 6, PART_H = 14;

    private static Part lastPart = Part.BRAIN;

    private Host host;
    private Font font;
    private int x, y, w, h;
    private Part part = lastPart;
    private AiProvider page;
    private final Set<AiProvider> keys;
    private final boolean localAllowed;
    private final Map<AiProvider, List<String>> fetched = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> fetchError = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> modelDraft = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> endpointDraft = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, List<AbstractWidget>> pageWidgets = new EnumMap<>(AiProvider.class);
    private final List<AbstractWidget> promptWidgets = new ArrayList<>();
    private final Set<AiProvider> loading = EnumSet.noneOf(AiProvider.class);
    private int modelScroll;
    private EditBox modelBox;
    private final Map<AiProvider, EditBox> modelBoxes = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, EditBox> keyBoxes = new EnumMap<>(AiProvider.class);

    public AiTab(Set<AiProvider> keys, boolean localAllowed, AiProvider active) {
        this.keys = EnumSet.noneOf(AiProvider.class);
        this.keys.addAll(keys);
        this.localAllowed = localAllowed;
        this.page = active;
    }

    private GirlBehavior behavior() {
        return host.state().behavior;
    }

    public static ResourceLocation icon(AiProvider p) {
        return ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "textures/gui/ai/" + p.id + ".png");
    }

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y + PART_H + 4;
        this.w = w;
        this.h = h - PART_H - 4;
        this.partY = y;
        pageWidgets.clear();
        promptWidgets.clear();
        for (AiProvider p : AiProvider.values()) pageWidgets.put(p, new ArrayList<>());
        initInternal();
        for (AiProvider p : AiProvider.values()) if (p.isExternal()) initProvider(p);
        initPrompt();
    }

    private int partY;

    private <W extends AbstractWidget> W add(AiProvider p, W widget) {
        pageWidgets.get(p).add(widget);
        return host.add(widget);
    }

    private int pageX() {
        return x + LIST_W + GAP;
    }

    private int pageW() {
        return w - LIST_W - GAP;
    }

    // ---- Internal ----

    private void initInternal() {
        add(AiProvider.INTERNAL, Button.builder(Component.translatable("editor.velvet.ai.use_internal"), b -> {
            behavior().provider = AiProvider.INTERNAL;
            changed();
        }).bounds(pageX(), y + h - 18, Math.min(180, pageW()), 16).build());
    }

    // ---- A service's page ----

    private void initProvider(AiProvider p) {
        int px = pageX(), pw = pageW();
        int py = y + 12 + 4 * 10 + 4; // under the title and the how-to
        if (p.needsKey || p == AiProvider.CUSTOM) {
            EditBox key = add(p, new EditBox(font, px, py, pw - 2 * 52 - 4, 16, Component.translatable("editor.velvet.ai.key")));
            key.setMaxLength(512);
            key.setHint(Component.translatable(keys.contains(p) ? "editor.velvet.ai.key_saved" : "editor.velvet.ai.key_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            // keys never show, not even while typing
            key.setFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
            keyBoxes.put(p, key);
            add(p, Button.builder(Component.translatable("editor.velvet.ai.key_save"), b -> {
                if (key.getValue().isBlank()) return;
                NetworkHandler.sendToServer(new SetAiKeyPacket(p.id, key.getValue().trim()));
                key.setValue("");
            }).bounds(px + pw - 2 * 52 - 2, py, 52, 16).build());
            add(p, Button.builder(Component.translatable("editor.velvet.ai.key_remove"), b ->
                    NetworkHandler.sendToServer(new SetAiKeyPacket(p.id, ""))).bounds(px + pw - 52, py, 52, 16).build());
            py += 20;
        }
        if (p.local) {
            EditBox endpoint = add(p, new EditBox(font, px, py, pw, 16, Component.translatable("editor.velvet.ai.endpoint")));
            endpoint.setMaxLength(256);
            endpoint.setHint(Component.literal(p.baseUrl.isEmpty() ? "http://localhost:8080/v1" : p.baseUrl).withStyle(ChatFormatting.DARK_GRAY));
            endpoint.setValue(endpointDraft.computeIfAbsent(p, k -> behavior().provider == k ? behavior().endpoint : ""));
            endpoint.setEditable(localAllowed);
            endpoint.setResponder(v -> {
                endpointDraft.put(p, v);
                if (behavior().provider == p) {
                    behavior().endpoint = v.trim();
                    changed();
                }
            });
            py += 20;
        }
        int buttonsW = 56 + 2 + 66;
        EditBox model = add(p, new EditBox(font, px, py, pw - buttonsW - 4, 16, Component.translatable("editor.velvet.ai.model")));
        model.setMaxLength(128);
        model.setHint(Component.literal(p.defaultModel().isEmpty() ? "model" : p.defaultModel()).withStyle(ChatFormatting.DARK_GRAY));
        model.setValue(modelDraft.computeIfAbsent(p, k -> behavior().provider == k ? behavior().model : ""));
        model.setResponder(v -> {
            modelDraft.put(p, v);
            if (behavior().provider == p) {
                behavior().model = v.trim();
                changed();
            }
        });
        add(p, Button.builder(Component.translatable("editor.velvet.ai.load_models"), b -> {
            loading.add(p);
            fetchError.remove(p);
            NetworkHandler.sendToServer(new FetchModelsPacket(p.id, endpointDraft.getOrDefault(p, "")));
        }).bounds(px + pw - buttonsW, py, 56, 16).build());
        add(p, Button.builder(Component.translatable("editor.velvet.ai.use"), b -> {
            behavior().provider = p;
            behavior().model = modelDraft.getOrDefault(p, "").trim();
            behavior().endpoint = endpointDraft.getOrDefault(p, "").trim();
            changed();
        }).bounds(px + pw - 66, py, 66, 16).build());
        if (!p.setupUrl.isEmpty()) {
            add(p, Button.builder(Component.translatable(p.needsKey ? "editor.velvet.ai.open_site" : "editor.velvet.ai.download"), b -> {
                Minecraft mc = Minecraft.getInstance();
                var back = mc.screen;
                mc.setScreen(new ConfirmLinkScreen(open -> {
                    if (open) Util.getPlatform().openUri(p.setupUrl);
                    mc.setScreen(back);
                }, p.setupUrl, true));
            }).bounds(px + pw - 70, y, 70, 12).build());
        }
        modelBoxes.put(p, model);
        if (page == p) modelBox = model;
    }

    // ---- Prompt ----

    private void initPrompt() {
        int boxY = y + 12;
        TextArea persona = host.add(new TextArea(font, x, boxY, w, h - 12 - 24,
                Component.translatable("editor.velvet.persona.hint"), Component.translatable("editor.velvet.persona")));
        persona.setCharacterLimit(GirlBehavior.MAX_PERSONA);
        persona.setValue(behavior().persona);
        persona.setValueListener(v -> {
            if (!v.equals(behavior().persona)) changed();
            behavior().persona = v;
        });
        promptWidgets.add(persona);
    }

    private int modelListTop() {
        int py = y + 12 + 4 * 10 + 4;
        if (page.needsKey || page == AiProvider.CUSTOM) py += 20;
        if (page.local) py += 20;
        return py + 20;
    }

    private List<String> models(AiProvider p) {
        Set<String> all = new LinkedHashSet<>(p.suggestedModels);
        all.addAll(fetched.getOrDefault(p, List.of()));
        return new ArrayList<>(all);
    }

    // ---- Parts and pages ----

    private void showPage(AiProvider p) {
        page = p;
        modelScroll = 0;
        modelBox = modelBoxes.get(p);
        applyVisibility();
    }

    private void showPart(Part p) {
        part = p;
        lastPart = p;
        applyVisibility();
    }

    private void applyVisibility() {
        pageWidgets.forEach((provider, widgets) -> widgets.forEach(wd -> wd.visible = part == Part.BRAIN && provider == page));
        promptWidgets.forEach(wd -> wd.visible = part == Part.PROMPT);
    }

    @Override
    public void onShow() {
        applyVisibility();
    }

    private void changed() {
        host.state().dirty = true;
    }

    /** From the server: the models of a service, or the services with a key. */
    public void onModelList(ModelListPacket packet) {
        if (packet.provider().equals(ModelListPacket.KEYS)) {
            keys.clear();
            for (String id : packet.models()) {
                AiProvider p = AiProvider.byId(id);
                if (p != null) keys.add(p);
            }
            keyBoxes.forEach((p, box) -> box.setHint(Component.translatable(keys.contains(p)
                    ? "editor.velvet.ai.key_saved" : "editor.velvet.ai.key_hint").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        AiProvider p = AiProvider.byId(packet.provider());
        if (p == null) return;
        loading.remove(p);
        if (packet.error().isEmpty()) {
            fetched.put(p, packet.models());
            fetchError.remove(p);
        } else {
            fetchError.put(p, packet.error());
        }
    }

    // ---- Input ----

    private int partAt(double mx, double my) {
        if (my < partY || my >= partY + PART_H) return -1;
        int pw = partW();
        for (int i = 0; i < Part.values().length; i++) {
            int px = x + i * (pw + 2);
            if (mx >= px && mx < px + pw) return i;
        }
        return -1;
    }

    private int partW() {
        return Math.min(110, (w - 2) / 2);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int pi = partAt(mx, my);
        if (pi >= 0) {
            showPart(Part.values()[pi]);
            return true;
        }
        if (part != Part.BRAIN) return false;
        if (mx >= x && mx < x + LIST_W && my >= y && my < y + AiProvider.values().length * ROW) {
            showPage(AiProvider.values()[(int) ((my - y) / ROW)]);
            return true;
        }
        if (page.isExternal()) {
            int top = modelListTop();
            int px = pageX();
            if (mx >= px && mx < px + pageW() && my >= top && my < y + h) {
                int i = (int) ((my - top - 12) / 10) + modelScroll;
                List<String> list = models(page);
                if (my >= top + 12 && i >= 0 && i < list.size() && modelBox != null) modelBox.setValue(list.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scroll) {
        if (part == Part.BRAIN && page.isExternal() && mx >= pageX() && my >= modelListTop()) {
            int rows = Math.max(1, (y + h - modelListTop() - 14) / 10);
            modelScroll = Mth.clamp(modelScroll - (int) Math.signum(scroll) * 2, 0, Math.max(0, models(page).size() - rows));
            return true;
        }
        return false;
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderParts(g, mouseX, mouseY);
        if (part == Part.PROMPT) {
            g.drawString(font, Component.translatable("editor.velvet.persona"), x, y + 1, LABEL, false);
            int hy = y + h - 22;
            for (var line : font.split(Component.translatable("editor.velvet.persona.help"), w)) {
                g.drawString(font, line, x, hy, MUTED, false);
                hy += 10;
            }
            return;
        }
        // the brains
        drawInset(g, x, y, LIST_W, AiProvider.values().length * ROW, 0xFF8B8B8B);
        for (int i = 0; i < AiProvider.values().length; i++) {
            AiProvider p = AiProvider.values()[i];
            int ry = y + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW;
            if (p == page) g.fill(x, ry, x + LIST_W, ry + ROW, 0xFF5A5A9A);
            else if (hover) g.fill(x, ry, x + LIST_W, ry + ROW, 0xFF9C9C9C);
            g.blit(icon(p), x + 1, ry + 1, 0, 0, 14, 14, 14, 14);
            String title = p == AiProvider.INTERNAL ? Component.translatable("editor.velvet.ai.internal").getString() : p.title;
            int colour = p == page ? 0xFFFFFFFF : 0xFF202020;
            g.drawString(font, font.plainSubstrByWidth(title, LIST_W - 28), x + 18, ry + 4, colour, false);
            if (behavior().provider == p) g.drawString(font, "●", x + LIST_W - 9, ry + 4, 0xFF2E9D42, false);
            else if (keys.contains(p)) g.drawString(font, "✓", x + LIST_W - 9, ry + 4, p == page ? 0xFFC0FFC0 : 0xFF2E7D32, false);
        }
        int hy = y + AiProvider.values().length * ROW + 4;
        for (var line : font.split(Component.translatable("editor.velvet.ai.active_hint"), LIST_W)) {
            if (hy + 9 > y + h) break;
            g.drawString(font, line, x, hy, MUTED, false);
            hy += 10;
        }

        int px = pageX(), pw = pageW();
        Component state = behavior().provider == page
                ? Component.translatable("editor.velvet.ai.active").withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable("editor.velvet.ai.inactive").withStyle(ChatFormatting.GRAY);
        Component title = (page == AiProvider.INTERNAL ? Component.translatable("editor.velvet.ai.internal")
                : Component.literal(page.title)).copy().withStyle(ChatFormatting.BOLD);
        g.blit(icon(page), px, y, 0, 0, 14, 14, 14, 14);
        g.drawString(font, title, px + 18, y + 3, LABEL, false);
        g.drawString(font, state, px + 18 + font.width(title) + 6, y + 3, LABEL, false);
        if (page == AiProvider.INTERNAL) {
            int ly = y + 20;
            for (var line : font.split(Component.translatable("editor.velvet.ai.internal.about"), pw)) {
                if (ly + 9 > y + h - 20) break;
                g.drawString(font, line, px, ly, 0xFF505050, false);
                ly += 10;
            }
            return;
        }
        int ly = y + 18;
        List<FormattedCharSequence> how = font.split(Component.translatable(page.setupKey()), pw);
        for (int i = 0; i < Math.min(4, how.size()); i++) {
            g.drawString(font, how.get(i), px, ly, 0xFF505050, false);
            ly += 10;
        }
        if (page.local && !localAllowed) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet.ai.local_not_allowed").getString(), pw),
                    px, ly - 10, 0xFFB02020, false);
        }

        int top = modelListTop();
        String status = loading.contains(page) ? Component.translatable("editor.velvet.ai.loading").getString()
                : fetchError.containsKey(page) ? errorText(fetchError.get(page))
                : Component.translatable("editor.velvet.ai.models", models(page).size()).getString();
        g.drawString(font, font.plainSubstrByWidth(status, pw), px, top, fetchError.containsKey(page) ? 0xFFB02020 : MUTED, false);
        int listTop = top + 12;
        drawInset(g, px, listTop, pw, y + h - listTop, 0xFF8B8B8B);
        List<String> list = models(page);
        int rows = Math.max(1, (y + h - listTop - 2) / 10);
        String current = modelBox != null ? modelBox.getValue().trim() : "";
        for (int i = 0; i < rows && i + modelScroll < list.size(); i++) {
            String m = list.get(i + modelScroll);
            int ry = listTop + 2 + i * 10;
            boolean hover = mouseX >= px && mouseX < px + pw && mouseY >= ry - 1 && mouseY < ry + 9;
            if (m.equals(current)) g.fill(px, ry - 1, px + pw, ry + 9, 0xFF5A5A9A);
            else if (hover) g.fill(px, ry - 1, px + pw, ry + 9, 0xFF9C9C9C);
            g.drawString(font, font.plainSubstrByWidth(m, pw - 4), px + 2, ry, m.equals(current) ? 0xFFFFFFFF : 0xFF202020, false);
        }
    }

    /** The two parts as a row of tabs at the top. */
    private void renderParts(GuiGraphics g, int mouseX, int mouseY) {
        int pw = partW();
        int hovered = partAt(mouseX, mouseY);
        for (int i = 0; i < Part.values().length; i++) {
            Part p = Part.values()[i];
            int px = x + i * (pw + 2);
            boolean open = p == part;
            g.fill(px, partY, px + pw, partY + PART_H, open ? 0xFF5A5A9A : hovered == i ? 0xFF9C9C9C : 0xFF8B8B8B);
            g.fill(px, partY + PART_H - 1, px + pw, partY + PART_H, open ? 0xFF3A3A7A : SHADOW);
            Component label = Component.translatable("editor.velvet.ai.part." + p.name().toLowerCase());
            g.drawCenteredString(font, label, px + pw / 2, partY + 3, open ? 0xFFFFFFFF : 0xFFF0F0F0);
        }
    }

    private static String errorText(String error) {
        return switch (error) {
            case "no_key" -> Component.translatable("editor.velvet.ai.need_key").getString();
            case "local_not_allowed" -> Component.translatable("editor.velvet.ai.local_not_allowed").getString();
            default -> Component.translatable("editor.velvet.ai.load_failed", error).getString();
        };
    }
}
