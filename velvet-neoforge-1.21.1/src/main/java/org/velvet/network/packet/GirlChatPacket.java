package org.velvet.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.GirlManager;

import java.util.UUID;

public record GirlChatPacket(String message, int dimension, UUID girlId) implements CustomPacketPayload {

    public static final Type<GirlChatPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "girl_chat"));

    public static final StreamCodec<FriendlyByteBuf, GirlChatPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, GirlChatPacket::message,
                    ByteBufCodecs.VAR_INT,     GirlChatPacket::dimension,
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, GirlChatPacket::girlId,
                    GirlChatPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GirlChatPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(pkt.message()), false);
                }
            } else {
                if (ctx.player() instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayersNear(sp.serverLevel(),
                            sp, sp.getX(), sp.getY(), sp.getZ(), 40.0, pkt);
                }
            }
        });
    }
}