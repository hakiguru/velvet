package org.velvetlite.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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

import java.util.EnumMap;
import java.util.Map;

/**
 * The girl's menu, laid out like the player inventory: armor column and model on the left, her bag
 * below, commands and paid services on the right, the player's inventory at the bottom. Toggle
 * buttons (follow, outfit) always show the action that applies right now.
 */
public class GirlScreen extends AbstractContainerScreen<GirlMenu> {

    private static final int MODEL_X = 26, MODEL_Y = 18, MODEL_W = 52, MODEL_H = 72;
    private static final int COMMANDS_X = 86, COMMANDS_W = 94;
    private static final int SERVICES_X = 184, SERVICES_W = 88;
    private static final int BUTTON_H = 20;

    // vanilla container colours
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int OUTLINE = 0xFF000000;
    private static final int LABEL = 0xFF404040;

    private final Map<SceneType, Button> sceneButtons = new EnumMap<>(SceneType.class);
    private Button followButton;
    private Button outfitButton;
    private Button homeButton;
    private Button setHomeButton;

    public GirlScreen(GirlMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 280;
        this.imageHeight = 232;
        this.inventoryLabelY = GirlMenu.PLAYER_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        AbstractGirlEntity girl = menu.getGirl();
        if (girl == null) return;
        int x = leftPos + COMMANDS_X;
        int y = topPos + 18;

        followButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleFollow(girl))
                .bounds(x, y, COMMANDS_W, BUTTON_H).build());
        homeButton = addRenderableWidget(Button.builder(Component.translatable("gui.velvet_lite.go_home"),
                        b -> NetworkHandler.sendToServer(new SendGirlHomePacket(girl.getGirlUUID())))
                .bounds(x, y + 22, COMMANDS_W, BUTTON_H).build());
        setHomeButton = addRenderableWidget(Button.builder(Component.translatable("gui.velvet_lite.set_home"),
                        b -> NetworkHandler.sendToServer(new SetHomePositionPacket(girl.getGirlUUID(), girl.position())))
                .bounds(x, y + 44, COMMANDS_W, BUTTON_H).build());
        outfitButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleOutfit(girl))
                .bounds(x, y + 66, COMMANDS_W, BUTTON_H).build());

        for (SceneType type : SceneType.values()) {
            sceneButtons.put(type, addRenderableWidget(new ServiceButton(leftPos + SERVICES_X, y + type.ordinal() * 22,
                    SERVICES_W, BUTTON_H, type, b -> startScene(girl, type))));
        }
        refresh(girl);
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

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawPanel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            drawSlot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        int mx = leftPos + MODEL_X, my = topPos + MODEL_Y;
        drawInset(g, mx, my, MODEL_W, MODEL_H, OUTLINE);
        AbstractGirlEntity girl = menu.getGirl();
        if (girl != null) {
            AbstractGirlRenderer.renderingInGui = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, mx, my, mx + MODEL_W, my + MODEL_H,
                        26, 0.0625F, mouseX, mouseY, girl);
            } finally {
                AbstractGirlRenderer.renderingInGui = false;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, LABEL, false);
        g.drawString(font, Component.translatable("gui.velvet_lite.bag"), GirlMenu.BAG_X, GirlMenu.BAG_Y - 11, LABEL, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        g.drawString(font, Component.translatable("gui.velvet_lite.commands"), COMMANDS_X, 6, LABEL, false);
        g.drawString(font, Component.translatable("gui.velvet_lite.services"), SERVICES_X, 6, LABEL, false);

        AbstractGirlEntity girl = menu.getGirl();
        if (girl == null) return;
        int y = 18 + 3 * 22 + 4;
        Component health = Component.literal("❤ " + Math.round(girl.getHealth()) + " / " + Math.round(girl.getMaxHealth()));
        g.drawString(font, health, SERVICES_X, y, 0xFFAA2222, false);
        Component armor = Component.literal("⛨ " + girl.getArmorValue());
        g.drawString(font, armor, SERVICES_X + SERVICES_W - font.width(armor), y, LABEL, false);
        if (girl.getCurrentAction().isBusy()) {
            Component busy = Component.translatable("gui.velvet_lite.busy");
            g.drawString(font, busy, imageWidth - 8 - font.width(busy), 6, LABEL, false);
        }

        y += 14;
        for (GirlSkill skill : GirlSkill.values()) {
            renderSkill(g, girl, skill, SERVICES_X, y);
            y += 20;
        }
    }

    /** Skill name and level, and a bar with the progress towards the next level. */
    private void renderSkill(GuiGraphics g, AbstractGirlEntity girl, GirlSkill skill, int x, int y) {
        int level = girl.getSkillLevel(skill);
        g.drawString(font, Component.translatable(skill.translationKey()), x, y, LABEL, false);
        Component lvl = Component.translatable("gui.velvet_lite.level", level);
        g.drawString(font, lvl, x + SERVICES_W - font.width(lvl), y, LABEL, false);

        float progress = 1F;
        if (level < GirlSkill.MAX_LEVEL) {
            int from = GirlSkill.xpForLevel(level), to = GirlSkill.xpForLevel(level + 1);
            progress = (girl.getSkillXp(skill) - from) / (float) (to - from);
        }
        int barY = y + 10;
        drawInset(g, x, barY, SERVICES_W, 4, 0xFF555555);
        g.fill(x, barY, x + Math.round(SERVICES_W * progress), barY + 4, skill.color);
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
