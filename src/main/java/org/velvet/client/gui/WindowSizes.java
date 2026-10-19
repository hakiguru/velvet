package org.velvet.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.VelvetPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The size each window (and each tab of it) was last given, kept in {@code config/velvet/windows.json}. In
 * screen pixels, so a window stays as big on the screen when the GUI scale changes.
 */
public final class WindowSizes {

    private WindowSizes() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, int[]> sizes;

    private static Path file() {
        return VelvetPaths.root().resolve("windows.json");
    }

    private static Map<String, int[]> sizes() {
        if (sizes != null) return sizes;
        sizes = new HashMap<>();
        Path file = file();
        if (Files.isRegularFile(file)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    JsonArray a = e.getValue().getAsJsonArray();
                    sizes.put(e.getKey(), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
                }
            } catch (IOException | RuntimeException e) {
                Velvet.LOGGER.warn("Couldn't read {}: {}", file, e.toString());
            }
        }
        return sizes;
    }

    /** Width and height in screen pixels, or null if it was never resized. */
    @Nullable
    public static int[] get(String key) {
        return sizes().get(key);
    }

    public static void put(String key, int screenW, int screenH) {
        sizes().put(key, new int[]{screenW, screenH});
        save();
    }

    /** Back to the window's own size. */
    public static void forget(String key) {
        if (sizes().remove(key) != null) save();
    }

    private static void save() {
        JsonObject json = new JsonObject();
        sizes().forEach((k, v) -> {
            JsonArray a = new JsonArray();
            a.add(v[0]);
            a.add(v[1]);
            json.add(k, a);
        });
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't write {}: {}", file(), e.toString());
        }
    }
}
