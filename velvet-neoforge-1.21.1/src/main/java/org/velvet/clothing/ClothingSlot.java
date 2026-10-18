package org.velvet.clothing;

public enum ClothingSlot {
    GIRL_SPECIFIC,
    HEAD(0, "customHead"),
    FOOT_L(60, "customShoeL"),
    FOOT_R(80, "customShoeR"),
    HAND_L(100, "customHandL"),
    HAND_R(120, "customHandR"),
    CUSTOM_BONE(140);

    public static final String SEPARATOR = "#";

    public int iconXPos = 0;
    public String boneName = null;

    ClothingSlot() {}
    ClothingSlot(int iconXPos) { this.iconXPos = iconXPos; }
    ClothingSlot(int iconXPos, String boneName) {
        this.iconXPos = iconXPos;
        this.boneName = boneName;
    }

    public static int displayCount() {
        return values().length - 2;
    }
}