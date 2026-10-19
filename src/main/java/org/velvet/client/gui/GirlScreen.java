package org.velvet.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.render.AbstractGirlRenderer;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlNeeds;
import org.velvet.entity.girl.GirlSkill;
import org.velvet.entity.girl.GirlType;
import org.velvet.entity.girl.scene.SceneType;
import org.velvet.menu.GirlMenu;
import org.velvet.mixin.SlotAccessor;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.SendGirlHomePacket;
import org.velvet.network.packet.SetFollowingPacket;
import org.velvet.network.packet.SetHomePositionPacket;
import org.velvet.network.packet.SetOutfitPacket;
import org.velvet.network.packet.StartScenePacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.velvet.client.gui.PanelArt.*;

/**
 * The girl's menu, split into tabs along the top edge:
 * <ul>
 *   <li>Inventory: her model with armor and hand slots, health, food and armor, her inventory (as many slots
 *       as she has open, in rows of up to nine) and the player's. As small as that takes, like a chest.</li>
 *   <li>Levels: every skill with its level and progress towards the next.</li>
 *   <li>Commands: follow, home, outfit. Toggle buttons always show the action that applies right now.</li>
 *   <li>Actions: the paid scenes, every one any NPC can play, in one list; scrolls.</li>
 * </ul>
 * Each tab has the size it needs, and the window can be resized by its edges like any of the mod's (the size
 * is kept, see {@link WindowFrame}); the slots are laid out again for it.
 */
public class GirlScreen extends AbstractContainerScreen<GirlMenu> {

    public enum Tab {
        INVENTORY(new ItemStack(Items.CHEST)),
        LEVELS(new ItemStack(Items.EXPERIENCE_BOTTLE)),
        COMMANDS(new ItemStack(Items.BELL)),
        /** Drawn with the HUD heart instead of an item. */
        ACTIONS(null);

        @Nullable final ItemStack icon;

        Tab(@Nullable ItemStack icon) {
            this.icon = icon;
        }

        Component title() {
            return Component.translatable("gui.velvet.tab." + name().toLowerCase());
        }
    }

    /** Remembered while the game runs, so the menu opens on the tab used last. */
    private static Tab lastTab = Tab.INVENTORY;

    private static final int PAD = 8, TITLE = 18;
    private static final int MODEL_W = 50, MODEL_H = 72;
    private static final int BUTTON_H = 20, BUTTON_STEP = 22;
    private static final int SKILL_STEP = 16;
    private static final int HEADER_H = 12;
    /** Behind the model: dark enough to frame her, light enough not to swallow dark clothes. */
    private static final int MODEL_BACKGROUND = 0xFF6B6B6B;

    private Tab tab = lastTab;
    private final WindowFrame frame = new WindowFrame(this::rebuildWidgets);
    /** Widgets of each tab, shown only while it's open. */
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);
    private final List<Button> sceneButtons = new ArrayList<>();
    private Button followButton;
    private Button outfitButton;
    private Button homeButton;
    private Button setHomeButton;
    private int sceneScroll;

    // the inventory tab's layout, relative to the panel
    private int modelX, modelY, statsX, statsY, bagLabelY, playerLabelX, playerLabelY;

    public GirlScreen(GirlMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    // ---- Layout ----

    /** What each tab takes (its smallest size): the inventory tab is laid out for her number of slots. */
    private int[] contentSize(Tab t) {
        AbstractGirlEntity girl = menu.getGirl();
        return switch (t) {
            case INVENTORY -> {
                int rows = bagRows(girl);
                int playerY = TITLE + MODEL_H + 6 + 11 + rows * 18 + 4 + 11;
                yield new int[]{176, playerY + 3 * 18 + 4 + 18 + 7};
            }
            case LEVELS -> new int[]{220, TITLE + GirlSkill.values().length * SKILL_STEP + 8};
            case COMMANDS -> new int[]{200, TITLE + 4 * BUTTON_STEP + 20};
            case ACTIONS -> new int[]{240, TITLE + 6 * BUTTON_STEP + 8};
        };
    }

    private static int bagRows(@Nullable AbstractGirlEntity girl) {
        int open = girl == null ? 1 : Math.max(1, girl.getBagCapacity());
        return (open + GirlMenu.BAG_COLUMNS - 1) / GirlMenu.BAG_COLUMNS;
    }

    @Override
    protected void init() {
        int[] size = contentSize(tab);
        // the scene list likes room to scroll; the others fit their content
        int prefH = tab == Tab.ACTIONS ? Math.max(size[1], 230) : size[1];
        frame.fit("menu/" + tab.name().toLowerCase(), width, height, size[0], prefH, size[0], tab == Tab.ACTIONS ? 120 : size[1],
                0, TAB_RISE);
        this.imageWidth = frame.w;
        this.imageHeight = frame.h;
        super.init();
        this.leftPos = frame.x;
        this.topPos = frame.y;
        tabWidgets.clear();
        sceneButtons.clear();
        for (Tab t : Tab.values()) tabWidgets.put(t, new ArrayList<>());
        layoutSlots();
        AbstractGirlEntity girl = menu.getGirl();
        if (girl == null) return;
        int x = leftPos + PAD, y = topPos + TITLE, contentW = imageWidth - 2 * PAD;

        followButton = add(Tab.COMMANDS, Button.builder(Component.empty(), b -> toggleFollow(girl))
                .bounds(x, y, contentW, BUTTON_H).build());
        homeButton = add(Tab.COMMANDS, Button.builder(Component.translatable("gui.velvet.go_home"),
                        b -> NetworkHandler.sendToServer(new SendGirlHomePacket(girl.getGirlUUID())))
                .bounds(x, y + BUTTON_STEP, contentW, BUTTON_H).build());
        setHomeButton = add(Tab.COMMANDS, Button.builder(Component.translatable("gui.velvet.set_home"),
                        b -> NetworkHandler.sendToServer(new SetHomePositionPacket(girl.getGirlUUID(), girl.position())))
                .bounds(x, y + 2 * BUTTON_STEP, contentW, BUTTON_H).build());
        outfitButton = add(Tab.COMMANDS, Button.builder(Component.empty(), b -> toggleOutfit(girl))
                .bounds(x, y + 3 * BUTTON_STEP, contentW, BUTTON_H).build());

        if (tab == Tab.ACTIONS) layoutScenes(girl, x, y, contentW);
        showTab(tab);
        refresh(girl);
    }

    /**
     * The inventory tab, centred in the window: armor, her model and the hand slots on top, her stats beside
     * them, her inventory under them and the player's at the bottom.
     */
    private void layoutSlots() {
        int[] content = contentSize(Tab.INVENTORY);
        int ox = (imageWidth - content[0]) / 2, oy = tab == Tab.INVENTORY ? (imageHeight - content[1]) / 2 : 0;
        AbstractGirlEntity girl = menu.getGirl();
        int open = girl == null ? 0 : girl.getBagCapacity();
        int armorX = ox + 8, top = oy + TITLE;
        modelX = armorX + 18;
        modelY = top;
        int handsX = modelX + MODEL_W + 4;
        statsX = handsX + 22;
        statsY = top + 2;
        int bagY = top + MODEL_H + 6 + 11;
        bagLabelY = bagY - 11;
        int playerY = bagY + bagRows(girl) * 18 + 4 + 11;
        playerLabelX = ox + 8;
        playerLabelY = playerY - 11;
        int slot = 0;
        for (Slot s : menu.slots) {
            int x, y;
            if (slot < GirlMenu.ARMOR.length) {
                x = armorX;
                y = top + slot * 18;
            } else if (slot < GirlMenu.ARMOR.length + GirlMenu.HANDS.length) {
                x = handsX;
                y = top + 36 + (slot - GirlMenu.ARMOR.length) * 18;
            } else if (slot < GirlMenu.ARMOR.length + GirlMenu.HANDS.length + AbstractGirlEntity.BAG_SIZE) {
                int i = slot - GirlMenu.ARMOR.length - GirlMenu.HANDS.length;
                int row = i / GirlMenu.BAG_COLUMNS;
                int inRow = Math.clamp(open - row * GirlMenu.BAG_COLUMNS, 1, GirlMenu.BAG_COLUMNS);
                x = ox + (content[0] - inRow * 18) / 2 + 1 + i % GirlMenu.BAG_COLUMNS * 18;
                y = bagY + row * 18;
            } else {
                int i = slot - GirlMenu.ARMOR.length - GirlMenu.HANDS.length - AbstractGirlEntity.BAG_SIZE;
                x = ox + 8 + i % 9 * 18;
                y = i < 27 ? playerY + i / 9 * 18 : playerY + 3 * 18 + 4;
            }
            ((SlotAccessor) s).velvet$setX(x);
            ((SlotAccessor) s).velvet$setY(y);
            slot++;
        }
    }

    /** The scenes that fit, under a header for each style; the wheel scrolls through the rest. */
    private void layoutScenes(AbstractGirlEntity girl, int x, int y, int w) {
        List<Object> rows = sceneRows(girl);
        int listH = topPos + imageHeight - PAD - y;
        int total = 0;
        for (Object r : rows) total += r instanceof GirlType ? HEADER_H : BUTTON_STEP;
        sceneScroll = Mth.clamp(sceneScroll, 0, Math.max(0, total - listH));
        int ry = y - sceneScroll;
        for (Object r : rows) {
            int rh = r instanceof GirlType ? HEADER_H : BUTTON_STEP;
            if (r instanceof SceneType type && ry >= y && ry + BUTTON_H <= y + listH) {
                Button b = add(Tab.ACTIONS, new ServiceButton(x, ry, w - 6, BUTTON_H, type, btn -> startScene(girl, type)));
                sceneButtons.add(b);
            }
            ry += rh;
        }
    }

    /** The scenes, in menu order: one plain list. */
    private static List<Object> sceneRows(AbstractGirlEntity girl) {
        return new ArrayList<>(SceneType.of(girl.getGirlType()));
    }

    private <W extends AbstractWidget> W add(Tab owner, W widget) {
        tabWidgets.get(owner).add(widget);
        return addRenderableWidget(widget);
    }

    private void showTab(Tab open) {
        tabWidgets.forEach((t, widgets) -> widgets.forEach(w -> w.visible = t == open));
        menu.setSlotsVisible(open == Tab.INVENTORY);
    }

    private void selectTab(Tab selected) {
        tab = selected;
        lastTab = selected;
        // each tab has its own size: lay it out again
        rebuildWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        AbstractGirlEntity girl = menu.getGirl();
        if (girl == null) {
            onClose();
            return;
        }
        refresh(girl);
    }

    /** Updates toggle labels and availability from the girl's synced state. */
    private void refresh(AbstractGirlEntity girl) {
        followButton.setMessage(Component.translatable(girl.isFollowing() ? "gui.velvet.stop_following" : "gui.velvet.follow_me"));
        boolean dressed = girl.getModelVariant() != AbstractGirlEntity.VARIANT_NUDE;
        outfitButton.setMessage(Component.translatable(dressed ? "gui.velvet.undress" : "gui.velvet.dress_up"));

        boolean free = !girl.getCurrentAction().isBusy();
        followButton.active = free;
        outfitButton.active = free;
        homeButton.active = free;
        setHomeButton.active = free;
        sceneButtons.forEach(b -> b.active = free);
    }

    private void toggleFollow(AbstractGirlEntity girl) {
        NetworkHandler.sendToServer(new SetFollowingPacket(girl.getGirlUUID(), !girl.isFollowing()));
    }

    private void toggleOutfit(AbstractGirlEntity girl) {
        boolean dressed = girl.getModelVariant() != AbstractGirlEntity.VARIANT_NUDE;
        NetworkHandler.sendToServer(new SetOutfitPacket(girl.getGirlUUID(),
                dressed ? AbstractGirlEntity.VARIANT_NUDE : AbstractGirlEntity.VARIANT_DRESSED));
        onClose(); // let the player watch the animation
    }

    private void startScene(AbstractGirlEntity girl, SceneType type) {
        NetworkHandler.sendToServer(new StartScenePacket(girl.getGirlUUID(), type));
        onClose();
    }

    // ---- Input ----

    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        int i = PanelArt.tabAt(mouseX, mouseY, leftPos, topPos, Tab.values().length);
        return i < 0 ? null : Tab.values()[i];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (frame.mouseClicked(mouseX, mouseY, button)) return true;
        Tab clicked = button == 0 ? tabAt(mouseX, mouseY) : null;
        if (clicked != null) {
            if (clicked != tab) {
                selectTab(clicked);
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (frame.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (frame.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.ACTIONS && isHovering(0, 0, imageWidth, imageHeight, mouseX, mouseY)) {
            sceneScroll -= (int) Math.signum(scrollY) * BUTTON_STEP;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // clicks on the tabs (and the edges being dragged) are not drops outside the window
        return tabAt(mouseX, mouseY) == null && !frame.isDragging() && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    @Override
    public void removed() {
        super.removed();
        WindowFrame.resetCursor();
    }

    private void drawTab(GuiGraphics g, Tab t) {
        int[] icon = PanelArt.drawTab(g, tabX(leftPos, t.ordinal()), topPos, t == tab);
        if (t.icon != null) {
            g.renderItem(t.icon, icon[0], icon[1]);
        } else {
            // the 9 pixel HUD heart at its own size, centred where an item icon would be
            StatusIcons.heart(g, icon[0] + 4, icon[1] + 4);
        }
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        frame.renderGrip(g, mouseX, mouseY);
        Tab hovered = tabAt(mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(font, hovered.title(), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
            if (tab == Tab.LEVELS) renderSkillTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // the closed tabs go behind the panel, the open one in front of its edge
        for (Tab t : Tab.values()) if (t != tab) drawTab(g, t);
        drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        drawTab(g, tab);
        joinOpenTab(g, tabX(leftPos, tab.ordinal()), topPos);
        if (tab != Tab.INVENTORY) return;

        for (Slot slot : menu.slots) {
            if (slot.isActive()) drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        int mx = leftPos + modelX, my = topPos + modelY;
        drawInset(g, mx, my, MODEL_W, MODEL_H, MODEL_BACKGROUND);
        AbstractGirlEntity girl = menu.getGirl();
        if (girl != null) {
            AbstractGirlRenderer.renderingInGui = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, mx, my, mx + MODEL_W, my + MODEL_H,
                        30, 0.0625F, mouseX, mouseY, girl);
            } finally {
                AbstractGirlRenderer.renderingInGui = false;
            }
            drawStats(g, girl, leftPos + statsX, topPos + statsY);
        }
    }

    /** Health, food and armor, one under the other beside her model, with the player's HUD icons. */
    private void drawStats(GuiGraphics g, AbstractGirlEntity girl, int x, int y) {
        String health = Math.round(girl.getHealth()) + "/" + Math.round(girl.getMaxHealth());
        String food = girl.getFood() + "/" + GirlNeeds.MAX_FOOD;
        String armor = String.valueOf(girl.getArmorValue());
        StatusIcons.heart(g, x, y);
        g.drawString(font, health, x + StatusIcons.ICON + 2, y + 1, LABEL, false);
        StatusIcons.food(g, x, y + 12);
        g.drawString(font, food, x + StatusIcons.ICON + 2, y + 13, girl.getFood() <= 6 ? 0xFFAA2222 : LABEL, false);
        StatusIcons.armor(g, x, y + 24);
        g.drawString(font, armor, x + StatusIcons.ICON + 2, y + 25, LABEL, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - 2 * PAD - 40), PAD, 6, LABEL, false);
        AbstractGirlEntity girl = menu.getGirl();
        // the inventory tab has its own labels
        Component right = girl != null && girl.getCurrentAction().isBusy()
                ? Component.translatable("gui.velvet.busy") : tab == Tab.INVENTORY ? Component.empty() : tab.title();
        g.drawString(font, right, imageWidth - PAD - font.width(right), 6, LABEL, false);
        if (girl == null) return;
        int contentW = imageWidth - 2 * PAD;

        switch (tab) {
            case INVENTORY -> {
                Component bag = Component.translatable("gui.velvet.girl_inventory");
                g.drawString(font, bag, playerLabelX, bagLabelY, LABEL, false);
                g.drawString(font, Component.translatable("gui.velvet.your_inventory"), playerLabelX, playerLabelY, LABEL, false);
            }
            case LEVELS -> {
                int y = TITLE;
                for (GirlSkill skill : GirlSkill.values()) {
                    renderSkill(g, girl, skill, PAD, y, contentW);
                    y += SKILL_STEP;
                }
            }
            case COMMANDS -> {
                int ly = TITLE + 4 * BUTTON_STEP + 4;
                for (var line : font.split(Component.translatable("gui.velvet.more_commands_soon"), contentW)) {
                    g.drawString(font, line, PAD, ly, MUTED, false);
                    ly += 10;
                }
            }
            case ACTIONS -> renderSceneHeaders(g, girl, contentW);
        }
    }

    /** How far the scene list goes: a thin bar on the right. */
    private void renderSceneHeaders(GuiGraphics g, AbstractGirlEntity girl, int contentW) {
        int bottom = imageHeight - PAD;
        int total = sceneRows(girl).size() * BUTTON_STEP, view = bottom - TITLE;
        if (total > view) {
            int barX = imageWidth - PAD - 3, barH = Math.max(10, view * view / total);
            int barY = TITLE + Math.round((view - barH) * (sceneScroll / (float) (total - view)));
            g.fill(barX, TITLE, barX + 3, bottom, 0xFF8B8B8B);
            g.fill(barX, barY, barX + 3, barY + barH, 0xFFE0E0E0);
        }
    }

    /** Skill name and level, and a bar with the progress towards the next level; it starts over with each level. */
    private void renderSkill(GuiGraphics g, AbstractGirlEntity girl, GirlSkill skill, int x, int y, int w) {
        int level = girl.getSkillLevel(skill);
        int textColor = skill.grows ? LABEL : 0xFF7A7A7A;
        g.drawString(font, Component.translatable(skill.translationKey()), x, y, textColor, false);
        Component lvl = Component.translatable("gui.velvet.level", level);
        g.drawString(font, lvl, x + w - font.width(lvl), y, textColor, false);

        int barY = y + 10;
        drawInset(g, x, barY, w, 3, 0xFF555555);
        g.fill(x, barY, x + Math.round(w * progress(girl, skill)), barY + 3, skill.color);
    }

    /** 0..1 towards the next level; full at the last one. */
    private static float progress(AbstractGirlEntity girl, GirlSkill skill) {
        int level = girl.getSkillLevel(skill);
        if (level >= GirlSkill.MAX_LEVEL) return 1;
        int from = GirlSkill.xpForLevel(level), to = GirlSkill.xpForLevel(level + 1);
        return (girl.getSkillXp(skill) - from) / (float) (to - from);
    }

    /** What a skill does and how far it is, for the row under the mouse. */
    private void renderSkillTooltip(GuiGraphics g, int mouseX, int mouseY) {
        AbstractGirlEntity girl = menu.getGirl();
        int row = (mouseY - topPos - TITLE) / SKILL_STEP;
        int localX = mouseX - leftPos - PAD;
        if (girl == null || mouseY < topPos + TITLE || localX < 0 || localX >= imageWidth - 2 * PAD
                || row >= GirlSkill.values().length) return;
        GirlSkill skill = GirlSkill.values()[row];
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(skill.translationKey()));
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));
        int level = girl.getSkillLevel(skill);
        if (level >= GirlSkill.MAX_LEVEL) {
            lines.add(Component.translatable("gui.velvet.max_level").withStyle(ChatFormatting.GOLD));
        } else {
            int from = GirlSkill.xpForLevel(level);
            lines.add(Component.translatable("gui.velvet.xp", girl.getSkillXp(skill) - from,
                    GirlSkill.xpForLevel(level + 1) - from).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!skill.grows) {
            lines.add(Component.translatable("gui.velvet.skill_not_growing").withStyle(ChatFormatting.DARK_GRAY));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Service button: name on the left, price (item and count, or "free" in creative) on the right. */
    private class ServiceButton extends Button {
        private final SceneType type;

        ServiceButton(int x, int y, int w, int h, SceneType type, OnPress onPress) {
            super(x, y, w, h, Component.translatable(type.translationKey()), onPress, DEFAULT_NARRATION);
            this.type = type;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Component label = getMessage();
            setMessage(Component.empty()); // vanilla draws the background only; the text goes left-aligned below
            super.renderWidget(g, mouseX, mouseY, partialTick);
            setMessage(label);

            int color = active ? 0xFFFFFFFF : 0xFFA0A0A0;
            int textY = getY() + (getHeight() - 8) / 2;
            int right = getX() + getWidth() - 3;
            boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
            int priceW;
            if (creative) {
                Component free = Component.translatable("gui.velvet.free");
                priceW = font.width(free) + 2;
                g.drawString(font, free, right - font.width(free) - 1, textY, 0xFF55FF55, true);
            } else {
                ItemStack cost = type.cost();
                String count = String.valueOf(cost.getCount());
                priceW = 18 + font.width(count);
                g.renderItem(cost, right - 16, getY() + 2);
                g.drawString(font, count, right - 17 - font.width(count), textY, color, true);
            }
            String text = font.plainSubstrByWidth(label.getString(), getWidth() - 10 - priceW);
            g.drawString(font, text, getX() + 5, textY, color, true);
            if (menu.getGirl() != null && type.needsBed(menu.getGirl().getGirlType())) g.drawString(font, "⌂", right - priceW - 9, textY, 0xFFB0C8FF, true);
        }
    }
}
