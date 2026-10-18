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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.velvet.client.render.AbstractGirlRenderer;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlNeeds;
import org.velvet.entity.girl.GirlSkill;
import org.velvet.entity.girl.scene.SceneType;
import org.velvet.menu.GirlMenu;
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
 *   <li>Inventory: her model with armor and hand slots, her inventory, health, food and armor, and the
 *       player's inventory.</li>
 *   <li>Levels: every skill with its level and progress towards the next.</li>
 *   <li>Commands: follow, home, outfit. Toggle buttons always show the action that applies right now.</li>
 *   <li>Actions: the paid scenes.</li>
 * </ul>
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

    private static final int MODEL_X = 26, MODEL_Y = 18, MODEL_W = 50, MODEL_H = 72;
    private static final int CONTENT_X = 8, CONTENT_Y = 18;
    private static final int CONTENT_W = GirlMenu.WIDTH - 2 * CONTENT_X;
    private static final int BUTTON_H = 20, BUTTON_STEP = 22;
    private static final int SKILL_STEP = 16;
    private static final int STATS_Y = GirlMenu.BAG_Y + 3 * 18 + 4;
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
        this.imageWidth = GirlMenu.WIDTH;
        this.imageHeight = 196;
        this.inventoryLabelX = GirlMenu.PLAYER_X;
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
        homeButton = add(Tab.COMMANDS, Button.builder(Component.translatable("gui.velvet.go_home"),
                        b -> NetworkHandler.sendToServer(new SendGirlHomePacket(girl.getGirlUUID())))
                .bounds(x, y + BUTTON_STEP, CONTENT_W, BUTTON_H).build());
        setHomeButton = add(Tab.COMMANDS, Button.builder(Component.translatable("gui.velvet.set_home"),
                        b -> NetworkHandler.sendToServer(new SetHomePositionPacket(girl.getGirlUUID(), girl.position())))
                .bounds(x, y + 2 * BUTTON_STEP, CONTENT_W, BUTTON_H).build());
        outfitButton = add(Tab.COMMANDS, Button.builder(Component.empty(), b -> toggleOutfit(girl))
                .bounds(x, y + 3 * BUTTON_STEP, CONTENT_W, BUTTON_H).build());

        // her preset's own scenes
        List<SceneType> scenes = SceneType.of(girl.getGirlType());
        for (int i = 0; i < scenes.size(); i++) {
            SceneType type = scenes.get(i);
            sceneButtons.put(type, add(Tab.ACTIONS, new ServiceButton(x, y + i * BUTTON_STEP,
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
        followButton.setMessage(Component.translatable(girl.isFollowing() ? "gui.velvet.stop_following" : "gui.velvet.follow_me"));
        boolean dressed = girl.getModelVariant() != AbstractGirlEntity.VARIANT_NUDE;
        outfitButton.setMessage(Component.translatable(dressed ? "gui.velvet.undress" : "gui.velvet.dress_up"));

        boolean free = !girl.getCurrentAction().isBusy();
        followButton.active = free;
        outfitButton.active = free;
        homeButton.active = free;
        setHomeButton.active = free;
        sceneButtons.values().forEach(b -> b.active = free);
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

    // ---- Tabs ----

    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        int i = PanelArt.tabAt(mouseX, mouseY, leftPos, topPos, Tab.values().length);
        return i < 0 ? null : Tab.values()[i];
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
            drawStats(g, girl, leftPos + GirlMenu.BAG_X, topPos + STATS_Y);
        }
    }

    /** Health, food and armor in one row under her inventory, with the player's HUD icons. */
    private void drawStats(GuiGraphics g, AbstractGirlEntity girl, int x, int y) {
        String health = Math.round(girl.getHealth()) + "/" + Math.round(girl.getMaxHealth());
        String food = girl.getFood() + "/" + GirlNeeds.MAX_FOOD;
        String armor = String.valueOf(girl.getArmorValue());
        StatusIcons.heart(g, x, y);
        x += StatusIcons.ICON + 2;
        g.drawString(font, health, x, y + 1, LABEL, false);
        x += font.width(health) + 7;
        StatusIcons.food(g, x, y);
        x += StatusIcons.ICON + 2;
        g.drawString(font, food, x, y + 1, girl.getFood() <= 6 ? 0xFFAA2222 : LABEL, false);
        x += font.width(food) + 7;
        StatusIcons.armor(g, x, y);
        g.drawString(font, armor, x + StatusIcons.ICON + 2, y + 1, LABEL, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, LABEL, false);
        AbstractGirlEntity girl = menu.getGirl();
        // the inventory tab has its own labels
        Component right = girl != null && girl.getCurrentAction().isBusy()
                ? Component.translatable("gui.velvet.busy") : tab == Tab.INVENTORY ? Component.empty() : tab.title();
        g.drawString(font, right, imageWidth - 8 - font.width(right), 6, LABEL, false);
        if (girl == null) return;

        switch (tab) {
            case INVENTORY -> {
                g.drawString(font, Component.translatable("gui.velvet.girl_inventory"),
                        GirlMenu.BAG_X, GirlMenu.BAG_Y - 11, LABEL, false);
                g.drawString(font, Component.translatable("gui.velvet.your_inventory"),
                        inventoryLabelX, inventoryLabelY, LABEL, false);
            }
            case LEVELS -> {
                int y = CONTENT_Y;
                for (GirlSkill skill : GirlSkill.values()) {
                    renderSkill(g, girl, skill, CONTENT_X, y);
                    y += SKILL_STEP;
                }
            }
            case COMMANDS -> g.drawString(font, Component.translatable("gui.velvet.more_commands_soon"),
                    CONTENT_X, CONTENT_Y + 4 * BUTTON_STEP + 4, MUTED, false);
            case ACTIONS -> {
                if (sceneButtons.isEmpty()) {
                    int ly = CONTENT_Y;
                    for (var line : font.split(Component.translatable("gui.velvet.no_scenes", girl.getGirlType().displayName()), CONTENT_W)) {
                        g.drawString(font, line, CONTENT_X, ly, MUTED, false);
                        ly += 10;
                    }
                }
            }
        }
    }

    /** Skill name and level, and a bar with the progress towards the next level; it starts over with each level. */
    private void renderSkill(GuiGraphics g, AbstractGirlEntity girl, GirlSkill skill, int x, int y) {
        int level = girl.getSkillLevel(skill);
        int textColor = skill.grows ? LABEL : 0xFF7A7A7A;
        g.drawString(font, Component.translatable(skill.translationKey()), x, y, textColor, false);
        Component lvl = Component.translatable("gui.velvet.level", level);
        g.drawString(font, lvl, x + CONTENT_W - font.width(lvl), y, textColor, false);

        int barY = y + 10;
        drawInset(g, x, barY, CONTENT_W, 3, 0xFF555555);
        g.fill(x, barY, x + Math.round(CONTENT_W * progress(girl, skill)), barY + 3, skill.color);
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
        int row = (mouseY - topPos - CONTENT_Y) / SKILL_STEP;
        int localX = mouseX - leftPos - CONTENT_X;
        if (girl == null || mouseY < topPos + CONTENT_Y || localX < 0 || localX >= CONTENT_W || row >= GirlSkill.values().length) return;
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
            g.drawString(font, label, getX() + 5, textY, color, true);

            int right = getX() + getWidth() - 3;
            boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
            if (creative) {
                Component free = Component.translatable("gui.velvet.free");
                g.drawString(font, free, right - font.width(free) - 1, textY, 0xFF55FF55, true);
            } else {
                ItemStack cost = type.cost();
                String count = String.valueOf(cost.getCount());
                g.renderItem(cost, right - 16, getY() + 2);
                g.drawString(font, count, right - 17 - font.width(count), textY, color, true);
            }
        }
    }
}
