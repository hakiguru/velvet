package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.preset.PresetMeta;
import org.velvet.preset.PresetStore;

/** C2S from the editor: make a custom preset, change what it is, or delete it. */
public record PresetActionPacket(int action, String id, String metaJson) implements CustomPacketPayload {

    public static final int CREATE = 0, UPDATE = 1, DELETE = 2;

    public static final Type<PresetActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "preset_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PresetActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PresetActionPacket::action,
            ByteBufCodecs.stringUtf8(32), PresetActionPacket::id,
            ByteBufCodecs.stringUtf8(4096), PresetActionPacket::metaJson,
            PresetActionPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PresetActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.action() == DELETE) {
                PresetStore.delete(player, pkt.id());
                return;
            }
            String id = pkt.action() == CREATE && !PresetMeta.ID.matcher(pkt.id()).matches() ? "preset" : pkt.id();
            PresetMeta meta = PresetMeta.fromJson(id, pkt.metaJson());
            if (meta != null) PresetStore.save(player, meta, pkt.action() == CREATE);
        });
    }
}
