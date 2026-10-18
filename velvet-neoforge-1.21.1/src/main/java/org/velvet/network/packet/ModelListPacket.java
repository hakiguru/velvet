package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.brain.AiProvider;
import org.velvet.network.NetworkHandler;

import java.util.List;
import java.util.Set;

/**
 * S2C, to the editor: the models a service offers (or why they couldn't be loaded). With the provider
 * "keys" it carries the services the player has a key for instead.
 */
public record ModelListPacket(String provider, List<String> models, String error) implements CustomPacketPayload {

    public static final String KEYS = "keys";

    public static final Type<ModelListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "model_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModelListPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), ModelListPacket::provider,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(256)), ModelListPacket::models,
            ByteBufCodecs.stringUtf8(512), ModelListPacket::error,
            ModelListPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void sendKeys(ServerPlayer player, Set<AiProvider> keys) {
        NetworkHandler.sendToPlayer(player, new ModelListPacket(KEYS, keys.stream().map(p -> p.id).toList(), ""));
    }

    public static void handle(ModelListPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.velvet.client.gui.editor.EditorEvents.modelList(pkt));
    }
}
