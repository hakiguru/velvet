package org.velvetlite.util;

import net.minecraft.ChatFormatting;

public final class ChatFormattingHelper {
    private ChatFormattingHelper() {}

    public static String color(String text, ChatFormatting fmt) {
        return fmt + text;
    }

    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase();
    }
}