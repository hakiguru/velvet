package org.velvetlite.custommodel;

import net.minecraft.resources.ResourceLocation;
import org.velvetlite.clothing.ClothingLighting;
import org.velvetlite.clothing.ClothingSlot;
import org.velvetlite.entity.girl.GirlType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class CustomModelDefinition {

    public final String id;
    public final ResourceLocation geoModel;
    public final ResourceLocation texture;
    public ClothingSlot slot = ClothingSlot.CUSTOM_BONE;
    public String boneName = "";
    public Set<GirlType> girls = EnumSet.noneOf(GirlType.class);
    public Set<String> hiddenBones = new HashSet<>();
    public ClothingLighting lighting = ClothingLighting.DEFAULT;
    public float scale = 1.0F;
    public float offsetX = 0.0F;
    public float offsetY = 0.0F;

    public CustomModelDefinition(String id, ResourceLocation geoModel, ResourceLocation texture) {
        this.id = id;
        this.geoModel = geoModel;
        this.texture = texture;
    }

    public static CustomModelDefinition fromConfig(String id, InputStream cfg,
                                                   ResourceLocation geo, ResourceLocation tex) throws IOException {
        CustomModelDefinition def = new CustomModelDefinition(id, geo, tex);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(cfg))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim().toLowerCase();
                String val = line.substring(eq + 1).trim();
                switch (key) {
                    case "slot"       -> def.slot = ClothingSlot.valueOf(val.toUpperCase());
                    case "bone"       -> def.boneName = val;
                    case "girls"      -> {
                        for (String g : val.split(",")) def.girls.add(GirlType.valueOf(g.trim().toUpperCase()));
                    }
                    case "hidden"     -> {
                        for (String b : val.split(",")) def.hiddenBones.add(b.trim());
                    }
                    case "lighting"   -> def.lighting = ClothingLighting.valueOf(val.toUpperCase());
                    case "scale"      -> def.scale = Float.parseFloat(val);
                    case "offset_x"   -> def.offsetX = Float.parseFloat(val);
                    case "offset_y"   -> def.offsetY = Float.parseFloat(val);
                }
            }
        }
        return def;
    }
}