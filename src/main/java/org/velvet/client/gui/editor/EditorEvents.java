package org.velvet.client.gui.editor;

import net.minecraft.client.Minecraft;
import org.velvet.client.gui.GirlEditorScreen;
import org.velvet.network.packet.ModelListPacket;

/** Answers from the server for the open editor. */
public final class EditorEvents {

    private EditorEvents() {}

    /** The editor turned to another girl: her data for the open screen. */
    public static void retarget(org.velvet.network.packet.EditorDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GirlEditorScreen screen) || mc.player == null
                || mc.player.containerMenu.containerId != packet.containerId()) return;
        var entity = mc.level == null ? null : mc.level.getEntity(packet.entityId());
        screen.retarget(entity instanceof org.velvet.entity.girl.AbstractGirlEntity g ? g : null, packet.data());
    }

    public static void modelList(ModelListPacket packet) {
        if (Minecraft.getInstance().screen instanceof GirlEditorScreen screen) screen.onModelList(packet);
    }
}
