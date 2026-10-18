package org.velvetlite.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvetlite.VelvetLite;
import org.velvetlite.entity.girl.brain.AiClient;
import org.velvetlite.entity.girl.brain.AiKeys;
import org.velvetlite.entity.girl.brain.AiProvider;
import org.velvetlite.menu.GirlEditorMenu;
import org.velvetlite.network.NetworkHandler;

import java.util.List;

/**
 * C2S: load the models a service offers, with the player's key (and for local ones the address typed in).
 * Doubles as a connection test; answered with {@link ModelListPacket}.
 */
public record FetchModelsPacket(String provider, String endpoint) implements CustomPacketPayload {

    public static final Type<FetchModelsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VelvetLite.MODID, "fetch_models"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FetchModelsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), FetchModelsPacket::provider,
            ByteBufCodecs.stringUtf8(256), FetchModelsPacket::endpoint,
            FetchModelsPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FetchModelsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof GirlEditorMenu)) return;
            AiProvider provider = AiProvider.byId(pkt.provider());
            if (provider == null || !provider.isExternal()) return;
            String base = provider.baseUrl;
            if (provider.local) {
                if (!GirlEditorMenu.mayUseLocalEndpoints(player)) {
                    NetworkHandler.sendToPlayer(player, new ModelListPacket(provider.id, List.of(), "local_not_allowed"));
                    return;
                }
                if (!pkt.endpoint().isBlank()) base = pkt.endpoint().trim();
            }
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String key = AiKeys.get(player.server).key(player.getUUID(), provider);
            if (provider.needsKey && key == null) {
                NetworkHandler.sendToPlayer(player, new ModelListPacket(provider.id, List.of(), "no_key"));
                return;
            }
            try {
                AiClient.listModels(provider, base, key).whenComplete((models, error) -> player.server.execute(() ->
                        NetworkHandler.sendToPlayer(player, error != null
                                ? new ModelListPacket(provider.id, List.of(), AiClient.describe(error))
                                : new ModelListPacket(provider.id, models.subList(0, Math.min(models.size(), 200)), ""))));
            } catch (RuntimeException e) {
                NetworkHandler.sendToPlayer(player, new ModelListPacket(provider.id, List.of(), AiClient.describe(e)));
            }
        });
    }
}
