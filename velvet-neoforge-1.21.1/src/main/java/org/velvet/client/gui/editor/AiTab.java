package org.velvet.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.velvet.client.gui.RangeSlider;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The AI tab: one page per brain on the left. "Internal" holds her own behavior (what she does by herself,
 * her instinct to stay alive and who hears her in chat); every other page is a language model service with
 * a short how-to, the player's API key for it (kept on the server, never shown again), the address for
 * local ones and the model to use, with a list loaded from the service itself.
 */
public final class AiTab implements EditorTab {

    private static final int LIST_W = 92, ROW = 12, GAP = 6;

    private Host host;
    private Font font;
    private int x, y, w, h;
    private AiProvider page;
    private final Set<AiProvider> keys;
    private final boolean localAllowed;
    private final Map<AiProvider, List<String>> fetched = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> fetchError = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> modelDraft = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, String> endpointDraft = new EnumMap<>(AiProvider.class);
    private final Map<AiProvider, List<AbstractWidget>> pageWidgets = new EnumMap<>(AiProvider.class);
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

    @Override
    public void init(Host host, int x, int y, int w, int h) {
        this.host = host;
        this.font = host.font();
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        pageWidgets.clear();
        for (AiProvider p : AiProvider.values()) pageWidgets.put(p, new ArrayList<>());
        initInternal();
        for (AiProvider p : AiProvider.values()) if (p.isExternal()) initProvider(p);
    }

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

    // ---- The internal page ----

    private record Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {}

    private void initInternal() {
        int px = pageX(), py = y + 12;
        int colW = (pageW() - 6) / 2;
        int col2 = px + colW + 6;
        List<Toggle> basic = List.of(
                new Toggle("wander", () -> behavior().wander, v -> behavior().wander = v),
                new Toggle("pick_up_items", () -> behavior().pickUpItems, v -> behavior().pickUpItems = v),
                new Toggle("auto_eat", () -> behavior().autoEat, v -> behavior().autoEat = v),
                new Toggle("flee_monsters", () -> behavior().fleeMonsters, v -> behavior().fleeMonsters = v));
        List<Toggle> survival = List.of(
                new Toggle("survival", () -> behavior().survival, v -> behavior().survival = v),
                new Toggle("fight_back", () -> behavior().fightBack, v -> behavior().fightBack = v),
                new Toggle("stealth", () -> behavior().stealth, v -> behavior().stealth = v),
                new Toggle("dig_in", () -> behavior().digIn, v -> behavior().digIn = v),
                new Toggle("pillar_up", () -> behavior().pillarUp, v -> behavior().pillarUp = v),
                new Toggle("use_items", () -> behavior().useItems, v -> behavior().useItems = v),
                new Toggle("ask_for_help", () -> behavior().askForHelp, v -> behavior().askForHelp = v));
        int step = Mth.clamp((h - 14) / 9, 16, 19);
        int row = 0;
        for (Toggle t : basic) addToggle(t, px, py + row++ * step, colW);
        int sy = py + row * step + 2;
        add(AiProvider.INTERNAL, Button.builder(visibilityLabel(), b -> {
            behavior().chatVisibility = behavior().chatVisibility == GirlBehavior.ChatVisibility.PRIVATE
                    ? GirlBehavior.ChatVisibility.PUBLIC : GirlBehavior.ChatVisibility.PRIVATE;
            b.setMessage(visibilityLabel());
            changed();
        }).bounds(px, sy, colW, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("editor.velvet.ai.chat.tooltip"))).build());
        sy += 18;
        add(AiProvider.INTERNAL, new RangeSlider(px, sy, colW, 16, GirlBehavior.MIN_STOP, GirlBehavior.MAX_STOP, 0.5,
                () -> behavior().followStopDistance, v -> { behavior().followStopDistance = v; changed(); },
                v -> Component.translatable("editor.velvet.ai.stop_distance", blocks(v))));
        add(AiProvider.INTERNAL, new RangeSlider(px, sy + 18, colW, 16, GirlBehavior.MIN_RUN, GirlBehavior.MAX_RUN, 0.5,
                () -> behavior().followRunDistance, v -> { behavior().followRunDistance = v; changed(); },
                v -> Component.translatable("editor.velvet.ai.run_distance", blocks(v))));
        add(AiProvider.INTERNAL, new RangeSlider(px, sy + 36, colW, 16, GirlBehavior.MIN_TELEPORT, GirlBehavior.MAX_TELEPORT, 1,
                () -> behavior().followTeleportDistance, v -> { behavior().followTeleportDistance = v; changed(); },
                v -> Component.translatable("editor.velvet.ai.teleport_distance", blocks(v))));
        row = 0;
        for (Toggle t : survival) addToggle(t, col2, py + row++ * step, colW);
        add(AiProvider.INTERNAL, Button.builder(Component.translatable("editor.velvet.ai.use_internal"), b -> {
            behavior().provider = AiProvider.INTERNAL;
            changed();
        }).bounds(col2, py + row * step + 2, colW, 16).build());
    }

    private Component visibilityLabel() {
        return Component.translatable("editor.velvet.ai.chat",
                Component.translatable("editor.velvet.ai.chat." + behavior().chatVisibility.id()));
    }

    private void addToggle(Toggle t, int px, int py, int colW) {
        add(AiProvider.INTERNAL, Checkbox.builder(Component.translatable("editor.velvet.ai." + t.key()), font)
                .pos(px, py).maxWidth(colW).selected(t.get().getAsBoolean())
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("editor.velvet.ai." + t.key() + ".tooltip")))
                .onValueChange((cb, v) -> {
                    t.set().accept(v);
                    changed();
                }).build());
    }

    private static String blocks(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    // ---- A service's page ----

    private void initProvider(AiProvider p) {
        int px = pageX(), pw = pageW();
        int py = y + 12 + 4 * 10 + 4; // under the title and the how-to
        // the key
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
        // the model
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

    // ---- Pages ----

    private void showPage(AiProvider p) {
        page = p;
        modelScroll = 0;
        pageWidgets.forEach((provider, widgets) -> widgets.forEach(wd -> wd.visible = provider == p));
        modelBox = modelBoxes.get(p);
    }

    @Override
    public void onShow() {
        showPage(page);
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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (mx >= x && mx < x + LIST_W && my >= y && my < y + AiProvider.values().length * ROW) {
            int i = (int) ((my - y) / ROW);
            showPage(AiProvider.values()[i]);
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
        if (page.isExternal() && mx >= pageX() && my >= modelListTop()) {
            int rows = Math.max(1, (y + h - modelListTop() - 14) / 10);
            modelScroll = Mth.clamp(modelScroll - (int) Math.signum(scroll) * 2, 0, Math.max(0, models(page).size() - rows));
            return true;
        }
        return false;
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the pages
        drawInset(g, x, y, LIST_W, AiProvider.values().length * ROW, 0xFF8B8B8B);
        for (int i = 0; i < AiProvider.values().length; i++) {
            AiProvider p = AiProvider.values()[i];
            int ry = y + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW;
            if (p == page) g.fill(x, ry, x + LIST_W, ry + ROW, 0xFF5A5A9A);
            else if (hover) g.fill(x, ry, x + LIST_W, ry + ROW, 0xFF9C9C9C);
            String title = p == AiProvider.INTERNAL ? Component.translatable("editor.velvet.ai.internal").getString() : p.title;
            int colour = p == page ? 0xFFFFFFFF : 0xFF202020;
            g.drawString(font, font.plainSubstrByWidth(title, LIST_W - 18), x + 3, ry + 2, colour, false);
            if (behavior().provider == p) g.drawString(font, "●", x + LIST_W - 9, ry + 2, 0xFF2E9D42, false);
            else if (keys.contains(p)) g.drawString(font, "✓", x + LIST_W - 9, ry + 2, p == page ? 0xFFC0FFC0 : 0xFF2E7D32, false);
        }
        int hy = y + AiProvider.values().length * ROW + 4;
        for (var line : font.split(Component.translatable("editor.velvet.ai.active_hint"), LIST_W)) {
            if (hy + 9 > y + h) break;
            g.drawString(font, line, x, hy, MUTED, false);
            hy += 10;
        }

        int px = pageX(), pw = pageW();
        if (page == AiProvider.INTERNAL) {
            g.drawString(font, Component.translatable("editor.velvet.ai.section.behavior"), px, y, LABEL, false);
            g.drawString(font, Component.translatable("editor.velvet.ai.section.survival"), px + (pw - 6) / 2 + 6, y, LABEL, false);
            return;
        }
        // title, state and how-to
        Component state = behavior().provider == page
                ? Component.translatable("editor.velvet.ai.active").withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable("editor.velvet.ai.inactive").withStyle(ChatFormatting.GRAY);
        Component title = Component.literal(page.title).withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, px, y + 2, LABEL, false);
        g.drawString(font, state, px + font.width(title) + 6, y + 2, LABEL, false);
        int ly = y + 14;
        List<FormattedCharSequence> how = font.split(Component.translatable(page.setupKey()), pw);
        for (int i = 0; i < Math.min(4, how.size()); i++) {
            g.drawString(font, how.get(i), px, ly, 0xFF505050, false);
            ly += 10;
        }
        if (page.local && !localAllowed) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet.ai.local_not_allowed").getString(), pw),
                    px, ly - 10, 0xFFB02020, false);
        }

        // models
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

    private static String errorText(String error) {
        return switch (error) {
            case "no_key" -> Component.translatable("editor.velvet.ai.need_key").getString();
            case "local_not_allowed" -> Component.translatable("editor.velvet.ai.local_not_allowed").getString();
            default -> Component.translatable("editor.velvet.ai.load_failed", error).getString();
        };
    }
}
