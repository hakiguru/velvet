package org.velvetlite.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;

public record ChunkedModelDataPacket(byte[] data, String fileName, String fileType, int index) implements CustomPacketPayload {

    public static final Type<ChunkedModelDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "chunked_model_data"));

    public static final StreamCodec<FriendlyByteBuf, ChunkedModelDataPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChunkedModelDataPacket decode(FriendlyByteBuf buf) {
            int len = buf.readVarInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            String name = buf.readUtf();
            String type = buf.readUtf();
            int idx = buf.readVarInt();
            return new ChunkedModelDataPacket(bytes, name, type, idx);
        }
        @Override
        public void encode(FriendlyByteBuf buf, ChunkedModelDataPacket pkt) {
            buf.writeVarInt(pkt.data().length);
            buf.writeBytes(pkt.data());
            buf.writeUtf(pkt.fileName());
            buf.writeUtf(pkt.fileType());
            buf.writeVarInt(pkt.index());
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(ChunkedModelDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* TODO step 7 */ });
    }
}