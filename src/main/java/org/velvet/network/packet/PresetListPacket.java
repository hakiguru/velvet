package org.velvet.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;

import java.util.ArrayList;
import java.util.List;

/** S2C: every custom preset the server has (id and preset.json); ones not in it are gone. */
public record PresetListPacket(List<Entry> presets) implements CustomPacketPayload {

    public record Entry(String id, String metaJson) {}

    public static final Type<PresetListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "preset_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PresetListPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeVarInt(pkt.presets().size());
                for (Entry e : pkt.presets()) {
                    buf.writeUtf(e.id(), 32);
                    buf.writeUtf(e.metaJson(), 4096);
                }
            },
            buf -> {
                int n = Math.min(buf.readVarInt(), 512);
                List<Entry> list = new ArrayList<>();
                for (int i = 0; i < n; i++) list.add(new Entry(buf.readUtf(32), buf.readUtf(4096)));
                return new PresetListPacket(list);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PresetListPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> org.velvet.client.preset.PresetCache.onList(pkt.presets()));
    }
}
