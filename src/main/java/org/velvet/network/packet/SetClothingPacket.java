package org.velvet.network.packet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SetClothingPacket(String modelCode, UUID girlId, List<Integer> variants) implements CustomPacketPayload {

    public static final Type<SetClothingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "set_clothing"));

    public static final StreamCodec<FriendlyByteBuf, SetClothingPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SetClothingPacket decode(FriendlyByteBuf buf) {
            String code = ByteBufCodecs.STRING_UTF8.decode(buf);
            UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
            int n = buf.readVarInt();
            List<Integer> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(buf.readVarInt());
            return new SetClothingPacket(code, id, list);
        }
        @Override
        public void encode(FriendlyByteBuf buf, SetClothingPacket pkt) {
            ByteBufCodecs.STRING_UTF8.encode(buf, pkt.modelCode());
            UUIDUtil.STREAM_CODEC.encode(buf, pkt.girlId());
            buf.writeVarInt(pkt.variants().size());
            for (int v : pkt.variants()) buf.writeVarInt(v);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SetClothingPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* TODO step 7 */ });
    }
}