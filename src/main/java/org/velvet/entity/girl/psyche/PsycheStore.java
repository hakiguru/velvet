package org.velvet.entity.girl.psyche;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.VelvetPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Each NPC's personality file, {@code config/velvet/npcs/personalities/<her id>.json}: her character (every
 * trait, 0.01 to 1.00), how she felt when she was last saved (emotions in tenths, adrenaline) and what's
 * still weighing on her. Written when she's saved; read when she's loaded, so it can be edited by hand.
 * <pre>
 * {
 *   "traits": { "sociability": 0.62, "playfulness": 0.48, ... },
 *   "emotions": { "sadness": 0.1, "joy": 0.3, ... },
 *   "adrenaline": 0.0,
 *   "hurt_memory": 0.0
 * }
 * </pre>
 */
public final class PsycheStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PsycheStore() {}

    @Nullable
    public static JsonObject read(UUID id) {
        Path file = VelvetPaths.personality(id);
        if (!Files.isRegularFile(file)) return null;
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Velvet.LOGGER.warn("Couldn't read {}: {}", file, e.toString());
            return null;
        }
    }

    public static void write(UUID id, Psyche psyche) {
        Path file = VelvetPaths.personality(id);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(psyche.toJson()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't write {}: {}", file, e.toString());
        }
    }
}
