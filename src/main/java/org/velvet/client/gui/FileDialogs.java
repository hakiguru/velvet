package org.velvet.client.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.velvet.Velvet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The system's own "open file" dialog (through LWJGL's tinyfd, which the game ships), off the game thread so the
 * game keeps drawing while it's open. The chosen files come back on the game thread.
 */
public final class FileDialogs {

    private FileDialogs() {}

    private static volatile boolean open;

    /**
     * Lets the player pick files; {@code patterns} like "*.png". Nothing happens if a dialog is already open or
     * the player cancels.
     */
    public static void open(String title, List<String> patterns, String description, boolean multiple, Consumer<List<Path>> then) {
        if (open) return;
        open = true;
        Thread thread = new Thread(() -> {
            String result;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(patterns.size());
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                result = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, description, multiple);
            } catch (Throwable t) {
                Velvet.LOGGER.warn("Couldn't open a file dialog: {}", t.toString());
                result = null;
            } finally {
                open = false;
            }
            if (result == null || result.isBlank()) return;
            List<Path> paths = new ArrayList<>();
            for (String part : result.split("\\|")) if (!part.isBlank()) paths.add(Path.of(part));
            Minecraft.getInstance().execute(() -> then.accept(paths));
        }, "Velvet file dialog");
        thread.setDaemon(true);
        thread.start();
    }
}
