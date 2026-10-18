package org.velvet.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;

/** C2S: open the menu of the girl under the crosshair (key binding); container menus open server-side. */
public record OpenGirlMenuPacket(int entityId) implements CustomPacketPayload {

    public static final Type<OpenGirlMenuPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "open_girl_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenGirlMenuPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenGirlMenuPacket::entityId, OpenGirlMenuPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenGirlMenuPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.level().getEntity(pkt.entityId()) instanceof AbstractGirlEntity girl) {
                girl.openMenu(player); // checks distance, ownership and busy state
            }
        });
    }
}
