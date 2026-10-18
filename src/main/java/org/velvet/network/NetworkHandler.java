package org.velvet.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.velvet.Velvet;
import org.velvet.network.packet.CopyToClipboardPacket;
import org.velvet.network.packet.*;

public final class NetworkHandler {

    private NetworkHandler() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(NetworkHandler::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Velvet.MODID).versioned("4");

        // --- C2S ---
        r.playToServer(SetFollowingPacket.TYPE,       SetFollowingPacket.STREAM_CODEC,       SetFollowingPacket::handle);
        r.playToServer(SaveGirlEditorPacket.TYPE,     SaveGirlEditorPacket.STREAM_CODEC,     SaveGirlEditorPacket::handle);
        r.playToServer(EditorSelectGirlPacket.TYPE,   EditorSelectGirlPacket.STREAM_CODEC,   EditorSelectGirlPacket::handle);
        r.playToServer(SetAiKeyPacket.TYPE,           SetAiKeyPacket.STREAM_CODEC,           SetAiKeyPacket::handle);
        r.playToServer(FetchModelsPacket.TYPE,        FetchModelsPacket.STREAM_CODEC,        FetchModelsPacket::handle);
        r.playToServer(SkinRequestPacket.TYPE,        SkinRequestPacket.STREAM_CODEC,        SkinRequestPacket::handle);
        r.playToServer(SendGirlHomePacket.TYPE,       SendGirlHomePacket.STREAM_CODEC,       SendGirlHomePacket::handle);
        r.playToServer(SetHomePositionPacket.TYPE,    SetHomePositionPacket.STREAM_CODEC,    SetHomePositionPacket::handle);
        r.playToServer(SetFutaStatePacket.TYPE,       SetFutaStatePacket.STREAM_CODEC,       SetFutaStatePacket::handle);
        r.playToServer(OpenGirlMenuPacket.TYPE,       OpenGirlMenuPacket.STREAM_CODEC,       OpenGirlMenuPacket::handle);
        r.playToServer(StartScenePacket.TYPE,         StartScenePacket.STREAM_CODEC,         StartScenePacket::handle);
        r.playToServer(SceneControlPacket.TYPE,       SceneControlPacket.STREAM_CODEC,       SceneControlPacket::handle);
        r.playToServer(SetOutfitPacket.TYPE,          SetOutfitPacket.STREAM_CODEC,          SetOutfitPacket::handle);
        r.playToServer(SetClothingPacket.TYPE,        SetClothingPacket.STREAM_CODEC,        SetClothingPacket::handle);
        r.playToServer(CreateNpcPacket.TYPE,          CreateNpcPacket.STREAM_CODEC,          CreateNpcPacket::handle);

        // --- both directions (handlers branch on ctx.flow()) ---
        r.playToClient(GirlChatPacket.TYPE, GirlChatPacket.STREAM_CODEC, GirlChatPacket::handle);
        r.playBidirectional(SetCustomModelPacket.TYPE, SetCustomModelPacket.STREAM_CODEC, SetCustomModelPacket::handle);
        r.playBidirectional(SkinChunkPacket.TYPE, SkinChunkPacket.STREAM_CODEC, SkinChunkPacket::handle);
        r.playBidirectional(ChunkedModelDataPacket.TYPE, ChunkedModelDataPacket.STREAM_CODEC, ChunkedModelDataPacket::handle);

        // --- S2C ---
        r.playToClient(CopyToClipboardPacket.TYPE,    CopyToClipboardPacket.STREAM_CODEC,    CopyToClipboardPacket::handle);
        r.playToClient(SendCustomModelDataPacket.TYPE,SendCustomModelDataPacket.STREAM_CODEC,SendCustomModelDataPacket::handle);
        r.playToClient(SetDynamicModelPacket.TYPE,    SetDynamicModelPacket.STREAM_CODEC,    SetDynamicModelPacket::handle);
        r.playToClient(ModelListPacket.TYPE,          ModelListPacket.STREAM_CODEC,          ModelListPacket::handle);
        r.playToClient(SkinUploadResultPacket.TYPE,   SkinUploadResultPacket.STREAM_CODEC,   SkinUploadResultPacket::handle);
        r.playToClient(HornyMeterPacket.TYPE,         HornyMeterPacket.STREAM_CODEC,         HornyMeterPacket::handle);
        r.playToClient(EditorDataPacket.TYPE,         EditorDataPacket.STREAM_CODEC,         EditorDataPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllAround(CustomPacketPayload payload, ServerLevel level, double x, double y, double z, double radius) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, radius, payload);
    }

    public static void sendToAllTracking(CustomPacketPayload payload, net.minecraft.world.entity.Entity tracked) {
        if (tracked.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(tracked, payload);
        }
    }
}