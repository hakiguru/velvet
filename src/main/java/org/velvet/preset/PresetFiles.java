package org.velvet.preset;

import java.util.regex.Pattern;

/**
 * The files a custom preset may have in its folder, and how big they may be. Nothing else is read, written
 * or sent.
 * <ul>
 *   <li>{@code preset.json}: what it is ({@link PresetMeta})</li>
 *   <li>{@code model.geo.json}, {@code dressed.geo.json}: the model, undressed and dressed (Bedrock geometry 1.12,
 *   as Blockbench exports it)</li>
 *   <li>{@code texture.png}</li>
 *   <li>{@code animations.json}: its own animations, named "animation.&lt;id&gt;.&lt;name&gt;"</li>
 *   <li>{@code sounds/<kind>/<n>.ogg}: its own voice, a few takes of each kind (moan, giggle, ...)</li>
 * </ul>
 */
public final class PresetFiles {

    private PresetFiles() {}

    public static final String META = "preset.json", MODEL = "model.geo.json", DRESSED = "dressed.geo.json",
            TEXTURE = "texture.png", ANIMATIONS = "animations.json";
    private static final Pattern SOUND = Pattern.compile("sounds/[a-z_]{1,24}/[0-9]{1,2}\\.ogg");
    public static final int MAX_JSON = 4 * 1024 * 1024, MAX_TEXTURE = 24 * 1024 * 1024, MAX_SOUND = 2 * 1024 * 1024;

    public static boolean allowed(String path) {
        return path.equals(META) || path.equals(MODEL) || path.equals(DRESSED) || path.equals(TEXTURE)
                || path.equals(ANIMATIONS) || SOUND.matcher(path).matches();
    }

    public static int maxSize(String path) {
        return path.equals(TEXTURE) ? MAX_TEXTURE : path.endsWith(".ogg") ? MAX_SOUND : MAX_JSON;
    }

    public static boolean isSound(String path) {
        return path.startsWith("sounds/");
    }
}
