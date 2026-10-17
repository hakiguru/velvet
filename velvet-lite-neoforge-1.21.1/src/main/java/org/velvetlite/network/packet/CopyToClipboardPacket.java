package org.velvetlite.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;

public record CopyToClipboardPacket(String text) implements CustomPacketPayload {

    public static final Type<CopyToClipboardPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "copy_to_clipboard"));

    public static final StreamCodec<FriendlyByteBuf, CopyToClipboardPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CopyToClipboardPacket::text,
                    CopyToClipboardPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CopyToClipboardPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                net.minecraft.client.Minecraft.getInstance()
                        .keyboardHandler.setClipboard(pkt.text());
            }
        });
    }
}