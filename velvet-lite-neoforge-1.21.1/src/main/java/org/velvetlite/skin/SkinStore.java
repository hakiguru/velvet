package org.velvetlite.skin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.VelvetLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server side: uploaded skins as files in the world folder (velvet_lite/skins/<sha1>.png). */
public final class SkinStore {

    private SkinStore() {}

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(VelvetLite.MODID).resolve("skins");
    }

    public static boolean has(MinecraftServer server, String hash) {
        return GirlSkin.isHash(hash) && Files.isRegularFile(folder(server).resolve(hash + ".png"));
    }

    /** Stores the file under its hash and returns that hash. */
    public static String save(MinecraftServer server, byte[] data) throws IOException {
        String hash = SkinFiles.sha1(data);
        Path file = folder(server).resolve(hash + ".png");
        if (!Files.isRegularFile(file)) {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        }
        return hash;
    }

    @Nullable
    public static byte[] load(MinecraftServer server, String hash) {
        if (!GirlSkin.isHash(hash)) return null;
        try {
            return Files.readAllBytes(folder(server).resolve(hash + ".png"));
        } catch (IOException e) {
            return null;
        }
    }
}
