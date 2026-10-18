package org.velvetlite.client.gui.editor;

import net.minecraft.client.Minecraft;
import org.velvetlite.client.gui.GirlEditorScreen;
import org.velvetlite.network.packet.ModelListPacket;

/** Answers from the server for the open editor. */
public final class EditorEvents {

    private EditorEvents() {}

    public static void modelList(ModelListPacket packet) {
        if (Minecraft.getInstance().screen instanceof GirlEditorScreen screen) screen.onModelList(packet);
    }
}
