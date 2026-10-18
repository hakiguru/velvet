package org.velvet.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
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
import org.velvet.client.gui.editor.AiTab;
import org.velvet.client.gui.editor.CharacterTab;
import org.velvet.client.gui.editor.DebugTab;
import org.velvet.client.gui.editor.EditorState;
import org.velvet.client.gui.editor.EditorTab;
import org.velvet.client.gui.editor.ModelTab;
import org.velvet.client.gui.editor.ModelView;
import org.velvet.client.gui.editor.PresetTab;
import org.velvet.client.skin.SkinCache;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.BoneTweaks;
import org.velvet.entity.girl.GirlBehavior;
import org.velvet.entity.girl.GirlType;
import org.velvet.entity.girl.NpcSettings;
import org.velvet.menu.GirlEditorMenu;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.CreateNpcPacket;
import org.velvet.network.packet.EditorSelectGirlPacket;
import org.velvet.network.packet.ModelListPacket;
import org.velvet.network.packet.SaveGirlEditorPacket;
import org.velvet.registry.VelvetItems;
import org.velvet.skin.GirlSkin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The NPC editor, opened with the editor wand (or the debug tool, with its own tabs on top). On the left, a
 * tab for each of the player's girls (her face and name; scrolls smoothly when there are many) and "New NPC"
 * under them; girls far away show but can't be edited until they're in a loaded area. Along the top:
 * <ul>
 *   <li>Character: her name, how she goes about her day, and what there is to know about her.</li>
 *   <li>Preset: the model she wears, with its animations and scenes.</li>
 *   <li>Model: a small Blockbench, see {@link ModelTab}.</li>
 *   <li>AI: her brain (built in or a language model) and her prompt, see {@link AiTab}.</li>
 *   <li>JSON: all of her behavior as JSON, including keys the forms don't show.</li>
 *   <li>Debug tool only: instinct, character, levels and flags, see {@link DebugTab}.</li>
 * </ul>
 * Nothing changes until "Save"; the model view shows the changes meanwhile. Picking another girl's tab keeps
 * this screen where it is: the server points the open editor at her and a new screen with her data takes
 * this one's place.
 * <p>
 * A plain screen rather than a container screen: those take mouse drags for slots and close on the
 * inventory key, which gets typed into the text fields here.
 */
public class GirlEditorScreen extends Screen implements MenuAccess<GirlEditorMenu>, EditorTab.Host {

    private enum Tab {
        CHARACTER(new ItemStack(Items.NAME_TAG), false),
        PRESET(new ItemStack(VelvetItems.NPC_SPAWN_EGG.get()), false),
        MODEL(new ItemStack(Items.ARMOR_STAND), false),
        AI(new ItemStack(Items.COMPARATOR), false),
        JSON(new ItemStack(Items.WRITABLE_BOOK), false),
        INSTINCT(new ItemStack(Items.SHIELD), true),
        PERSONALITY(new ItemStack(Items.BOOK), true),
        LEVELS(new ItemStack(Items.EXPERIENCE_BOTTLE), true),
        FLAGS(new ItemStack(Items.COMMAND_BLOCK), true);

        final ItemStack icon;
        final boolean debug;

        Tab(ItemStack icon, boolean debug) {
            this.icon = icon;
            this.debug = debug;
        }

        Component title() {
            return this == JSON ? Component.literal("JSON") : Component.translatable("editor.velvet.tab." + name().toLowerCase());
        }
    }

    private static Tab lastTab = Tab.CHARACTER;
    /** The list of girls keeps its scroll when the screen is made again for another girl. */
    private static float lastNpcScroll;

    private static final int MARGIN = 8, TOP = 18, BOTTOM_BAR = 28;
    private static final int NPC_TAB_W = 76, NPC_TAB_H = 30, NPC_TAB_GAP = 1, SCROLLER_W = 8;
    private static final int SAVED_TICKS = 60;

    private final GirlEditorMenu menu;
    private final GirlEditorMenu.Data data;
    @Nullable private final AbstractGirlEntity girl;
    @Nullable private final GirlEditorMenu.GirlEntry target;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab tab;
    private int left, top, panelW, panelH;
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);
    private final Map<Tab, EditorTab> panels = new EnumMap<>(Tab.class);
    @Nullable private Tab adding;

    private final EditorState state;
    private final AiTab aiTab;
    private String json;
    @Nullable private String jsonError;
    private int savedTicks;

    /** Stand-ins of every girl in the list, for the faces on their tabs. */
    private final Map<UUID, AbstractGirlEntity> faces = new HashMap<>();
    /** Scroll of the girls' list in pixels: where it is and where it glides to. */
    private float npcScroll = lastNpcScroll, npcScrollTarget = lastNpcScroll;
    private boolean draggingScroller;
    private double dragGrab;
    /** Clicked another girl's tab with unsaved changes: once more to leave them. */
    @Nullable private UUID leaveConfirm;

    public GirlEditorScreen(GirlEditorMenu menu, Inventory playerInv, Component title) {
        super(Component.translatable("editor.velvet.title", title));
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
        GirlType preset = found != null ? GirlType.byId(found.appearance().getString("preset")) : null;
        if (preset == null) preset = girl != null ? girl.getGirlType() : GirlType.UNKNOWN;
        BoneTweaks tweaks = found != null ? BoneTweaks.fromTag(found.appearance().getCompound("bone_tweaks")) : BoneTweaks.NONE;
        GirlSkin skin = found != null ? GirlSkin.decode(found.appearance().getString("skin")) : null;
        this.state = new EditorState(name, preset, tweaks, skin, behavior, data.debug(), NpcSettings.fromJson(data.settingsJson()),
                data.skillLevels(), preview);
        this.json = behavior.toJson();
        this.aiTab = new AiTab(data.keys(), data.localEndpoints(), behavior.provider);
        for (Tab t : Tab.values()) if (!t.debug || data.debug()) tabs.add(t);
        this.tab = tabs.contains(lastTab) ? lastTab : Tab.CHARACTER;
    }

    @Nullable
    private AbstractGirlEntity standIn(GirlEditorMenu.GirlEntry e) {
        if (minecraft == null) minecraft = Minecraft.getInstance();
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

    /** From the server: the editor now edits {@code girl}; a fresh screen for her takes this one's place. */
    public void retarget(@Nullable AbstractGirlEntity girl, GirlEditorMenu.Data data) {
        menu.retargetClient(girl, data);
        lastNpcScroll = npcScrollTarget;
        if (minecraft == null || minecraft.player == null) return;
        Component title = girl == null ? Component.translatable("editor.velvet.no_npc") : girl.getName();
        // straight from screen to screen: the mouse stays free and where it is
        minecraft.setScreen(new GirlEditorScreen(menu, minecraft.player.getInventory(), title));
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
        panelH = Mth.clamp(height - TAB_RISE - 8, 220, 300);
        left = (width - panelW + NPC_TAB_W + SCROLLER_W) / 2;
        top = (height - panelH + TAB_RISE) / 2;
        tabWidgets.clear();
        panels.clear();
        for (Tab t : Tab.values()) tabWidgets.put(t, new ArrayList<>());
        npcScroll = npcScrollTarget = Mth.clamp(npcScrollTarget, 0, maxNpcScroll());

        int x = left + MARGIN, y = top + TOP;
        int contentW = panelW - 2 * MARGIN;
        int contentH = panelH - TOP - BOTTOM_BAR;

        if (target != null && girlEditable()) {
            panels.put(Tab.CHARACTER, new CharacterTab(target, girl));
            panels.put(Tab.PRESET, new PresetTab());
            panels.put(Tab.MODEL, new ModelTab());
            panels.put(Tab.AI, aiTab);
            if (state.debug) {
                panels.put(Tab.INSTINCT, new DebugTab(DebugTab.Page.INSTINCT));
                panels.put(Tab.PERSONALITY, new DebugTab(DebugTab.Page.CHARACTER));
                panels.put(Tab.LEVELS, new DebugTab(DebugTab.Page.LEVELS));
                panels.put(Tab.FLAGS, new DebugTab(DebugTab.Page.FLAGS));
            }
            for (var e : panels.entrySet()) {
                adding = e.getKey();
                e.getValue().init(this, x, y, contentW, contentH);
            }
            adding = Tab.JSON;
            TextArea jsonBox = add(new TextArea(font, x, y, contentW, contentH - 12, Component.literal("{ }"), Component.literal("JSON")));
            jsonBox.setCharacterLimit(GirlBehavior.MAX_LENGTH);
            jsonBox.setValue(json);
            jsonBox.setValueListener(v -> {
                if (!v.equals(json)) state.dirty = true;
                json = v;
            });
            adding = null;

            int by = top + panelH - BOTTOM_BAR + 5;
            addRenderableWidget(Button.builder(Component.translatable("editor.velvet.save"), b -> save())
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
        EditorTab old = panels.get(before);
        if (before != selected && old != null) old.onHide();
        EditorTab panel = panel();
        if (panel != null) panel.onShow();
        return true;
    }

    @Nullable
    private EditorTab panel() {
        return girlEditable() ? panels.get(tab) : null;
    }

    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        int i = PanelArt.tabAt(mouseX, mouseY, left, top, tabs.size());
        return i < 0 ? null : tabs.get(i);
    }

    // ---- Editing ----

    private void setJsonFromBehavior() {
        json = state.behavior.toJson();
        jsonError = null;
        for (AbstractWidget w : tabWidgets.get(Tab.JSON)) {
            if (w instanceof TextArea box) box.setValue(json);
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
        int[] levels = state.debug ? state.skillLevels : new int[0];
        NetworkHandler.sendToServer(new SaveGirlEditorPacket(state.name, state.preset.id(), BoneTweaks.of(state.tweaks).toTag(),
                state.skin == null ? "" : state.skin.encode(), state.behavior.toJson(),
                state.debug ? state.settings.toJson() : "", levels));
        state.dirty = false;
        savedTicks = SAVED_TICKS;
        AbstractGirlEntity face = target == null ? null : faces.get(target.id());
        if (face != null) {
            face.setCustomNameString(state.name);
            face.setGirlType(state.preset);
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

    private int listTop() {
        return top + 2;
    }

    private int listBottom() {
        return top + panelH - 2;
    }

    /** The girls and "New NPC" under them. */
    private int rowCount() {
        return data.girls().size() + 1;
    }

    private int rowStep() {
        return NPC_TAB_H + NPC_TAB_GAP;
    }

    private float maxNpcScroll() {
        return Math.max(0, rowCount() * rowStep() - NPC_TAB_GAP - (listBottom() - listTop()));
    }

    /** Which row is under the mouse: a girl's index, {@code girls.size()} for "New NPC", -1 for none. */
    private int rowAt(double mx, double my) {
        int tx = npcTabX();
        if (mx < tx || mx >= tx + NPC_TAB_W - 3 || my < listTop() || my >= listBottom()) return -1;
        double inList = my - listTop() + npcScroll;
        int i = (int) (inList / rowStep());
        if (i < 0 || i >= rowCount() || inList - i * rowStep() >= NPC_TAB_H) return -1;
        return i;
    }

    private int[] scroller() {
        int x0 = npcTabX() - SCROLLER_W - 2;
        return new int[]{x0, listTop(), x0 + SCROLLER_W, listBottom()};
    }

    private int thumbHeight() {
        int track = listBottom() - listTop();
        int content = rowCount() * rowStep();
        return Mth.clamp(track * track / Math.max(1, content), 15, track);
    }

    private int thumbY() {
        int track = listBottom() - listTop();
        float max = maxNpcScroll();
        return listTop() + Math.round((track - thumbHeight()) * (max <= 0 ? 0 : npcScroll / max));
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

    private void newNpc() {
        if (state.dirty && leaveConfirm == null) {
            leaveConfirm = new UUID(0, 0);
            return;
        }
        NetworkHandler.sendToServer(new CreateNpcPacket());
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Tab clicked = button == 0 ? tabAt(mouseX, mouseY) : null;
        if (clicked != null) {
            if (clicked != tab && selectTab(clicked)) click();
            return true;
        }
        int row = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (row >= 0) {
            click();
            if (row == data.girls().size()) newNpc();
            else pickGirl(data.girls().get(row));
            return true;
        }
        int[] sc = scroller();
        if (button == 0 && maxNpcScroll() > 0 && mouseX >= sc[0] && mouseX < sc[2] && mouseY >= sc[1] && mouseY < sc[3]) {
            draggingScroller = true;
            int ty = thumbY();
            // grabbed on the thumb it stays under the mouse; elsewhere the thumb jumps there first
            dragGrab = mouseY >= ty && mouseY < ty + thumbHeight() ? mouseY - ty : thumbHeight() / 2.0;
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

    /** Dragging the thumb: the list follows it pixel by pixel. */
    private void scrollTo(double mouseY) {
        int track = listBottom() - listTop() - thumbHeight();
        float f = track <= 0 ? 0 : (float) ((mouseY - dragGrab - listTop()) / track);
        npcScroll = npcScrollTarget = Mth.clamp(f, 0, 1) * maxNpcScroll();
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
            // a row per notch, gliding there
            npcScrollTarget = Mth.clamp(npcScrollTarget - (float) scrollY * rowStep(), 0, maxNpcScroll());
            return true;
        }
        EditorTab panel = panel();
        if (panel != null && panel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean typing = getFocused() instanceof EditBox || getFocused() instanceof TextArea;
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
        lastNpcScroll = npcScrollTarget;
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
        // glide towards where the wheel sent the list
        npcScroll += (npcScrollTarget - npcScroll) * Math.min(1, 0.4F + partialTick * 0.1F);
        if (Math.abs(npcScrollTarget - npcScroll) < 0.5F) npcScroll = npcScrollTarget;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i) != tab) drawTab(g, i);
        renderNpcTabs(g, mouseX, mouseY, false);
        drawPanel(g, left, top, panelW, panelH);
        int open = tabs.indexOf(tab);
        drawTab(g, open);
        joinOpenTab(g, tabX(left, open), top);
        renderNpcTabs(g, mouseX, mouseY, true);
    }

    private void drawTab(GuiGraphics g, int index) {
        Tab t = tabs.get(index);
        int[] icon = PanelArt.drawTab(g, tabX(left, index), top, t == tab);
        g.renderItem(t.icon, icon[0], icon[1]);
        if (t.debug) g.fill(tabX(left, index) + 3, top - TAB_RISE + 2 + (t == tab ? 0 : 3), tabX(left, index) + TAB_W - 3,
                top - TAB_RISE + 3 + (t == tab ? 0 : 3), 0xFFD04848);
    }

    /** The girls' tabs left of the panel: the open one in front of its edge, the others behind it. */
    private void renderNpcTabs(GuiGraphics g, int mouseX, int mouseY, boolean openOnly) {
        int tx = npcTabX();
        List<GirlEditorMenu.GirlEntry> girls = data.girls();
        g.enableScissor(tx - 1, listTop(), tx + NPC_TAB_W + 4, listBottom());
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < rowCount(); i++) {
            int ty = Math.round(listTop() + i * rowStep() - npcScroll);
            if (ty + NPC_TAB_H < listTop() || ty > listBottom()) continue;
            if (i == girls.size()) {
                if (!openOnly) renderNewNpcRow(g, tx, ty, hovered == i);
                continue;
            }
            GirlEditorMenu.GirlEntry e = girls.get(i);
            boolean open = target != null && e.id().equals(target.id());
            if (open != openOnly) continue;
            int x0 = open ? tx : tx + 3;
            int x1 = tx + NPC_TAB_W;
            g.fill(x0 + 1, ty, x1, ty + NPC_TAB_H, OUTLINE);
            g.fill(x0, ty + 1, x1, ty + NPC_TAB_H - 1, OUTLINE);
            int fill = open ? PANEL : hovered == i && e.loaded() ? 0xFFB8B8B8 : 0xFFA8A8A8;
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
                g.enableScissor(fx0, Math.max(fy0, listTop()), fx1, Math.min(fy1, listBottom()));
                ModelView.render(g, face, fx0, fy0, fx1, fy1, 20, 5, 3.4F, 0, 0, 0.86F, 0.9F, false, null, 0);
                g.disableScissor();
            }
            // her name, on up to two lines
            String name = e.appearance().getString("name");
            Component label = name.isEmpty() ? presetName(e) : Component.literal(name);
            int textX = fx1 + 3, textW = x1 - textX - 2;
            var lines = font.split(label, textW);
            int colour = e.loaded() ? LABEL : MUTED;
            for (int l = 0; l < Math.min(2, lines.size()); l++) g.drawString(font, lines.get(l), textX, ty + 6 + l * 10, colour, false);
            if (!e.loaded()) g.drawString(font, "⌛", x1 - 9, ty + NPC_TAB_H - 11, MUTED, false);
        }
        g.disableScissor();
        if (openOnly && maxNpcScroll() > 0) {
            int[] sc = scroller();
            drawInset(g, sc[0], sc[1], SCROLLER_W, sc[3] - sc[1], 0xFF8B8B8B);
            int thumbY = thumbY(), th = thumbHeight();
            g.fill(sc[0], thumbY, sc[2], thumbY + th, draggingScroller ? 0xFFD8D8D8 : 0xFFC6C6C6);
            g.fill(sc[0], thumbY, sc[2] - 1, thumbY + 1, LIGHT);
            g.fill(sc[0], thumbY, sc[0] + 1, thumbY + th - 1, LIGHT);
            g.fill(sc[0] + 1, thumbY + th - 1, sc[2], thumbY + th, SHADOW);
            g.fill(sc[2] - 1, thumbY + 1, sc[2], thumbY + th, SHADOW);
        }
    }

    /** "New NPC" in the last row of the list. */
    private void renderNewNpcRow(GuiGraphics g, int tx, int ty, boolean hover) {
        int x0 = tx + 3, x1 = tx + NPC_TAB_W;
        g.fill(x0 + 1, ty, x1, ty + NPC_TAB_H, OUTLINE);
        g.fill(x0, ty + 1, x1, ty + NPC_TAB_H - 1, OUTLINE);
        g.fill(x0 + 1, ty + 1, x1, ty + NPC_TAB_H - 1, hover ? 0xFF8FC48F : 0xFF7DAE7D);
        g.fill(x0 + 1, ty + 1, x1, ty + 3, 0xFFB8E0B8);
        g.fill(x0 + 1, ty + NPC_TAB_H - 3, x1, ty + NPC_TAB_H - 1, 0xFF4E7A4E);
        g.renderItem(new ItemStack(VelvetItems.NPC_SPAWN_EGG.get()), x0 + 4, ty + 7);
        var lines = font.split(Component.translatable("editor.velvet.new_npc"), x1 - x0 - 26);
        for (int l = 0; l < Math.min(2, lines.size()); l++) {
            g.drawString(font, lines.get(l), x0 + 23, ty + (lines.size() == 1 ? 11 : 6) + l * 10, 0xFF1E3A1E, false);
        }
    }

    private static Component presetName(GirlEditorMenu.GirlEntry e) {
        GirlType preset = GirlType.byId(e.appearance().getString("preset"));
        return preset != null ? preset.displayName() : BuiltInRegistries.ENTITY_TYPE.get(e.type()).getDescription();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background, panels and widgets
        g.drawString(font, title, left + MARGIN, top + 6, LABEL, false);
        Component tabTitle = tab.title();
        g.drawString(font, tabTitle, left + panelW - MARGIN - font.width(tabTitle), top + 6, tab.debug ? 0xFFB02020 : LABEL, false);

        if (!girlEditable()) {
            renderNoGirl(g);
        } else {
            if (tab == Tab.JSON && jsonError != null) {
                g.drawString(font, font.plainSubstrByWidth("⚠ " + jsonError, panelW - 2 * MARGIN),
                        left + MARGIN, top + panelH - BOTTOM_BAR - 10, 0xFFB02020, false);
            }
            EditorTab panel = panel();
            if (panel != null) {
                panel.render(g, mouseX, mouseY, partialTick);
                // widgets drawn under the panel's own drawing: on top again
                for (AbstractWidget w : tabWidgets.get(tab)) if (w.visible) w.render(g, mouseX, mouseY, partialTick);
                panel.renderOver(g, mouseX, mouseY, partialTick);
            }
        }

        // status in the bottom bar
        int sy = top + panelH - BOTTOM_BAR + 10;
        if (leaveConfirm != null && state.dirty) {
            g.drawString(font, Component.translatable("editor.velvet.leave_unsaved"), left + MARGIN, sy, 0xFFB02020, false);
        } else if (savedTicks > 0) {
            g.drawString(font, Component.translatable("editor.velvet.saved"), left + MARGIN, sy, 0xFF2E7D32, false);
        } else if (state.dirty) {
            g.drawString(font, Component.translatable("editor.velvet.unsaved"), left + MARGIN, sy, MUTED, false);
        }

        Tab hovered = tabAt(mouseX, mouseY);
        int row = rowAt(mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(font, hovered.title(), mouseX, mouseY);
        } else if (row == data.girls().size()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("editor.velvet.new_npc"));
            boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
            lines.add(Component.translatable(creative ? "editor.velvet.new_npc.free" : "editor.velvet.new_npc.costs")
                    .withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (row >= 0) {
            GirlEditorMenu.GirlEntry npc = data.girls().get(row);
            List<Component> lines = new ArrayList<>();
            String name = npc.appearance().getString("name");
            lines.add(name.isEmpty() ? presetName(npc) : Component.literal(name));
            BlockPos p = npc.pos();
            lines.add(Component.literal(npc.dimension().getPath() + ": " + p.getX() + " " + p.getY() + " " + p.getZ())
                    .withStyle(ChatFormatting.GRAY));
            if (!npc.loaded()) lines.add(Component.translatable("editor.velvet.not_loaded").withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void renderNoGirl(GuiGraphics g) {
        Component text = data.girls().isEmpty() ? Component.translatable("editor.velvet.no_npcs")
                : target != null ? Component.translatable("editor.velvet.not_loaded")
                : Component.translatable("editor.velvet.pick_npc");
        int y = top + TOP + 20;
        for (var line : font.split(text, panelW - 2 * MARGIN)) {
            g.drawString(font, line, left + MARGIN, y, LABEL, false);
            y += 10;
        }
    }
}
