package org.velvetlite.clothing;

import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClothingEntry {

    public boolean isEmpty = true;
    private final UUID ownerId;
    private final ClothingSlot slot;
    private final List<String> variants = new ArrayList<>();
    private int value = 0;

    public ClothingEntry(Level level, UUID ownerId, String name) {
        this.ownerId = ownerId;
        this.slot = ClothingSlot.GIRL_SPECIFIC;
        this.variants.add("cross");
        this.variants.add(name);
        this.isEmpty = name == null || name.isEmpty();
    }

    public ClothingEntry(ClothingSlot slot, List<String> variants, int value) {
        this.ownerId = null;
        this.slot = slot;
        this.variants.addAll(variants);
        this.value = value;
    }

    public String getCurrentModel() {
        return variants.isEmpty() ? "" : variants.get(value);
    }

    public ClothingSlot getSlot() { return slot; }
    public List<String> getVariants() { return variants; }
    public int getValue() { return value; }
    public void setValue(int v) { this.value = v; }
    public UUID getOwnerId() { return ownerId; }
}