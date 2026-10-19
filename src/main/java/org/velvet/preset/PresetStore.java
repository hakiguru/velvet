package org.velvet.preset;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.Nullable;
import org.velvet.Velvet;
import org.velvet.VelvetPaths;
import org.velvet.entity.girl.GirlType;
import org.velvet.network.NetworkHandler;
import org.velvet.network.packet.PresetFilePacket;
import org.velvet.network.packet.PresetListPacket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The server's custom presets, from {@code config/velvet/presets/<id>/} (see {@link PresetFiles}): read when the
 * server starts, sent to every player who joins, and changed from the editor by players allowed to (operators,
 * creative players, the host of a single player world), who upload the files they made. Every change goes to
 * everyone online at once, so they all see the same NPCs.
 */
@EventBusSubscriber(modid = Velvet.MODID)
public final class PresetStore {

    private PresetStore() {}

    private static final Map<String, PresetMeta> METAS = new LinkedHashMap<>();
    private static final Map<String, Map<String, byte[]>> FILES = new LinkedHashMap<>();
    /** Uploads on their way: player, preset and file to the pieces so far. */
    private static final Map<String, ByteArrayOutputStream> UPLOADS = new LinkedHashMap<>();

    @SubscribeEvent
    public static void onStart(ServerAboutToStartEvent event) {
        for (GirlType t : GirlType.customs()) GirlType.removeCustom(t.id());
        METAS.clear();
        FILES.clear();
        Path root = VelvetPaths.presets();
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.sorted().toList()) {
                String id = dir.getFileName().toString();
                Path metaFile = dir.resolve(PresetFiles.META);
                if (!Files.isRegularFile(metaFile)) continue;
                PresetMeta meta = PresetMeta.fromJson(id, Files.readString(metaFile, StandardCharsets.UTF_8));
                if (meta == null) {
                    Velvet.LOGGER.warn("Skipping preset folder {}: bad id or preset.json", dir);
                    continue;
                }
                Map<String, byte[]> files = new LinkedHashMap<>();
                try (Stream<Path> all = Files.walk(dir)) {
                    for (Path f : all.filter(Files::isRegularFile).toList()) {
                        String rel = dir.relativize(f).toString().replace('\\', '/');
                        if (!PresetFiles.allowed(rel) || rel.equals(PresetFiles.META)) continue;
                        if (Files.size(f) > PresetFiles.maxSize(rel)) continue;
                        files.put(rel, Files.readAllBytes(f));
                    }
                }
                METAS.put(id, meta);
                FILES.put(id, files);
                meta.register();
            }
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't read the presets in {}: {}", root, e.toString());
        }
        Velvet.LOGGER.info("Loaded {} custom presets", METAS.size());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        NetworkHandler.sendToPlayer(player, list());
        FILES.forEach((id, files) -> files.forEach((path, bytes) -> PresetFilePacket.send(player, id, path, bytes)));
    }

    public static List<PresetMeta> all() {
        return new ArrayList<>(METAS.values());
    }

    private static PresetListPacket list() {
        List<PresetListPacket.Entry> entries = new ArrayList<>();
        for (PresetMeta m : METAS.values()) entries.add(new PresetListPacket.Entry(m.id(), m.toJson()));
        return new PresetListPacket(entries);
    }

    /** Operators, creative players and the host of a single player world. */
    public static boolean mayEdit(ServerPlayer player) {
        return player.hasPermissions(2) || player.isCreative() || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    // ---- Changes from the editor ----

    /** A new preset, or new settings for one; the id is made unique for a new one. */
    @Nullable
    public static PresetMeta save(ServerPlayer player, PresetMeta meta, boolean create) {
        if (!mayEdit(player)) {
            deny(player);
            return null;
        }
        String id = meta.id();
        if (create) {
            String base = PresetMeta.ID.matcher(id).matches() ? id : PresetMeta.idFor(meta.name());
            id = base;
            for (int n = 2; METAS.containsKey(id) || GirlType.byId(id) != null; n++) id = base + "_" + n;
        } else if (!METAS.containsKey(id)) {
            return null;
        }
        PresetMeta saved = new PresetMeta(id, meta.name(), meta.base(), meta.height(), meta.voice(), meta.strips(), meta.translucent());
        try {
            Path dir = VelvetPaths.presets().resolve(id);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(PresetFiles.META), saved.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't save preset {}: {}", id, e.toString());
            return null;
        }
        METAS.put(id, saved);
        FILES.computeIfAbsent(id, k -> new LinkedHashMap<>());
        saved.register();
        broadcastList(player.server);
        return saved;
    }

    public static void delete(ServerPlayer player, String id) {
        if (!mayEdit(player)) {
            deny(player);
            return;
        }
        if (METAS.remove(id) == null) return;
        FILES.remove(id);
        GirlType.removeCustom(id);
        Path dir = VelvetPaths.presets().resolve(id);
        try (Stream<Path> all = Files.walk(dir)) {
            for (Path p : all.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't delete preset folder {}: {}", dir, e.toString());
        }
        broadcastList(player.server);
    }

    /** A piece of a file someone saved in the editor; the whole file is checked, kept and sent on. */
    public static void receive(ServerPlayer player, String id, String path, int index, int total, byte[] data) {
        if (!METAS.containsKey(id) || !PresetFiles.allowed(path) || path.equals(PresetFiles.META)) return;
        if (!mayEdit(player)) {
            if (index == 0) deny(player);
            return;
        }
        String key = player.getUUID() + "|" + id + "|" + path;
        ByteArrayOutputStream out = index == 0 ? new ByteArrayOutputStream() : UPLOADS.get(key);
        if (out == null) return;
        if (out.size() + data.length > PresetFiles.maxSize(path)) {
            UPLOADS.remove(key);
            player.sendSystemMessage(Component.translatable("editor.velvet.preset.too_big", path).withStyle(ChatFormatting.RED));
            return;
        }
        out.writeBytes(data);
        UPLOADS.put(key, out);
        if (index < total - 1) return;
        UPLOADS.remove(key);
        byte[] bytes = out.toByteArray();
        try {
            Path file = VelvetPaths.presets().resolve(id).resolve(path);
            Files.createDirectories(file.getParent());
            if (bytes.length == 0) Files.deleteIfExists(file);
            else Files.write(file, bytes);
        } catch (IOException e) {
            Velvet.LOGGER.warn("Couldn't save {} of preset {}: {}", path, id, e.toString());
            return;
        }
        // an empty file means it's gone (back to the base preset's)
        if (bytes.length == 0) FILES.get(id).remove(path);
        else FILES.get(id).put(path, bytes);
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) PresetFilePacket.send(p, id, path, bytes);
    }

    private static void broadcastList(MinecraftServer server) {
        PresetListPacket packet = list();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) NetworkHandler.sendToPlayer(p, packet);
    }

    private static void deny(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("editor.velvet.preset.not_allowed").withStyle(ChatFormatting.RED));
    }
}
