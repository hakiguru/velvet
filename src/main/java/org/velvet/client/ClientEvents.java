package org.velvet.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlAction;
import org.velvet.entity.girl.GirlManager;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.OpenGirlMenuPacket;
import org.velvet.network.packet.SceneControlPacket;

/**
 * Keys, and what the local player sees as a scene partner. While attached to a scene the partner rides
 * the girl, so vanilla already blocks walking and sneak dismounts (which ends the scene server-side).
 */
@EventBusSubscriber(modid = Velvet.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Skins and custom presets of one world aren't the next one's. */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        org.velvet.client.skin.SkinCache.clear();
        org.velvet.client.preset.PresetCache.clear();
    }

    /** The speed key state the server last heard about. */
    private static boolean sentFast;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SceneCamera.tick(mc);
        AbstractGirlEntity scenePartner = GirlManager.partnerOf(mc.player);

        while (KeyMappings.interactWithGirl.consumeClick()) {
            if (scenePartner == null && mc.crosshairPickEntity instanceof AbstractGirlEntity girl
                    && !girl.getCurrentAction().isBusy()) {
                NetworkHandler.sendToServer(new OpenGirlMenuPacket(girl.getId()));
            }
        }
        while (KeyMappings.sceneCamera.consumeClick()) {
            SceneCamera.next();
        }

        boolean fast = scenePartner != null && mc.screen == null && KeyMappings.isHeld(KeyMappings.sceneFaster);
        if (fast != sentFast) {
            if (scenePartner != null) {
                NetworkHandler.sendToServer(new SceneControlPacket(fast
                        ? SceneControlPacket.Control.FAST_ON : SceneControlPacket.Control.FAST_OFF));
            }
            sentFast = fast; // the server forgets it by itself when the scene ends
        }
    }

    // ---- Partner rendering ----

    /**
     * Attached partners aren't drawn at all: their body is the girl's "steve" bone (with their skin and
     * their name, see PartnerSkinLayer and AbstractGirlRenderer), and they look out of its head.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (GirlManager.attachedTo(event.getEntity()) != null) event.setCanceled(true);
    }

    /** The body is the model's, so no first-person arm either. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && GirlManager.attachedTo(mc.player) != null) event.setCanceled(true);
    }

    // ---- HUD ----

    /** Bottom-left scene panel, kept off the very corner. */
    private static final int PANEL_X = 12;
    private static final int PANEL_BOTTOM_MARGIN = 28;
    private static final int LINE = 12;
    /** 128x64 sheet: camera icon 16x16 at (0,0), bar frame 104x10 at (0,16), fill 100x6 at (0,32), full fill at (0,40). */
    private static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "textures/gui/scene_hud.png");
    private static final int BAR_FRAME_WIDTH = 104;
    private static final int BAR_FRAME_HEIGHT = 10;
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 6;
    /** The bar grows in steps (one per cycle); drawn smoothly. */
    private static float shownProgress;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        AbstractGirlEntity girl = GirlManager.partnerOf(mc.player);
        if (girl == null) return;
        GuiGraphics g = event.getGuiGraphics();
        GirlAction action = girl.getCurrentAction();

        if (!action.isAttached()) {
            // bed scenes: following her to the bed, then stepping onto the spot she climbed up from
            Component hint = Component.translatable(action.bed() == GirlAction.Bed.WALK
                    ? "gui.velvet.scene_follow" : "gui.velvet.scene_step_behind", girl.getName());
            g.drawCenteredString(mc.font, hint, g.guiWidth() / 2, g.guiHeight() - 72, 0xFFFFFFFF);
            return;
        }
        drawScenePanel(g, mc.font, girl, action);
    }

    private static void drawScenePanel(GuiGraphics g, Font font, AbstractGirlEntity girl, GirlAction action) {
        int x = PANEL_X;
        int y = g.guiHeight() - PANEL_BOTTOM_MARGIN;
        Minecraft mc = Minecraft.getInstance();

        // bottom up: stop hint, speed hint, camera button, progress bar
        g.drawString(font, Component.translatable("gui.velvet.scene_stop", key(mc.options.keyShift.getTranslatedKeyMessage())),
                x, y, 0xFFB0B0B0, true);

        y -= LINE;
        boolean held = KeyMappings.isHeld(KeyMappings.sceneFaster);
        int speedColor = !action.isPaced() ? 0xFF707070 : held ? 0xFFFFD45E : 0xFFFFFFFF;
        g.drawString(font, Component.translatable("gui.velvet.scene_faster", key(KeyMappings.sceneFaster.getTranslatedKeyMessage())),
                x, y, speedColor, true);

        y -= LINE + 8;
        Component camera = Component.translatable("gui.velvet.camera",
                key(KeyMappings.sceneCamera.getTranslatedKeyMessage()), SceneCamera.view().label());
        int buttonWidth = 16 + 5 + font.width(camera);
        g.fill(x - 3, y - 3, x + buttonWidth + 3, y + 17, 0x90000000);
        g.renderOutline(x - 3, y - 3, buttonWidth + 6, 20, 0x60FFFFFF);
        g.blit(HUD, x, y, 0, 0, 16, 16, 128, 64);
        g.drawString(font, camera, x + 21, y + 4, 0xFFFFFFFF, true);

        y -= 3 + 6 + BAR_FRAME_HEIGHT;
        drawProgressBar(g, font, x - 2, y, girl.getSceneProgress());
    }

    private static Component key(Component keyName) {
        return Component.literal("[").append(keyName).append("]").withStyle(ChatFormatting.YELLOW);
    }

    private static void drawProgressBar(GuiGraphics g, Font font, int x, int y, float progress) {
        shownProgress = progress < shownProgress ? progress : Mth.lerp(0.1F, shownProgress, progress);
        g.blit(HUD, x, y, 0, 16, BAR_FRAME_WIDTH, BAR_FRAME_HEIGHT, 128, 64);
        int filled = Math.round(Mth.clamp(shownProgress, 0, 1) * BAR_WIDTH);
        int fillV = progress >= 1 ? 40 : 32;
        if (filled > 0) g.blit(HUD, x + 2, y + 2, 0, fillV, filled, BAR_HEIGHT, 128, 64);
        g.drawString(font, Math.round(progress * 100) + "%", x + BAR_FRAME_WIDTH + 5, y + 1, 0xFFFFFFFF, true);
    }
}
