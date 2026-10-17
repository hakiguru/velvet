package org.velvetlite.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.velvetlite.client.render.AbstractGirlRenderer;
import org.velvetlite.entity.girl.AbstractGirlEntity;
import org.velvetlite.entity.girl.GirlSkill;
import org.velvetlite.entity.girl.scene.SceneType;
import org.velvetlite.menu.GirlMenu;
import org.velvetlite.network.NetworkHandler;
import org.velvetlite.network.packet.SendGirlHomePacket;
import org.velvetlite.network.packet.SetGirlOwnerPacket;
import org.velvetlite.network.packet.SetHomePositionPacket;
import org.velvetlite.network.packet.SetOutfitPacket;
import org.velvetlite.network.packet.StartScenePacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The girl's menu, split into tabs along the top edge:
 * <ul>
 *   <li>Inventory: her model with armor and hand slots, her bag, health, and the player's inventory.</li>
 *   <li>Levels: every skill with its level and progress.</li>
 *   <li>Commands: follow, home, outfit. Toggle buttons always show the action that applies right now.</li>
 *   <li>Scenes: the paid services.</li>
 * </ul>
 */
public class GirlScreen extends AbstractContainerScreen<GirlMenu> {

    public enum Tab {
        INVENTORY(new ItemStack(Items.CHEST)),
        LEVELS(new ItemStack(Items.EXPERIENCE_BOTTLE)),
        COMMANDS(new ItemStack(Items.BELL)),
        SCENES(new ItemStack(Items.PINK_TULIP));

        final ItemStack icon;

        Tab(ItemStack icon) {
            this.icon = icon;
        }

        Component title() {
            return Component.translatable("gui.velvet_lite.tab." + name().toLowerCase());
        }
    }

    /** Remembered while the game runs, so the menu opens on the tab used last. */
    private static Tab lastTab = Tab.INVENTORY;

    private static final int MODEL_X = 26, MODEL_Y = 18, MODEL_W = 50, MODEL_H = 72;
    private static final int TAB_W = 28, TAB_H = 24, TAB_GAP = 1;
    /** How far the tabs stick out above the panel. */
    private static final int TAB_RISE = 21;
    private static final int CONTENT_X = 8, CONTENT_Y = 18, CONTENT_W = 160;
    private static final int BUTTON_H = 20, BUTTON_STEP = 22;
    private static final int SKILL_STEP = 16;

    // vanilla container colours
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int OUTLINE = 0xFF000000;
    private static final int LABEL = 0xFF404040;
    /** Behind the model: dark enough to frame her, light enough not to swallow dark clothes. */
    private static final int MODEL_BACKGROUND = 0xFF6B6B6B;

    private Tab tab = lastTab;
    /** Widgets of each tab, shown only while it's open. */
    private final Map<Tab, List<AbstractWidget>> tabWidgets = new EnumMap<>(Tab.class);
    private final Map<SceneType, Button> sceneButtons = new EnumMap<>(SceneType.class);
    private Button followButton;
    private Button outfitButton;
    private Button homeButton;
    private Button setHomeButton;

    public GirlScreen(GirlMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.inventoryLabelY = GirlMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        // room for the tabs above the panel
        this.topPos += TAB_RISE / 2;
        tabWidgets.clear();
        for (Tab t : Tab.values()) tabWidgets.put(t, new ArrayList<>());
        AbstractGirlEntity girl = menu.getGirl();
        if (girl == null) return;
        int x = leftPos + CONTENT_X;
        int y = topPos + CONTENT_Y;

        followButton = add(Tab.COMMANDS, Button.builder(Component.empty(), b -> toggleFollow(girl))
                .bounds(x, y, CONTENT_W, BUTTON_H).build());
        homeButton = add(Tab.COMMANDS, Button.builder(Component.translatable("gui.velvet_lite.go_home"),
                        b -> NetworkHandler.sendToServer(new SendGirlHomePacket(girl.getGirlUUID())))
                .bounds(x, y + BUTTON_STEP, CONTENT_W, BUTTON_H).build());
        setHomeButton = add(Tab.COMMANDS, Button.builder(Component.translatable("gui.velvet_lite.set_home"),
                        b -> NetworkHandler.sendToServer(new SetHomePositionPacket(girl.getGirlUUID(), girl.position())))
                .bounds(x, y + 2 * BUTTON_STEP, CONTENT_W, BUTTON_H).build());
        outfitButton = add(Tab.COMMANDS, Button.builder(Component.empty(), b -> toggleOutfit(girl))
                .bounds(x, y + 3 * BUTTON_STEP, CONTENT_W, BUTTON_H).build());

        for (SceneType type : SceneType.values()) {
            sceneButtons.put(type, add(Tab.SCENES, new ServiceButton(x, y + type.ordinal() * BUTTON_STEP,
                    CONTENT_W, BUTTON_H, type, b -> startScene(girl, type))));
        }
        selectTab(tab);
        refresh(girl);
    }

    private <W extends AbstractWidget> W add(Tab owner, W widget) {
        tabWidgets.get(owner).add(widget);
        return addRenderableWidget(widget);
    }

    private void selectTab(Tab selected) {
        tab = selected;
        lastTab = selected;
        tabWidgets.forEach((t, widgets) -> widgets.forEach(w -> w.visible = t == selected));
        menu.setSlotsVisible(selected == Tab.INVENTORY);
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
        boolean following = minecraft != null && minecraft.player != null
                && minecraft.player.getUUID().equals(girl.getOwnerUUID());
        followButton.setMessage(Component.translatable(following ? "gui.velvet_lite.stop_following" : "gui.velvet_lite.follow_me"));
        boolean dressed = girl.getModelVariant() != AbstractGirlEntity.VARIANT_NUDE;
        outfitButton.setMessage(Component.translatable(dressed ? "gui.velvet_lite.undress" : "gui.velvet_lite.dress_up"));

        boolean free = !girl.getCurrentAction().isBusy();
        followButton.active = free;
        outfitButton.active = free;
        homeButton.active = free;
        setHomeButton.active = free;
        sceneButtons.values().forEach(b -> b.active = free);
    }

    private void toggleFollow(AbstractGirlEntity girl) {
        if (minecraft == null || minecraft.player == null) return;
        boolean following = minecraft.player.getUUID().equals(girl.getOwnerUUID());
        NetworkHandler.sendToServer(new SetGirlOwnerPacket(girl.getGirlUUID(),
                following ? SetGirlOwnerPacket.NO_OWNER : minecraft.player.getUUID()));
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

    // ---- Tabs ----

    private int tabX(Tab t) {
        return leftPos + t.ordinal() * (TAB_W + TAB_GAP);
    }

    private int tabY() {
        return topPos - TAB_RISE;
    }

    private Tab tabAt(double mouseX, double mouseY) {
        if (mouseY < tabY() || mouseY >= topPos) return null;
        for (Tab t : Tab.values()) {
            if (mouseX >= tabX(t) && mouseX < tabX(t) + TAB_W) return t;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // clicks on the tabs are not drops outside the window
        return tabAt(mouseX, mouseY) == null && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    /** A tab: the open one joins the panel, the others sit behind it a little darker. */
    private void drawTab(GuiGraphics g, Tab t) {
        boolean open = t == tab;
        int x = tabX(t), y = tabY() + (open ? 0 : 3);
        int bottom = topPos + (open ? 3 : 0);
        int fill = open ? PANEL : 0xFFA8A8A8;
        g.fill(x + 1, y, x + TAB_W - 1, bottom, OUTLINE);
        g.fill(x, y + 1, x + TAB_W, bottom, OUTLINE);
        g.fill(x + 1, y + 1, x + TAB_W - 1, bottom, fill);
        g.fill(x + 1, y + 1, x + TAB_W - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, bottom, open ? LIGHT : fill);
        g.fill(x + TAB_W - 3, y + 2, x + TAB_W - 1, bottom, open ? SHADOW : fill);
        g.renderItem(t.icon, x + (TAB_W - 16) / 2, y + (TAB_H - 16) / 2 - (open ? 0 : 1));
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
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
        // over the open tab's bottom outline, so it joins the panel
        g.fill(tabX(tab) + 3, topPos, tabX(tab) + TAB_W - 3, topPos + 3, PANEL);
        if (tab != Tab.INVENTORY) return;

        for (Slot slot : menu.slots) {
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        int mx = leftPos + MODEL_X, my = topPos + MODEL_Y;
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
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, LABEL, false);
        AbstractGirlEntity girl = menu.getGirl();
        // the inventory tab already says "Inventory" above the player's slots
        Component right = girl != null && girl.getCurrentAction().isBusy()
                ? Component.translatable("gui.velvet_lite.busy") : tab == Tab.INVENTORY ? Component.empty() : tab.title();
        g.drawString(font, right, imageWidth - 8 - font.width(right), 6, LABEL, false);
        if (girl == null) return;

        switch (tab) {
            case INVENTORY -> {
                g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
                int y = GirlMenu.BAG_Y + 3 * 18 + 4;
                Component health = Component.literal("❤ " + Math.round(girl.getHealth()) + " / " + Math.round(girl.getMaxHealth()));
                g.drawString(font, health, GirlMenu.BAG_X, y, 0xFFAA2222, false);
                Component armor = Component.literal("⛨ " + girl.getArmorValue());
                g.drawString(font, armor, GirlMenu.BAG_X, y + 10, LABEL, false);
            }
            case LEVELS -> {
                int y = CONTENT_Y;
                for (GirlSkill skill : GirlSkill.values()) {
                    renderSkill(g, girl, skill, CONTENT_X, y);
                    y += SKILL_STEP;
                }
            }
            case COMMANDS -> g.drawString(font, Component.translatable("gui.velvet_lite.more_commands_soon"),
                    CONTENT_X, CONTENT_Y + 4 * BUTTON_STEP + 4, 0xFF707070, false);
            case SCENES -> {}
        }
    }

    /** Skill name and level, and a bar with the progress towards the next level. */
    private void renderSkill(GuiGraphics g, AbstractGirlEntity girl, GirlSkill skill, int x, int y) {
        int level = girl.getSkillLevel(skill);
        int textColor = skill.grows ? LABEL : 0xFF7A7A7A;
        g.drawString(font, Component.translatable(skill.translationKey()), x, y, textColor, false);
        Component lvl = Component.translatable("gui.velvet_lite.level", level);
        g.drawString(font, lvl, x + CONTENT_W - font.width(lvl), y, textColor, false);

        float progress = 1F;
        if (level < GirlSkill.MAX_LEVEL) {
            int from = GirlSkill.xpForLevel(level), to = GirlSkill.xpForLevel(level + 1);
            progress = (girl.getSkillXp(skill) - from) / (float) (to - from);
        }
        int barY = y + 10;
        drawInset(g, x, barY, CONTENT_W, 3, 0xFF555555);
        g.fill(x, barY, x + Math.round(CONTENT_W * progress), barY + 3, skill.color);
    }

    /** What a skill does and how it grows, for the row under the mouse. */
    private void renderSkillTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int row = (mouseY - topPos - CONTENT_Y) / SKILL_STEP;
        int localX = mouseX - leftPos - CONTENT_X;
        if (mouseY < topPos + CONTENT_Y || localX < 0 || localX >= CONTENT_W || row >= GirlSkill.values().length) return;
        GirlSkill skill = GirlSkill.values()[row];
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(skill.translationKey()));
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));
        if (!skill.grows) {
            lines.add(Component.translatable("gui.velvet_lite.skill_not_growing").withStyle(ChatFormatting.DARK_GRAY));
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
            g.drawString(font, label, getX() + 5, textY, color, true);

            int right = getX() + getWidth() - 3;
            boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
            if (creative) {
                Component free = Component.translatable("gui.velvet_lite.free");
                g.drawString(font, free, right - font.width(free) - 1, textY, 0xFF55FF55, true);
            } else {
                ItemStack cost = type.cost();
                String count = String.valueOf(cost.getCount());
                g.renderItem(cost, right - 16, getY() + 2);
                g.drawString(font, count, right - 17 - font.width(count), textY, color, true);
            }
        }
    }

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, SHADOW);
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        drawInset(g, x + 1, y + 1, 16, 16, SLOT);
    }

    /** Sunken box: dark top-left edge, light bottom-right edge. */
    private static void drawInset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x - 1, y - 1, x + w, y, DARK);
        g.fill(x - 1, y - 1, x, y + h, DARK);
        g.fill(x, y + h, x + w + 1, y + h + 1, LIGHT);
        g.fill(x + w, y, x + w + 1, y + h + 1, LIGHT);
    }
}
