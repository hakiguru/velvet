package org.velvetlite.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.brain.AiKeys;
import org.velvetlite.entity.girl.brain.AiProvider;
import org.velvetlite.menu.GirlEditorMenu;

/** C2S: the player's API key for a service (empty: remove it). Answered with the services that have one. */
public record SetAiKeyPacket(String provider, String key) implements CustomPacketPayload {

    public static final Type<SetAiKeyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "set_ai_key"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetAiKeyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), SetAiKeyPacket::provider,
            ByteBufCodecs.stringUtf8(AiKeys.MAX_KEY_LENGTH), SetAiKeyPacket::key,
            SetAiKeyPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetAiKeyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof GirlEditorMenu)) return;
            AiProvider provider = AiProvider.byId(pkt.provider());
            if (provider == null || !provider.isExternal()) return;
            AiKeys keys = AiKeys.get(player.server);
            keys.set(player.getUUID(), provider, pkt.key());
            ModelListPacket.sendKeys(player, keys.withKey(player.getUUID()));
        });
    }
}
