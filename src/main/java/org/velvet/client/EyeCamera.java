package org.velvet.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.mixin.MinecraftAccessor;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.EyesImagePacket;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Draws the world from an NPC's eyes when the server asks (for a language model that sees): after a normal
 * frame, the game renders once more into a target of its own, with her as the camera and no hand, and the
 * picture goes back to the server as a small JPEG. One picture per frame at most; nothing on screen changes.
 */
@EventBusSubscriber(modid = Velvet.MODID, value = Dist.CLIENT)
public final class EyeCamera {

    private EyeCamera() {}

    private static final int WIDTH = 512, HEIGHT = 288;
    private static final float QUALITY = 0.7F;

    private record Request(int id, int entityId) {}

    private static final Deque<Request> REQUESTS = new ArrayDeque<>();
    private static boolean rendering;

    public static void request(int id, int entityId) {
        if (REQUESTS.size() < 4) REQUESTS.add(new Request(id, entityId));
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (REQUESTS.isEmpty() || rendering) return;
        Minecraft mc = Minecraft.getInstance();
        Request r = REQUESTS.poll();
        if (mc.level == null || mc.player == null) return;
        if (!(mc.level.getEntity(r.entityId) instanceof AbstractGirlEntity girl)) return;
        try {
            byte[] jpeg = render(mc, girl, event);
            if (jpeg != null) send(r.id, jpeg);
        } catch (Throwable t) {
            Velvet.LOGGER.warn("Couldn't render what {} sees: {}", girl.getName().getString(), t.toString());
        }
    }

    private static byte[] render(Minecraft mc, AbstractGirlEntity girl, RenderFrameEvent.Post event) throws Exception {
        RenderTarget main = mc.getMainRenderTarget();
        TextureTarget target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        Entity camera = mc.getCameraEntity();
        CameraType type = mc.options.getCameraType();
        var hit = mc.hitResult;
        rendering = true;
        try {
            target.setClearColor(0, 0, 0, 1);
            target.clear(Minecraft.ON_OSX);
            ((MinecraftAccessor) mc).velvet$setMainRenderTarget(target);
            target.bindWrite(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(girl);
            mc.gameRenderer.setRenderHand(false);
            mc.gameRenderer.renderLevel(event.getPartialTick());
        } finally {
            mc.gameRenderer.setRenderHand(true);
            mc.setCameraEntity(camera);
            mc.options.setCameraType(type);
            mc.hitResult = hit;
            ((MinecraftAccessor) mc).velvet$setMainRenderTarget(main);
            main.bindWrite(true);
            rendering = false;
        }
        NativeImage full = Screenshot.takeScreenshot(target);
        target.destroyBuffers();
        try (NativeImage small = new NativeImage(WIDTH, HEIGHT, false)) {
            full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
            full.close();
            return jpeg(small);
        }
    }

    private static byte[] jpeg(NativeImage image) throws Exception {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
                out.setRGB(x, y, r << 16 | g << 8 | b);
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static void send(int id, byte[] jpeg) {
        int total = (jpeg.length + EyesImagePacket.CHUNK - 1) / EyesImagePacket.CHUNK;
        for (int i = 0; i < total; i++) {
            byte[] part = Arrays.copyOfRange(jpeg, i * EyesImagePacket.CHUNK, Math.min(jpeg.length, (i + 1) * EyesImagePacket.CHUNK));
            NetworkHandler.sendToServer(new EyesImagePacket(id, i, total, part));
        }
    }
}
