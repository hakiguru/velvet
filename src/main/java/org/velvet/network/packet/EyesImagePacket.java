package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.brain.EyesService;

/** One piece of what an NPC sees (a JPEG), from her owner's game to the server. */
public record EyesImagePacket(int request, int index, int total, byte[] data) implements CustomPacketPayload {

    /** Packets to the server can't be big. */
    public static final int CHUNK = 30000;

    public static final Type<EyesImagePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "eyes_image"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EyesImagePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EyesImagePacket::request,
            ByteBufCodecs.VAR_INT, EyesImagePacket::index,
            ByteBufCodecs.VAR_INT, EyesImagePacket::total,
            ByteBufCodecs.byteArray(CHUNK), EyesImagePacket::data,
            EyesImagePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EyesImagePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) EyesService.receive(player, pkt.request(), pkt.index(), pkt.total(), pkt.data());
        });
    }
}
