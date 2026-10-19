package org.velvet.compat.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import org.velvet.Config;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.brain.VoiceBridge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat's side of {@link VoiceBridge}: found by that mod through its annotation, and only then
 * loaded, so Velvet runs the same without it. Microphone packets of players are decoded and handed on; her
 * speech is played through an audio channel that follows her around.
 */
@ForgeVoicechatPlugin
public final class VelvetVoicePlugin implements VoicechatPlugin {

    private static volatile VoicechatServerApi api;
    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return Velvet.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        Velvet.LOGGER.info("Simple Voice Chat found: NPCs can hear and speak");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            VoiceBridge.setBackend(VelvetVoicePlugin::play);
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            api = null;
            VoiceBridge.setBackend(null);
            decoders.values().forEach(OpusDecoder::close);
            decoders.clear();
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent e) {
        if (e.getSenderConnection() == null || api == null) return;
        UUID player = e.getSenderConnection().getPlayer().getUuid();
        byte[] opus = e.getPacket().getOpusEncodedData();
        if (opus.length == 0) return;
        OpusDecoder decoder = decoders.computeIfAbsent(player, id -> api.createDecoder());
        VoiceBridge.onMicrophone(player, decoder.decode(opus));
    }

    private static void play(AbstractGirlEntity girl, short[] samples) {
        VoicechatServerApi a = api;
        if (a == null) return;
        EntityAudioChannel channel = a.createEntityAudioChannel(UUID.randomUUID(), a.fromEntity(girl));
        if (channel == null) return;
        channel.setDistance(Config.VOICE_DISTANCE.get().floatValue());
        AudioPlayer player = a.createAudioPlayer(channel, a.createEncoder(), samples);
        player.startPlaying();
    }
}
