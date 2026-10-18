package org.velvet.clothing;

import org.velvet.entity.girl.GirlType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClothingRegistry {

    private static final Map<String, ClothingData> BY_ID = new HashMap<>();
    private static final Map<GirlType, List<ClothingData>> BY_GIRL = new HashMap<>();

    private ClothingRegistry() {}

    public static void clear() {
        BY_ID.clear();
        BY_GIRL.clear();
    }

    public static void register(ClothingData data) {
        BY_ID.put(data.getId(), data);
        for (GirlType t : data.getAllowedGirls()) {
            BY_GIRL.computeIfAbsent(t, k -> new ArrayList<>()).add(data);
        }
    }

    public static ClothingData byId(String id) {
        return BY_ID.get(id);
    }

    public static List<ClothingData> forGirl(GirlType type) {
        return BY_GIRL.getOrDefault(type, List.of());
    }
}