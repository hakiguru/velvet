package org.velvet.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

import java.util.HashMap;
import java.util.Map;

public record SetCustomModelPacket(Map<String, Float> scales) implements CustomPacketPayload {

    public static final Type<SetCustomModelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "set_custom_model"));

    public static final StreamCodec<FriendlyByteBuf, SetCustomModelPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SetCustomModelPacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            Map<String, Float> map = new HashMap<>(n);
            for (int i = 0; i < n; i++) {
                map.put(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat());
            }
            return new SetCustomModelPacket(map);
        }
        @Override
        public void encode(FriendlyByteBuf buf, SetCustomModelPacket pkt) {
            buf.writeVarInt(pkt.scales().size());
            for (var e : pkt.scales().entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, e.getKey());
                buf.writeFloat(e.getValue());
            }
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SetCustomModelPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* TODO step 7 */ });
    }
}