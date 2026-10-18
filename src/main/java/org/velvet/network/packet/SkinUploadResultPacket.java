package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

/** S2C: the uploaded skin is stored (error empty) or was refused. */
public record SkinUploadResultPacket(String hash, String error) implements CustomPacketPayload {

    public static final Type<SkinUploadResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "skin_upload_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinUploadResultPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(40), SkinUploadResultPacket::hash,
            ByteBufCodecs.stringUtf8(256), SkinUploadResultPacket::error,
            SkinUploadResultPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkinUploadResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.velvet.client.skin.SkinCache.uploadResult(pkt.hash(), pkt.error()));
    }
}
