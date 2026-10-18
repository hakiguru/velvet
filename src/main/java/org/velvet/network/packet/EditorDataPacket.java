package org.velvet.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.menu.GirlEditorMenu;

/** S2C: the open editor now edits another girl (her tab was clicked): her data, for the screen to show. */
public record EditorDataPacket(int containerId, int entityId, GirlEditorMenu.Data data) implements CustomPacketPayload {

    public static final Type<EditorDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "editor_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorDataPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.containerId());
                buf.writeVarInt(pkt.entityId());
                pkt.data().write(buf);
            },
            buf -> new EditorDataPacket(buf.readVarInt(), buf.readVarInt(), GirlEditorMenu.Data.read(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EditorDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.velvet.client.gui.editor.EditorEvents.retarget(pkt));
    }
}
