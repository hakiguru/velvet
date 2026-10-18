package org.velvetlite.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.velvetlite.client.gui.editor.AiTab;
import org.velvetlite.client.gui.editor.EditorState;
import org.velvetlite.client.gui.editor.EditorTab;
import org.velvetlite.client.gui.editor.ModelTab;
import org.velvetlite.client.gui.editor.ModelView;
import org.velvetlite.client.skin.SkinCache;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.BoneTweaks;
import org.velvetlite.entity.girl.GirlBehavior;
import org.velvetlite.entity.girl.GirlNeeds;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.EditorSelectGirlPacket;
import org.velvetlite.network.packet.ModelListPacket;
import org.velvetlite.network.packet.SaveGirlEditorPacket;
import org.velvetlite.skin.GirlSkin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.velvetlite.client.gui.PanelArt.*;

/**
 * The NPC editor, opened with the editor wand. On the left, a tab for each of the player's girls (with her
 * face and name; scrolls like the creative inventory when there are many); girls far away show but can't be
 * edited until they're in a loaded area. Along the top:
 * <ul>
 *   <li>Character: her name and who she is (for the language models), and what she's up to.</li>
 *   <li>Model: a small Blockbench, see {@link ModelTab}.</li>
 *   <li>AI: her own behavior and survival instinct, and the language model services, see {@link AiTab}.</li>
 *   <li>JSON: all of her behavior as JSON, including keys the forms don't show.</li>
 * </ul>
 * Nothing changes until "Save"; the model view shows the changes meanwhile.
 * <p>
 * A plain screen rather than a container screen: those take mouse drags for slots and close on the
 * inventory key, which gets typed into the text fields here.
 */
public class GirlEditorScreen extends Screen implements MenuAccess<GirlEditorMenu>, EditorTab.Host {

    private enum Tab {
        CHARACTER(new ItemStack(Items.NAME_TAG)),
        MODEL(new ItemStack(Items.ARMOR_STAND)),
        AI(new ItemStack(Items.COMPARATOR)),
        JSON(new ItemStack(Items.WRITABLE_BOOK));

        final ItemStack icon;

        Tab(ItemStack icon) {
            this.icon = icon;
        }

        Component title() {
            return this == JSON ? Component.literal("JSON") : Component.translatable("editor.velvet_lite.tab." + name().toLowerCase());
        }
    }

    private static Tab lastTab = Tab.CHARACTER;

    private static final int MARGIN = 8, TOP = 18, BOTTOM_BAR = 28;
    private static final int NPC_TAB_W = 76, NPC_TAB_H = 30, NPC_TAB_GAP = 1, SCROLLER_W = 8;
    private static final int SAVED_TICKS = 60;

    private final GirlEditorMenu menu;
    private final GirlEditorMenu.Data data;
    @Nullable private final AbstractGirlEntity girl;
    @Nullable private final GirlEditorMenu.GirlEntry target;
    private Tab tab = lastTab;
    private int left, top, panelW, panelH;
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);
    @Nullable private Tab adding;

    private final EditorState state;
    private final ModelTab modelTab = new ModelTab();
    private final AiTab aiTab;
    private String json;
    @Nullable private String jsonError;
    private int savedTicks;

    /** Stand-ins of every girl in the list, for the faces on their tabs. */
    private final Map<UUID, AbstractGirlEntity> faces = new HashMap<>();
    private int npcScroll;
    private boolean draggingScroller;
    /** Clicked another girl's tab with unsaved changes: once more to leave them. */
    @Nullable private UUID leaveConfirm;

    public GirlEditorScreen(GirlEditorMenu menu, Inventory playerInv, Component title) {
        super(Component.translatable("editor.velvet_lite.title", title));
        this.menu = menu;
        this.data = menu.getData();
        this.girl = menu.getGirl();
        GirlEditorMenu.GirlEntry found = null;
        for (GirlEditorMenu.GirlEntry e : data.girls()) {
            AbstractGirlEntity face = standIn(e);
            if (face != null) faces.put(e.id(), face);
            if (e.id().equals(data.target())) found = e;
        }
        this.target = found;
        // the model view gets its own stand-in, so the changes don't show on her in the world before saving
        AbstractGirlEntity preview = found != null ? standIn(found) : null;
        GirlBehavior behavior = GirlBehavior.fromJsonOrDefault(data.behaviorJson());
        String name = found != null ? found.appearance().getString("name") : "";
        BoneTweaks tweaks = found != null ? BoneTweaks.fromTag(found.appearance().getCompound("bone_tweaks")) : BoneTweaks.NONE;
        GirlSkin skin = found != null ? GirlSkin.decode(found.appearance().getString("skin")) : null;
        this.state = new EditorState(name, tweaks, skin, behavior, preview);
        this.json = behavior.toJson();
        this.aiTab = new AiTab(data.keys(), data.localEndpoints(), behavior.provider);
    }

    @Nullable
    private AbstractGirlEntity standIn(GirlEditorMenu.GirlEntry e) {
        if (minecraft == null) minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) return null;
        var type = BuiltInRegistries.ENTITY_TYPE.get(e.type());
        if (!(type.create(minecraft.level) instanceof AbstractGirlEntity standIn)) return null;
        standIn.setGirlUUID(e.id());
        standIn.applyAppearance(e.appearance());
        return standIn;
    }

    @Override
    public GirlEditorMenu getMenu() {
        return menu;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Host ----

    @Override
    public <W extends AbstractWidget> W add(W widget) {
        if (adding != null) tabWidgets.get(adding).add(widget);
        return addRenderableWidget(widget);
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public EditorState state() {
        return state;
    }

    @Override
    public void rebuild() {
        rebuildWidgets();
    }

    // ---- Layout ----

    @Override
    protected void init() {
        panelW = Mth.clamp(width - NPC_TAB_W - SCROLLER_W - 12, 330, 540);
        panelH = Mth.clamp(height - TAB_RISE - 8, 200, 300);
        left = (width - panelW + NPC_TAB_W + SCROLLER_W) / 2;
        top = (height - panelH + TAB_RISE) / 2;
        tabWidgets.clear();
        for (Tab t : Tab.values()) tabWidgets.put(t, new ArrayList<>());
        npcScroll = Mth.clamp(npcScroll, 0, maxNpcScroll());

        int x = left + MARGIN, y = top + TOP;
        int contentW = panelW - 2 * MARGIN;
        int contentH = panelH - TOP - BOTTOM_BAR;

        if (target != null && girlEditable()) {
            // character
            adding = Tab.CHARACTER;
            EditBox nameBox = add(new EditBox(font, x, y + 11, Math.min(160, contentW / 2 - 8), 18,
                    Component.translatable("editor.velvet_lite.name")));
            nameBox.setMaxLength(SaveGirlEditorPacket.MAX_NAME_LENGTH);
            nameBox.setValue(state.name);
            nameBox.setHint(Component.literal(typeName().getString()).withStyle(ChatFormatting.DARK_GRAY));
            nameBox.setResponder(v -> {
                if (!v.equals(state.name)) state.dirty = true;
                state.name = v;
                state.updatePreview();
            });
            int personaY = y + 44;
            MultiLineEditBox persona = add(new MultiLineEditBox(font, x, personaY, contentW / 2 - 8, top + panelH - BOTTOM_BAR - personaY,
                    Component.translatable("editor.velvet_lite.persona.hint"), Component.translatable("editor.velvet_lite.persona")));
            persona.setCharacterLimit(GirlBehavior.MAX_PERSONA);
            persona.setValue(state.behavior.persona);
            persona.setValueListener(v -> {
                if (!v.equals(state.behavior.persona)) state.dirty = true;
                state.behavior.persona = v;
            });

            // model
            adding = Tab.MODEL;
            modelTab.init(this, x, y, contentW, contentH);

            // AI
            adding = Tab.AI;
            aiTab.init(this, x, y, contentW, contentH);

            // JSON
            adding = Tab.JSON;
            MultiLineEditBox jsonBox = add(new MultiLineEditBox(font, x, y, contentW, contentH - 12,
                    Component.literal("{ }"), Component.literal("JSON")));
            jsonBox.setCharacterLimit(GirlBehavior.MAX_LENGTH);
            jsonBox.setValue(json);
            jsonBox.setValueListener(v -> {
                if (!v.equals(json)) state.dirty = true;
                json = v;
            });
            adding = null;

            int by = top + panelH - BOTTOM_BAR + 5;
            addRenderableWidget(Button.builder(Component.translatable("editor.velvet_lite.save"), b -> save())
                    .bounds(left + panelW - MARGIN - 2 * 70 - 4, by, 70, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelW - MARGIN - 70, top + panelH - BOTTOM_BAR + 5, 70, 18).build());
        selectTab(tab);
        SkinCache.setUploadListener(null);
    }

    private boolean girlEditable() {
        return target != null && target.loaded();
    }

    // ---- Tabs ----

    /** Switches tabs; leaving the JSON tab takes over what was typed there, unless it doesn't parse. */
    private boolean selectTab(Tab selected) {
        boolean fromJson = tab == Tab.JSON && selected != Tab.JSON;
        if (fromJson && !applyJson()) return false;
        if (selected == Tab.JSON && tab != Tab.JSON) setJsonFromBehavior();
        Tab before = tab;
        tab = selected;
        lastTab = selected;
        if (fromJson) {
            rebuildWidgets(); // the forms show what was typed; this comes back here with the new tab
            return true;
        }
        tabWidgets.forEach((t, widgets) -> widgets.forEach(w -> w.visible = t == selected));
        if (getFocused() instanceof AbstractWidget w && !w.visible) setFocused(null);
        if (before == Tab.MODEL && selected != Tab.MODEL) modelTab.onHide();
        EditorTab panel = panel();
        if (panel != null) panel.onShow();
        return true;
    }

    @Nullable
    private EditorTab panel() {
        if (!girlEditable()) return null;
        return switch (tab) {
            case MODEL -> modelTab;
            case AI -> aiTab;
            default -> null;
        };
    }

    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        int i = PanelArt.tabAt(mouseX, mouseY, left, top, Tab.values().length);
        return i < 0 ? null : Tab.values()[i];
    }

    // ---- Editing ----

    private void setJsonFromBehavior() {
        json = state.behavior.toJson();
        jsonError = null;
        for (AbstractWidget w : tabWidgets.get(Tab.JSON)) {
            if (w instanceof MultiLineEditBox box) box.setValue(json);
        }
    }

    /** Parses the JSON tab into the behavior; on an error shows it and keeps the old behavior. */
    private boolean applyJson() {
        try {
            state.behavior = GirlBehavior.fromJson(json);
            jsonError = null;
            return true;
        } catch (RuntimeException e) {
            jsonError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return false;
        }
    }

    private void save() {
        if (!girlEditable()) return;
        if (tab == Tab.JSON && !applyJson()) return;
        NetworkHandler.sendToServer(new SaveGirlEditorPacket(state.name, BoneTweaks.of(state.tweaks).toTag(),
                state.skin == null ? "" : state.skin.encode(), state.behavior.toJson()));
        state.dirty = false;
        savedTicks = SAVED_TICKS;
        AbstractGirlEntity face = target == null ? null : faces.get(target.id());
        if (face != null) {
            face.setCustomNameString(state.name);
            face.setBoneTweaks(BoneTweaks.of(state.tweaks));
            face.setSkin(state.skin);
        }
    }

    public void onModelList(ModelListPacket packet) {
        aiTab.onModelList(packet);
    }

    // ---- The girls' tabs ----

    private int npcTabX() {
        return left - NPC_TAB_W + 3;
    }

    private int visibleNpcTabs() {
        return Math.max(1, (panelH - 4) / (NPC_TAB_H + NPC_TAB_GAP));
    }

    private int maxNpcScroll() {
        return Math.max(0, data.girls().size() - visibleNpcTabs());
    }

    @Nullable
    private GirlEditorMenu.GirlEntry npcTabAt(double mx, double my) {
        int tx = npcTabX();
        if (mx < tx || mx >= tx + NPC_TAB_W - 3 || my < top + 2) return null;
        int i = (int) ((my - top - 2) / (NPC_TAB_H + NPC_TAB_GAP));
        if (i >= visibleNpcTabs() || (my - top - 2) - i * (NPC_TAB_H + NPC_TAB_GAP) >= NPC_TAB_H) return null;
        int index = i + npcScroll;
        return index < data.girls().size() ? data.girls().get(index) : null;
    }

    private int[] scroller() {
        int x0 = npcTabX() - SCROLLER_W - 2;
        return new int[]{x0, top + 2, x0 + SCROLLER_W, top + panelH - 2};
    }

    private void pickGirl(GirlEditorMenu.GirlEntry e) {
        if (target != null && e.id().equals(target.id())) return;
        if (!e.loaded()) return;
        if (state.dirty && !e.id().equals(leaveConfirm)) {
            leaveConfirm = e.id();
            return;
        }
        NetworkHandler.sendToServer(new EditorSelectGirlPacket(e.id()));
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Tab clicked = button == 0 ? tabAt(mouseX, mouseY) : null;
        if (clicked != null) {
            if (clicked != tab && selectTab(clicked)) click();
            return true;
        }
        GirlEditorMenu.GirlEntry npc = button == 0 ? npcTabAt(mouseX, mouseY) : null;
        if (npc != null) {
            click();
            pickGirl(npc);
            return true;
        }
        int[] sc = scroller();
        if (button == 0 && maxNpcScroll() > 0 && mouseX >= sc[0] && mouseX < sc[2] && mouseY >= sc[1] && mouseY < sc[3]) {
            draggingScroller = true;
            scrollTo(mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        EditorTab panel = panel();
        if (panel != null && panel.mouseClicked(mouseX, mouseY, button)) {
            setFocused(null);
            return true;
        }
        return false;
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void scrollTo(double mouseY) {
        int[] sc = scroller();
        float f = (float) ((mouseY - sc[1] - 7.5) / (sc[3] - sc[1] - 15));
        npcScroll = Mth.clamp(Math.round(Mth.clamp(f, 0, 1) * maxNpcScroll()), 0, maxNpcScroll());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroller) {
            scrollTo(mouseY);
            return true;
        }
        EditorTab panel = panel();
        if (panel != null && panel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroller = false;
        EditorTab panel = panel();
        if (panel != null && panel.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < left && maxNpcScroll() > 0) {
            npcScroll = Mth.clamp(npcScroll - (int) Math.signum(scrollY), 0, maxNpcScroll());
            return true;
        }
        EditorTab panel = panel();
        if (panel != null && panel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean typing = getFocused() instanceof EditBox || getFocused() instanceof MultiLineEditBox;
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scanCode, modifiers);
        if (typing) return super.keyPressed(key, scanCode, modifiers);
        EditorTab panel = panel();
        if (panel != null && panel.keyPressed(key, scanCode, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_S && hasControlDown()) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (savedTicks > 0) savedTicks--;
        EditorTab panel = panel();
        if (panel != null) panel.tick();
        for (AbstractGirlEntity face : faces.values()) face.tickCount++;
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        SkinCache.setUploadListener(null);
    }

    // ---- Rendering ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        for (Tab t : Tab.values()) if (t != tab) drawTab(g, t);
        renderNpcTabs(g, mouseX, mouseY, false);
        drawPanel(g, left, top, panelW, panelH);
        drawTab(g, tab);
        joinOpenTab(g, tabX(left, tab.ordinal()), top);
        renderNpcTabs(g, mouseX, mouseY, true);
    }

    private void drawTab(GuiGraphics g, Tab t) {
        int[] icon = PanelArt.drawTab(g, tabX(left, t.ordinal()), top, t == tab);
        g.renderItem(t.icon, icon[0], icon[1]);
    }

    /** The girls' tabs left of the panel: the open one in front of its edge, the others behind it. */
    private void renderNpcTabs(GuiGraphics g, int mouseX, int mouseY, boolean openOnly) {
        int tx = npcTabX();
        List<GirlEditorMenu.GirlEntry> girls = data.girls();
        for (int i = 0; i < visibleNpcTabs() && i + npcScroll < girls.size(); i++) {
            GirlEditorMenu.GirlEntry e = girls.get(i + npcScroll);
            boolean open = target != null && e.id().equals(target.id());
            if (open != openOnly) continue;
            int ty = top + 2 + i * (NPC_TAB_H + NPC_TAB_GAP);
            int x0 = open ? tx : tx + 3;
            int x1 = tx + NPC_TAB_W;
            g.fill(x0 + 1, ty, x1, ty + NPC_TAB_H, OUTLINE);
            g.fill(x0, ty + 1, x1, ty + NPC_TAB_H - 1, OUTLINE);
            boolean hover = npcTabAt(mouseX, mouseY) == e;
            int fill = open ? PANEL : hover && e.loaded() ? 0xFFB8B8B8 : 0xFFA8A8A8;
            g.fill(x0 + 1, ty + 1, x1, ty + NPC_TAB_H - 1, fill);
            g.fill(x0 + 1, ty + 1, x1, ty + 3, LIGHT);
            g.fill(x0 + 1, ty + 1, x0 + 3, ty + NPC_TAB_H - 1, LIGHT);
            g.fill(x0 + 1, ty + NPC_TAB_H - 3, x1, ty + NPC_TAB_H - 1, open ? PANEL : SHADOW);
            if (open) g.fill(x1 - 3, ty + 3, x1 + 3, ty + NPC_TAB_H - 3, PANEL); // joins the panel

            // her face
            AbstractGirlEntity face = faces.get(e.id());
            int fx0 = x0 + 4, fy0 = ty + 3, fx1 = fx0 + 22, fy1 = ty + NPC_TAB_H - 3;
            g.fill(fx0, fy0, fx1, fy1, 0xFF6B6B6B);
            if (face != null) {
                g.enableScissor(fx0, fy0, fx1, fy1);
                ModelView.render(g, face, fx0, fy0, fx1, fy1, 20, 5, 3.4F, 0, 0, 0.86F, 0.9F, false, null, 0);
                g.disableScissor();
            }
            // her name, on up to two lines
            String name = e.appearance().getString("name");
            Component label = name.isEmpty() ? typeName(e) : Component.literal(name);
            int textX = fx1 + 3, textW = x1 - textX - 2;
            var lines = font.split(label, textW);
            int colour = e.loaded() ? LABEL : MUTED;
            for (int l = 0; l < Math.min(2, lines.size()); l++) g.drawString(font, lines.get(l), textX, ty + 6 + l * 10, colour, false);
            if (!e.loaded()) g.drawString(font, "⌛", x1 - 9, ty + NPC_TAB_H - 11, MUTED, false);
        }
        if (openOnly && maxNpcScroll() > 0) {
            int[] sc = scroller();
            drawInset(g, sc[0], sc[1], SCROLLER_W, sc[3] - sc[1], 0xFF8B8B8B);
            int thumbY = sc[1] + Math.round((sc[3] - sc[1] - 15) * (npcScroll / (float) maxNpcScroll()));
            g.fill(sc[0], thumbY, sc[2], thumbY + 15, 0xFFC6C6C6);
            g.fill(sc[0], thumbY, sc[2] - 1, thumbY + 1, LIGHT);
            g.fill(sc[0], thumbY, sc[0] + 1, thumbY + 14, LIGHT);
            g.fill(sc[0] + 1, thumbY + 14, sc[2], thumbY + 15, SHADOW);
            g.fill(sc[2] - 1, thumbY + 1, sc[2], thumbY + 15, SHADOW);
        }
    }

    private Component typeName() {
        return target == null ? Component.empty() : typeName(target);
    }

    private static Component typeName(GirlEditorMenu.GirlEntry e) {
        return BuiltInRegistries.ENTITY_TYPE.get(e.type()).getDescription();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background, panels and widgets
        g.drawString(font, title, left + MARGIN, top + 6, LABEL, false);
        Component tabTitle = tab.title();
        g.drawString(font, tabTitle, left + panelW - MARGIN - font.width(tabTitle), top + 6, LABEL, false);

        if (!girlEditable()) {
            renderNoGirl(g);
        } else {
            switch (tab) {
                case CHARACTER -> renderCharacter(g);
                case JSON -> {
                    if (jsonError != null) {
                        g.drawString(font, font.plainSubstrByWidth("⚠ " + jsonError, panelW - 2 * MARGIN),
                                left + MARGIN, top + panelH - BOTTOM_BAR - 10, 0xFFB02020, false);
                    }
                }
                default -> {
                    EditorTab panel = panel();
                    if (panel != null) panel.render(g, mouseX, mouseY, partialTick);
                }
            }
            // widgets drawn under the panel's own drawing: the ones of model and AI on top again
            EditorTab panel = panel();
            if (panel != null) {
                for (AbstractWidget w : tabWidgets.get(tab)) if (w.visible) w.render(g, mouseX, mouseY, partialTick);
                panel.renderOver(g, mouseX, mouseY, partialTick);
            }
        }

        // status in the bottom bar
        int sy = top + panelH - BOTTOM_BAR + 10;
        if (leaveConfirm != null && state.dirty) {
            g.drawString(font, Component.translatable("editor.velvet_lite.leave_unsaved"), left + MARGIN, sy, 0xFFB02020, false);
        } else if (savedTicks > 0) {
            g.drawString(font, Component.translatable("editor.velvet_lite.saved"), left + MARGIN, sy, 0xFF2E7D32, false);
        } else if (state.dirty) {
            g.drawString(font, Component.translatable("editor.velvet_lite.unsaved"), left + MARGIN, sy, MUTED, false);
        }

        Tab hovered = tabAt(mouseX, mouseY);
        GirlEditorMenu.GirlEntry npc = npcTabAt(mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(font, hovered.title(), mouseX, mouseY);
        } else if (npc != null) {
            List<Component> lines = new ArrayList<>();
            String name = npc.appearance().getString("name");
            lines.add(name.isEmpty() ? typeName(npc) : Component.literal(name));
            BlockPos p = npc.pos();
            lines.add(Component.literal(npc.dimension().getPath() + ": " + p.getX() + " " + p.getY() + " " + p.getZ())
                    .withStyle(ChatFormatting.GRAY));
            if (!npc.loaded()) lines.add(Component.translatable("editor.velvet_lite.not_loaded").withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void renderNoGirl(GuiGraphics g) {
        Component text = data.girls().isEmpty() ? Component.translatable("editor.velvet_lite.no_npcs")
                : target != null ? Component.translatable("editor.velvet_lite.not_loaded")
                : Component.translatable("editor.velvet_lite.pick_npc");
        int y = top + TOP + 20;
        for (var line : font.split(text, panelW - 2 * MARGIN)) {
            g.drawString(font, line, left + MARGIN, y, LABEL, false);
            y += 10;
        }
    }

    private void renderCharacter(GuiGraphics g) {
        int x = left + MARGIN, y = top + TOP;
        int contentW = panelW - 2 * MARGIN;
        g.drawString(font, Component.translatable("editor.velvet_lite.name"), x, y + 1, LABEL, false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet_lite.persona").getString(), contentW / 2 - 8),
                x, y + 34, LABEL, false);

        int ix = x + contentW / 2 + 4;
        int iy = y + 2;
        var a = target.appearance();
        boolean live = girl != null;
        float health = live ? girl.getHealth() : a.getFloat("health");
        float maxHealth = live ? girl.getMaxHealth() : a.getFloat("max_health");
        int food = live ? girl.getFood() : a.getInt("food");
        BlockPos home = live ? girl.getHomePos() : a.contains("home") ? BlockPos.of(a.getLong("home")) : null;
        BlockPos pos = live ? girl.blockPosition() : target.pos();
        line(g, ix, iy, "editor.velvet_lite.type", typeName());
        line(g, ix, iy + 12, "editor.velvet_lite.owner", ownerName());
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (!target.dimension().getPath().equals("overworld")) where = target.dimension().getPath() + ": " + where;
        line(g, ix, iy + 24, "editor.velvet_lite.position", Component.literal(where));
        line(g, ix, iy + 36, "editor.velvet_lite.home", home == null
                ? Component.translatable("editor.velvet_lite.none") : Component.literal(home.toShortString()));
        line(g, ix, iy + 48, "editor.velvet_lite.health", Component.literal(Math.round(health) + " / " + Math.round(maxHealth)));
        line(g, ix, iy + 60, "editor.velvet_lite.food", Component.literal(food + " / " + GirlNeeds.MAX_FOOD));
        line(g, ix, iy + 72, "editor.velvet_lite.brain", state.behavior.provider == org.velvetlite.entity.girl.brain.AiProvider.INTERNAL
                ? Component.translatable("editor.velvet_lite.ai.internal")
                : Component.literal(state.behavior.provider.title + (state.behavior.model.isBlank() ? "" : " · " + state.behavior.model)));
        g.drawString(font, font.plainSubstrByWidth(target.id().toString(), contentW / 2 - 4), ix, iy + 88, 0xFF8A8A8A, false);
        int hy = iy + 104;
        for (var l : font.split(Component.translatable("editor.velvet_lite.chat_hint", state.name.isEmpty() ? typeName() : Component.literal(state.name)),
                contentW / 2 - 4)) {
            g.drawString(font, l, ix, hy, MUTED, false);
            hy += 10;
        }
    }

    private void line(GuiGraphics g, int x, int y, String key, Component value) {
        g.drawString(font, Component.translatable(key), x, y, MUTED, false);
        int labelW = 58;
        g.drawString(font, font.plainSubstrByWidth(value.getString(), (panelW - 2 * MARGIN) / 2 - 4 - labelW), x + labelW, y, LABEL, false);
    }

    private Component ownerName() {
        UUID owner = girl != null ? girl.getOwnerUUID() : minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        if (owner == null) return Component.translatable("editor.velvet_lite.none");
        if (minecraft != null && minecraft.player != null && owner.equals(minecraft.player.getUUID())) {
            return Component.translatable("editor.velvet_lite.owner_you", minecraft.player.getName());
        }
        PlayerInfo info = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(owner) : null;
        return Component.literal(info != null ? info.getProfile().getName() : owner.toString());
    }
}
