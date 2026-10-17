package org.velvetlite.custommodel;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.GirlType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomModelManager {

    // All registered custom models by ID
    private static final Map<String, CustomModelDefinition> MODELS = new java.util.HashMap<>();
    // Girl-specific lists (each girl has a list of allowed model IDs)
    private static final Map<GirlType, List<String>> GIRL_MODELS = new EnumMap<>(GirlType.class);

    private CustomModelManager() {}

    public static void resetStaticFlags() {
        MODELS.clear();
        GIRL_MODELS.clear();
    }

    public static void loadAll() {
        MODELS.clear();
        GIRL_MODELS.clear();

        Minecraft mc = Minecraft.getInstance();
        var rm = mc.getResourceManager();

        try {
            var allResources = rm.listResources("custom_models",
                    path -> path.getPath().endsWith(".cfg"));

            for (var entry : allResources.entrySet()) {
                ResourceLocation cfgLoc = entry.getKey();
                String path = cfgLoc.getPath();
                String id = path.substring("custom_models/".length(),
                        path.length() - ".cfg".length());
                if (id.contains("/")) continue;

                try (InputStream cfg = entry.getValue().open()) {

                    ResourceLocation geo = ResourceLocation.fromNamespaceAndPath(
                            VelvetLite.MODID, "custom_models/" + id + ".geo.json");
                    ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(
                            VelvetLite.MODID, "custom_models/" + id + ".png");

                    CustomModelDefinition def = CustomModelDefinition.fromConfig(id, cfg, geo, tex);
                    MODELS.put(id, def);

                    for (GirlType g : def.girls) {
                        GIRL_MODELS.computeIfAbsent(g, k -> new ArrayList<>()).add(id);
                    }
                }
            }

            VelvetLite.LOGGER.info("Loaded {} custom models", MODELS.size());
        } catch (IOException e) {
            VelvetLite.LOGGER.error("Failed loading custom models", e);
        }
    }

    public static CustomModelDefinition get(String id) {
        return MODELS.get(id);
    }

    public static List<String> forGirl(GirlType type) {
        return GIRL_MODELS.getOrDefault(type, List.of());
    }

    public static Set<String> allIds() {
        return new HashSet<>(MODELS.keySet());
    }
}