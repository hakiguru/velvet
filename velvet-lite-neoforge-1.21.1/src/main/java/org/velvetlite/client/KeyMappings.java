package org.velvetlite.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyMappings {
    private KeyMappings() {}

    private static final String CATEGORY = "key.categories.velvet_lite";

    /**
     * Scene keys only matter while the player is locked in a scene, where jumping does nothing anyway, so
     * they may share a key with in-game vanilla ones (space by default); NeoForge presses every mapping
     * bound to a key, and {@link #isHeld} reads the key directly anyway.
     */
    private static final IKeyConflictContext SCENE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    /** Opens the menu of the girl under the crosshair. */
    public static KeyMapping interactWithGirl;
    /** Held during a scene: the faster pace. */
    public static KeyMapping sceneFaster;
    /** Switches the camera view during a scene. */
    public static KeyMapping sceneCamera;

    public static void register(RegisterKeyMappingsEvent event) {
        interactWithGirl = new KeyMapping("key.velvet_lite.interact",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
        sceneFaster = new KeyMapping("key.velvet_lite.scene_faster", SCENE,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
        sceneCamera = new KeyMapping("key.velvet_lite.scene_camera", SCENE,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
        event.register(interactWithGirl);
        event.register(sceneFaster);
        event.register(sceneCamera);
    }

    /**
     * Whether the key bound to {@code mapping} is physically held. Read straight from the window, so it
     * works even when another mapping on the same key took the press.
     */
    public static boolean isHeld(KeyMapping mapping) {
        if (mapping.isUnbound()) return false;
        InputConstants.Key key = mapping.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }
}
