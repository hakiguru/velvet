package org.velvetlite.skin;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A custom texture on a girl, from the editor: an uploaded PNG (known by its SHA-1) that either replaces her
 * texture or is drawn over it (transparent where it should show the original). Synced as "mode:hash".
 */
public record GirlSkin(Mode mode, String hash) {

    public enum Mode {
        /** Instead of her own texture: same layout, all of it repainted. */
        REPLACE,
        /** On top of her own texture, where it isn't transparent. */
        OVERLAY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public String encode() {
        return mode.id() + ":" + hash;
    }

    @Nullable
    public static GirlSkin decode(String text) {
        int colon = text.indexOf(':');
        if (colon < 0) return null;
        String hash = text.substring(colon + 1);
        if (!isHash(hash)) return null;
        try {
            return new GirlSkin(Mode.valueOf(text.substring(0, colon).toUpperCase(Locale.ROOT)), hash);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isHash(String hash) {
        return hash.length() == 40 && hash.chars().allMatch(c -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f');
    }
}
