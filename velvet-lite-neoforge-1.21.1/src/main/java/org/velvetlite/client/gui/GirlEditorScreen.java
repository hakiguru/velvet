package org.velvetlite.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.client.model.AbstractGirlGeoModel;
import org.velvetlite.client.render.AbstractGirlRenderer;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.BoneTweaks;
import org.velvetlite.entity.girl.GirlBehavior;
import org.velvetlite.entity.girl.GirlNeeds;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.SaveGirlEditorPacket;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.velvetlite.client.gui.PanelArt.*;

/**
 * The NPC editor, opened with the editor wand. Tabs:
 * <ul>
 *   <li>Character: her name and who she belongs to.</li>
 *   <li>Model: her model in a 3D view (drag to turn it, right drag to move it, scroll to zoom) with every
 *       bone listed; the picked bone is tinted and can be scaled, shifted and turned.</li>
 *   <li>AI: the main behavior switches and follow distances.</li>
 *   <li>behavior.json: all of her behavior as JSON, including keys the form doesn't show.</li>
 * </ul>
 * Nothing changes until "Save"; model changes show on her right away meanwhile (only for this player).
 * <p>
 * A plain screen rather than a container screen: those take mouse drags for slots and close on the
 * inventory key, which gets typed into the text fields here.
 */
public class GirlEditorScreen extends Screen implements MenuAccess<GirlEditorMenu> {

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
            return this == JSON ? Component.literal("behavior.json") : Component.translatable("editor.velvet_lite.tab." + name().toLowerCase());
        }
    }

    private static Tab lastTab = Tab.CHARACTER;

    private static final int MARGIN = 8, TOP = 18, BOTTOM_BAR = 28;
    private static final int LIST_W = 92, SLIDERS_W = 116, GAP = 6;
    private static final int LIST_ROW = 10;
    private static final int VIEW_BACKGROUND = 0xFF2E3138;
    private static final int GRID = 0x60FFFFFF, GRID_AXIS_X = 0xC0E05050, GRID_AXIS_Z = 0xC05070E0;
    private static final int SAVED_TICKS = 60;

    private final GirlEditorMenu menu;
    @Nullable private final AbstractGirlEntity girl;
    private Tab tab = lastTab;
    private int left, top, panelW, panelH;
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);

    // ---- what's being edited ----
    private String name;
    private final Map<String, BoneTweaks.Tweak> tweaks;
    private GirlBehavior behavior;
    private String json;
    @Nullable private String jsonError;
    private boolean dirty;
    private int savedTicks;

    // ---- model tab ----
    private record BoneRow(String name, int depth) {}
    private final List<BoneRow> bones = new ArrayList<>();
    @Nullable private String selectedBone;
    private int listScroll;
    private final List<RangeSlider> tweakSliders = new ArrayList<>();
    private static final float VIEW_YAW = 25, VIEW_PITCH = 15;
    private float viewYaw = VIEW_YAW, viewPitch = VIEW_PITCH, viewZoom = 1, panX, panY;
    private int dragButton = -1;

    public GirlEditorScreen(GirlEditorMenu menu, Inventory playerInv, Component title) {
        super(Component.translatable("editor.velvet_lite.title", title));
        this.menu = menu;
        this.girl = menu.getGirl();
        this.name = girl != null ? girl.getCustomNameString() : "";
        this.tweaks = girl != null ? girl.getBoneTweaks().toMutableMap() : new java.util.LinkedHashMap<>();
        this.behavior = GirlBehavior.fromJsonOrDefault(menu.getBehaviorJson());
        this.json = behavior.toJson();
    }

    @Override
    public GirlEditorMenu getMenu() {
        return menu;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Layout ----

    @Override
    protected void init() {
        panelW = Mth.clamp(width - 16, 300, 460);
        panelH = Mth.clamp(height - TAB_RISE - 8, 180, 270);
        left = (width - panelW) / 2;
        top = (height - panelH + TAB_RISE) / 2;
        tabWidgets.clear();
        for (Tab t : Tab.values()) tabWidgets.put(t, new ArrayList<>());
        if (girl == null) return;
        if (bones.isEmpty()) collectBones();

        int x = left + MARGIN, y = top + TOP;
        int contentW = panelW - 2 * MARGIN;

        // character
        EditBox nameBox = add(Tab.CHARACTER, new EditBox(font, x, y + 12, Math.min(200, contentW), 18,
                Component.translatable("editor.velvet_lite.name")));
        nameBox.setMaxLength(SaveGirlEditorPacket.MAX_NAME_LENGTH);
        nameBox.setValue(name);
        nameBox.setHint(Component.literal(girl.getType().getDescription().getString()).withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setResponder(v -> {
            if (!v.equals(name)) dirty = true;
            name = v;
        });

        // model
        int sx = left + panelW - MARGIN - SLIDERS_W;
        int contentBottom = top + panelH - BOTTOM_BAR;
        int step = Math.min(16, (contentBottom - y - 40) / 9);
        tweakSliders.clear();
        String[] kinds = {"scale", "offset", "rotation"};
        String[] axes = {"X", "Y", "Z"};
        for (int k = 0; k < 3; k++) {
            for (int a = 0; a < 3; a++) {
                int kind = k, axis = a;
                double min = kind == 0 ? BoneTweaks.MIN_SCALE : kind == 1 ? -BoneTweaks.MAX_OFFSET : -BoneTweaks.MAX_ROTATION;
                double max = kind == 0 ? BoneTweaks.MAX_SCALE : kind == 1 ? BoneTweaks.MAX_OFFSET : BoneTweaks.MAX_ROTATION;
                double stepSize = kind == 0 ? 0.05 : kind == 1 ? 0.25 : 5;
                RangeSlider slider = add(Tab.MODEL, new RangeSlider(sx, y + (k * 3 + a) * step, SLIDERS_W, step - 1,
                        min, max, stepSize, () -> tweakValue(kind, axis), v -> setTweak(kind, axis, (float) v),
                        v -> Component.translatable("editor.velvet_lite." + kinds[kind], axes[axis], format(kind, v))));
                tweakSliders.add(slider);
            }
        }
        int buttonsY = y + 9 * step + 4;
        add(Tab.MODEL, Button.builder(Component.translatable("editor.velvet_lite.reset_bone"), b -> resetBone())
                .bounds(sx, buttonsY, SLIDERS_W, 16).build());
        add(Tab.MODEL, Button.builder(Component.translatable("editor.velvet_lite.reset_all"), b -> resetAll())
                .bounds(sx, buttonsY + 18, SLIDERS_W, 16).build());
        int[] view = viewport();
        add(Tab.MODEL, Button.builder(Component.literal("⟲"), b -> resetView())
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("editor.velvet_lite.reset_view")))
                .bounds(view[2] - 17, view[1] + 2, 15, 15).build());
        updateTweakSliders();

        // AI
        int col2 = x + contentW / 2 + 4;
        int colW = contentW / 2 - 4;
        int row = 0;
        for (var toggle : List.of(
                new Toggle("wander", () -> behavior.wander, v -> behavior.wander = v),
                new Toggle("pick_up_items", () -> behavior.pickUpItems, v -> behavior.pickUpItems = v),
                new Toggle("auto_eat", () -> behavior.autoEat, v -> behavior.autoEat = v),
                new Toggle("flee_monsters", () -> behavior.fleeMonsters, v -> behavior.fleeMonsters = v))) {
            add(Tab.AI, Checkbox.builder(Component.translatable("editor.velvet_lite.ai." + toggle.key), font)
                    .pos(x, y + 12 + row++ * 22).maxWidth(colW).selected(toggle.get.getAsBoolean())
                    .onValueChange((cb, value) -> {
                        toggle.set.accept(value);
                        behaviorChanged();
                    }).build());
        }
        add(Tab.AI, new RangeSlider(col2, y + 12, colW, 20, GirlBehavior.MIN_STOP, GirlBehavior.MAX_STOP, 0.5,
                () -> behavior.followStopDistance, v -> { behavior.followStopDistance = v; behaviorChanged(); },
                v -> Component.translatable("editor.velvet_lite.ai.stop_distance", blocks(v))));
        add(Tab.AI, new RangeSlider(col2, y + 36, colW, 20, GirlBehavior.MIN_RUN, GirlBehavior.MAX_RUN, 0.5,
                () -> behavior.followRunDistance, v -> { behavior.followRunDistance = v; behaviorChanged(); },
                v -> Component.translatable("editor.velvet_lite.ai.run_distance", blocks(v))));
        add(Tab.AI, new RangeSlider(col2, y + 60, colW, 20, GirlBehavior.MIN_TELEPORT, GirlBehavior.MAX_TELEPORT, 1,
                () -> behavior.followTeleportDistance, v -> { behavior.followTeleportDistance = v; behaviorChanged(); },
                v -> Component.translatable("editor.velvet_lite.ai.teleport_distance", blocks(v))));

        // JSON
        MultiLineEditBox jsonBox = add(Tab.JSON, new MultiLineEditBox(font, x, y, contentW, contentBottom - y - 12,
                Component.literal("{ }"), Component.literal("behavior.json")));
        jsonBox.setCharacterLimit(GirlBehavior.MAX_LENGTH);
        jsonBox.setValue(json);
        jsonBox.setValueListener(v -> {
            if (!v.equals(json)) dirty = true;
            json = v;
        });

        // all tabs
        int by = top + panelH - BOTTOM_BAR + 5;
        addRenderableWidget(Button.builder(Component.translatable("editor.velvet_lite.save"), b -> save())
                .bounds(left + panelW - MARGIN - 2 * 70 - 4, by, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelW - MARGIN - 70, by, 70, 18).build());

        selectTab(tab);
    }

    private record Toggle(String key, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {}

    private <W extends AbstractWidget> W add(Tab owner, W widget) {
        tabWidgets.get(owner).add(widget);
        return addRenderableWidget(widget);
    }

    /** The 3D view between the bone list and the sliders: x0, y0, x1, y1. */
    private int[] viewport() {
        int x0 = left + MARGIN + LIST_W + GAP;
        int x1 = left + panelW - MARGIN - SLIDERS_W - GAP;
        return new int[]{x0, top + TOP, x1, top + panelH - BOTTOM_BAR};
    }

    private int[] boneList() {
        return new int[]{left + MARGIN, top + TOP, left + MARGIN + LIST_W, top + panelH - BOTTOM_BAR};
    }

    // ---- Tabs ----

    /** Switches tabs; leaving the JSON tab takes over what was typed there, unless it doesn't parse. */
    private boolean selectTab(Tab selected) {
        boolean fromJson = tab == Tab.JSON && selected != Tab.JSON;
        if (fromJson && !applyJson()) return false;
        if (selected == Tab.JSON && tab != Tab.JSON) setJsonFromBehavior();
        tab = selected;
        lastTab = selected;
        if (fromJson) {
            rebuildWidgets(); // the AI form shows what was typed; this comes back here with the new tab
            return true;
        }
        tabWidgets.forEach((t, widgets) -> widgets.forEach(w -> w.visible = t == selected));
        if (getFocused() instanceof AbstractWidget w && !w.visible) setFocused(null);
        if (girl != null) girl.clientSelectedBone = selected == Tab.MODEL ? selectedBone : null;
        return true;
    }

    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        int i = PanelArt.tabAt(mouseX, mouseY, left, top, Tab.values().length);
        return i < 0 ? null : Tab.values()[i];
    }

    // ---- Editing ----

    private void behaviorChanged() {
        dirty = true;
    }

    private void setJsonFromBehavior() {
        json = behavior.toJson();
        jsonError = null;
        for (AbstractWidget w : tabWidgets.get(Tab.JSON)) {
            if (w instanceof MultiLineEditBox box) box.setValue(json);
        }
    }

    /** Parses the JSON tab into the behavior; on an error shows it and keeps the old behavior. */
    private boolean applyJson() {
        try {
            behavior = GirlBehavior.fromJson(json);
            jsonError = null;
            return true;
        } catch (RuntimeException e) {
            jsonError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return false;
        }
    }

    private void save() {
        if (girl == null) return;
        if (tab == Tab.JSON && !applyJson()) return;
        NetworkHandler.sendToServer(new SaveGirlEditorPacket(name, BoneTweaks.of(tweaks).toTag(), behavior.toJson()));
        dirty = false;
        savedTicks = SAVED_TICKS;
        if (tab != Tab.JSON) rebuildWidgets(); // the AI form shows what was saved (clamped values)
    }

    private float tweakValue(int kind, int axis) {
        BoneTweaks.Tweak t = selectedBone == null ? null : tweaks.get(selectedBone);
        if (t == null) return kind == 0 ? 1 : 0;
        return (kind == 0 ? t.scale() : kind == 1 ? t.offset() : t.rotation())[axis];
    }

    private void setTweak(int kind, int axis, float value) {
        if (selectedBone == null) return;
        BoneTweaks.Tweak t = tweaks.computeIfAbsent(selectedBone, b -> BoneTweaks.Tweak.identity());
        (kind == 0 ? t.scale() : kind == 1 ? t.offset() : t.rotation())[axis] = value;
        tweaksChanged();
    }

    private void tweaksChanged() {
        dirty = true;
        if (girl != null) girl.clientPreviewTweaks = BoneTweaks.of(tweaks);
    }

    private void resetBone() {
        if (selectedBone == null) return;
        tweaks.remove(selectedBone);
        tweaksChanged();
        updateTweakSliders();
    }

    private void resetAll() {
        tweaks.clear();
        tweaksChanged();
        updateTweakSliders();
    }

    private void resetView() {
        viewYaw = VIEW_YAW;
        viewPitch = VIEW_PITCH;
        viewZoom = 1;
        panX = panY = 0;
    }

    private void selectBone(@Nullable String bone) {
        selectedBone = bone;
        if (girl != null) girl.clientSelectedBone = bone;
        updateTweakSliders();
    }

    private void updateTweakSliders() {
        for (RangeSlider s : tweakSliders) {
            s.sync();
            s.active = selectedBone != null;
        }
    }

    private static String format(int kind, double v) {
        return kind == 0 ? String.format("%.2f", v) : kind == 1 ? String.format("%+.2f", v) : String.format("%+.0f°", v);
    }

    private static String blocks(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    /** Her bones in model order (children indented), without the partner's body and the armor overlays. */
    @SuppressWarnings("unchecked")
    private void collectBones() {
        if (minecraft == null || girl == null) return;
        if (!(minecraft.getEntityRenderDispatcher().getRenderer(girl) instanceof AbstractGirlRenderer<?> renderer)) return;
        var model = (software.bernie.geckolib.model.GeoModel<AbstractGirlEntity>) (Object) renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(girl));
        for (GeoBone bone : baked.topLevelBones()) addBone(bone, 0);
    }

    private void addBone(GeoBone bone, int depth) {
        String n = bone.getName();
        if (n.equals(AbstractGirlGeoModel.PARTNER_BONE) || n.startsWith("armor")) return;
        bones.add(new BoneRow(n, depth));
        for (GeoBone child : bone.getChildBones()) addBone(child, depth + 1);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Tab clicked = button == 0 ? tabAt(mouseX, mouseY) : null;
        if (clicked != null) {
            if (clicked != tab && selectTab(clicked) && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (tab == Tab.MODEL) {
            int[] list = boneList();
            if (inside(mouseX, mouseY, list) && button == 0) {
                int row = (int) ((mouseY - list[1] - 2) / LIST_ROW) + listScroll;
                if (row >= 0 && row < bones.size()) {
                    String bone = bones.get(row).name();
                    selectBone(bone.equals(selectedBone) ? null : bone);
                }
                return true;
            }
            if (inside(mouseX, mouseY, viewport())) {
                setFocused(null);
                dragButton = button;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragButton >= 0 && button == dragButton) {
            if (button == 0) {
                viewYaw += (float) dragX * 0.8F;
                viewPitch = Mth.clamp(viewPitch + (float) dragY * 0.8F, -89, 89);
            } else {
                panX += (float) dragX;
                panY += (float) dragY;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == dragButton) dragButton = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.MODEL) {
            if (inside(mouseX, mouseY, viewport())) {
                viewZoom = Mth.clamp(viewZoom * (float) Math.pow(1.12, scrollY), 0.4F, 5);
                return true;
            }
            int[] list = boneList();
            if (inside(mouseX, mouseY, list)) {
                int visible = (list[3] - list[1] - 4) / LIST_ROW;
                listScroll = Mth.clamp(listScroll - (int) Math.signum(scrollY) * 3, 0, Math.max(0, bones.size() - visible));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean inside(double x, double y, int[] r) {
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    @Override
    public void tick() {
        super.tick();
        if (savedTicks > 0) savedTicks--;
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (girl != null) {
            girl.clientPreviewTweaks = null;
            girl.clientSelectedBone = null;
        }
    }

    // ---- Rendering ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        for (Tab t : Tab.values()) if (t != tab) drawTab(g, t);
        drawPanel(g, left, top, panelW, panelH);
        drawTab(g, tab);
        joinOpenTab(g, tabX(left, tab.ordinal()), top);
    }

    private void drawTab(GuiGraphics g, Tab t) {
        int[] icon = PanelArt.drawTab(g, tabX(left, t.ordinal()), top, t == tab);
        g.renderItem(t.icon, icon[0], icon[1]);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background, panel and widgets
        g.drawString(font, title, left + MARGIN, top + 6, LABEL, false);
        Component tabTitle = tab.title();
        g.drawString(font, tabTitle, left + panelW - MARGIN - font.width(tabTitle), top + 6, LABEL, false);
        if (girl == null) return;

        switch (tab) {
            case CHARACTER -> renderCharacter(g);
            case MODEL -> renderModelTab(g, mouseX, mouseY, partialTick);
            case AI -> g.drawString(font, Component.translatable("editor.velvet_lite.ai.hint"),
                    left + MARGIN, top + TOP + 12 + 4 * 22 + 6, MUTED, false);
            case JSON -> {
                if (jsonError != null) {
                    g.drawString(font, font.plainSubstrByWidth("⚠ " + jsonError, panelW - 2 * MARGIN),
                            left + MARGIN, top + panelH - BOTTOM_BAR - 10, 0xFFB02020, false);
                }
            }
        }
        // status in the bottom bar
        int sy = top + panelH - BOTTOM_BAR + 10;
        if (savedTicks > 0) {
            g.drawString(font, Component.translatable("editor.velvet_lite.saved"), left + MARGIN, sy, 0xFF2E7D32, false);
        } else if (dirty) {
            g.drawString(font, Component.translatable("editor.velvet_lite.unsaved"), left + MARGIN, sy, MUTED, false);
        }
        Tab hovered = tabAt(mouseX, mouseY);
        if (hovered != null) g.renderTooltip(font, hovered.title(), mouseX, mouseY);
    }

    private void renderCharacter(GuiGraphics g) {
        int x = left + MARGIN, y = top + TOP;
        g.drawString(font, Component.translatable("editor.velvet_lite.name"), x, y + 1, LABEL, false);
        y += 40;
        line(g, x, y, "editor.velvet_lite.type", girl.getType().getDescription());
        line(g, x, y + 12, "editor.velvet_lite.owner", ownerName());
        var home = girl.getHomePos();
        line(g, x, y + 24, "editor.velvet_lite.home", home == null
                ? Component.translatable("editor.velvet_lite.none") : Component.literal(home.toShortString()));
        line(g, x, y + 36, "editor.velvet_lite.health", Component.literal(Math.round(girl.getHealth()) + " / " + Math.round(girl.getMaxHealth())));
        line(g, x, y + 48, "editor.velvet_lite.food", Component.literal(girl.getFood() + " / " + GirlNeeds.MAX_FOOD));
        line(g, x, y + 60, "editor.velvet_lite.id", Component.literal(girl.getGirlUUID().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private void line(GuiGraphics g, int x, int y, String key, Component value) {
        Component label = Component.translatable(key);
        g.drawString(font, label, x, y, MUTED, false);
        g.drawString(font, value, x + 70, y, LABEL, false);
    }

    private Component ownerName() {
        UUID owner = girl.getOwnerUUID();
        if (owner == null) return Component.translatable("editor.velvet_lite.none");
        if (minecraft != null && minecraft.player != null && owner.equals(minecraft.player.getUUID())) {
            return Component.translatable("editor.velvet_lite.owner_you", minecraft.player.getName());
        }
        PlayerInfo info = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(owner) : null;
        return Component.literal(info != null ? info.getProfile().getName() : owner.toString());
    }

    private void renderModelTab(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // bone list
        int[] list = boneList();
        drawInset(g, list[0], list[1], list[2] - list[0], list[3] - list[1], 0xFF8B8B8B);
        int visible = (list[3] - list[1] - 4) / LIST_ROW;
        for (int i = 0; i < visible && i + listScroll < bones.size(); i++) {
            BoneRow row = bones.get(i + listScroll);
            int ry = list[1] + 2 + i * LIST_ROW;
            boolean selected = row.name().equals(selectedBone);
            boolean edited = tweaks.containsKey(row.name()) && !tweaks.get(row.name()).isIdentity();
            if (selected) g.fill(list[0], ry - 1, list[2], ry + LIST_ROW - 1, 0xFF5A5A9A);
            else if (mouseX >= list[0] && mouseX < list[2] && mouseY >= ry - 1 && mouseY < ry + LIST_ROW - 1) {
                g.fill(list[0], ry - 1, list[2], ry + LIST_ROW - 1, 0xFF9C9C9C);
            }
            int indent = Math.min(row.depth(), 6) * 4;
            String text = font.plainSubstrByWidth((edited ? "• " : "") + row.name(), list[2] - list[0] - 4 - indent);
            g.drawString(font, text, list[0] + 2 + indent, ry, selected ? 0xFFFFFFFF : edited ? 0xFF1A3E8C : 0xFF202020, false);
        }
        if (bones.size() > visible) {
            // scroll bar
            int h = list[3] - list[1];
            int barH = Math.max(8, h * visible / bones.size());
            int barY = list[1] + (h - barH) * listScroll / Math.max(1, bones.size() - visible);
            g.fill(list[2] - 2, barY, list[2], barY + barH, 0xFF505050);
        }

        // 3D view
        int[] v = viewport();
        drawInset(g, v[0], v[1], v[2] - v[0], v[3] - v[1], VIEW_BACKGROUND);
        g.enableScissor(v[0], v[1], v[2], v[3]);
        renderView(g, v, partialTick);
        g.disableScissor();
        Component hint = Component.translatable("editor.velvet_lite.view_hint");
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), v[2] - v[0] - 4), v[0] + 3, v[3] - 10, 0x90FFFFFF, false);
        if (selectedBone == null) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("editor.velvet_lite.pick_bone").getString(),
                    v[2] - v[0] - 22), v[0] + 3, v[1] + 4, 0xC0FFFFFF, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth(selectedBone, v[2] - v[0] - 22), v[0] + 3, v[1] + 4, 0xFFFF9A9A, false);
        }
        // widgets were drawn under the view's background: the reset-view button goes on top again
        for (AbstractWidget w : tabWidgets.get(Tab.MODEL)) {
            if (w instanceof Button b && b.getX() > v[0] && b.getY() < v[1] + 20) b.render(g, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Her model in its own little 3D space: turned around her middle, with a floor grid (red line = X,
     * blue line = Z) under her feet, one cell per block.
     */
    private void renderView(GuiGraphics g, int[] v, float partialTick) {
        float height = girl.getBbHeight();
        float scale = (v[3] - v[1]) / (height * 1.6F) * viewZoom;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((v[0] + v[2]) / 2.0F + panX, (v[1] + v[3]) / 2.0F + panY, 400);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-viewPitch));
        pose.mulPose(Axis.YP.rotationDegrees(viewYaw));
        pose.translate(0, -height / 2, 0);

        drawGrid(g, pose.last());

        // face the same way whatever she was doing; restored afterwards like the vanilla inventory preview
        float bodyRot = girl.yBodyRot, bodyRotO = girl.yBodyRotO, yRot = girl.getYRot(), xRot = girl.getXRot();
        float headRot = girl.yHeadRot, headRotO = girl.yHeadRotO;
        girl.yBodyRot = girl.yBodyRotO = 180;
        girl.setYRot(180);
        girl.setXRot(0);
        girl.yHeadRot = girl.yHeadRotO = 180;
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        AbstractGirlRenderer.renderingInGui = true;
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(girl, 0, 0, 0, 0, partialTick, pose, g.bufferSource(), 0xF000F0));
            g.flush();
        } finally {
            AbstractGirlRenderer.renderingInGui = false;
            dispatcher.setRenderShadow(true);
            girl.yBodyRot = bodyRot;
            girl.yBodyRotO = bodyRotO;
            girl.setYRot(yRot);
            girl.setXRot(xRot);
            girl.yHeadRot = headRot;
            girl.yHeadRotO = headRotO;
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    private void drawGrid(GuiGraphics g, PoseStack.Pose pose) {
        VertexConsumer lines = g.bufferSource().getBuffer(RenderType.lines());
        int n = 2;
        for (int i = -n; i <= n; i++) {
            int colourX = i == 0 ? GRID_AXIS_X : GRID;
            int colourZ = i == 0 ? GRID_AXIS_Z : GRID;
            line(lines, pose, -n, i, n, i, colourX);  // along X
            line(lines, pose, i, -n, i, n, colourZ);  // along Z
        }
        g.flush();
    }

    /** The lines shader widens a line across its normal, which has to be turned like the line itself. */
    private static void line(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, int colour) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        vc.addVertex(pose, x0, 0, z0).setColor(colour).setNormal(pose, dx / len, 0, dz / len);
        vc.addVertex(pose, x1, 0, z1).setColor(colour).setNormal(pose, dx / len, 0, dz / len);
    }
}
