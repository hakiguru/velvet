package org.velvetlite.entity.girl;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.velvetlite.VelvetLite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = VelvetLite.MODID)
public final class GirlManager {

    // Entity.equals compares entity ids, and in singleplayer the client and server copy of a girl share
    // her id, so one set for both sides would keep only one of them.
    private static final Set<AbstractGirlEntity> CLIENT = ConcurrentHashMap.newKeySet();
    private static final Set<AbstractGirlEntity> SERVER = ConcurrentHashMap.newKeySet();

    private GirlManager() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof AbstractGirlEntity girl) {
            sideOf(girl).add(girl);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractGirlEntity girl) {
            sideOf(girl).remove(girl);
        }
    }

    private static Set<AbstractGirlEntity> sideOf(AbstractGirlEntity girl) {
        return girl.level().isClientSide() ? CLIENT : SERVER;
    }

    /** Girls of both sides (in singleplayer both copies of each girl). */
    public static List<AbstractGirlEntity> all() {
        List<AbstractGirlEntity> all = new ArrayList<>(SERVER);
        all.addAll(CLIENT);
        return all;
    }

    @Nullable
    public static AbstractGirlEntity byUuid(UUID id) {
        for (AbstractGirlEntity girl : all()) {
            if (girl.getGirlUUID().equals(id)) return girl;
        }
        return null;
    }

    @Nullable
    public static AbstractGirlEntity byUuid(UUID id, boolean clientSide) {
        for (AbstractGirlEntity girl : all()) {
            if (girl.getGirlUUID().equals(id) && girl.level().isClientSide() == clientSide) {
                return girl;
            }
        }
        return null;
    }

    /**
     * Girls with this id on the side of the calling thread. In singleplayer there's both the
     * client and the server copy, and a packet handler must only touch its own side's entity.
     */
    public static List<AbstractGirlEntity> allByUuid(UUID id) {
        boolean clientThread = EffectiveSide.get().isClient();
        List<AbstractGirlEntity> out = new ArrayList<>();
        for (AbstractGirlEntity girl : all()) {
            if (girl.level().isClientSide() == clientThread && girl.getGirlUUID().equals(id)) out.add(girl);
        }
        return out;
    }

    /** The girl {@code player} is currently in a scene with, on the calling thread's side. */
    @Nullable
    public static AbstractGirlEntity partnerOf(Player player) {
        boolean clientThread = EffectiveSide.get().isClient();
        for (AbstractGirlEntity girl : all()) {
            if (girl.level().isClientSide() == clientThread && girl.getCurrentAction().hasPartner()
                    && player.getUUID().equals(girl.getPartnerUUID())) {
                return girl;
            }
        }
        return null;
    }

    /** Like {@link #partnerOf}, but only while the player is locked to her and drawn through her model. */
    @Nullable
    public static AbstractGirlEntity attachedTo(Player player) {
        AbstractGirlEntity girl = partnerOf(player);
        return girl != null && girl.getCurrentAction().isAttached() ? girl : null;
    }

    /** Whether {@code player} may give orders to {@code girl}: close enough and not someone else's. */
    public static boolean canCommand(Player player, AbstractGirlEntity girl) {
        UUID owner = girl.getOwnerUUID();
        return girl.distanceToSqr(player) <= 8.0 * 8.0 && (owner == null || owner.equals(player.getUUID()));
    }

    @Nullable
    public static AbstractGirlEntity byOwner(UUID ownerId) {
        for (AbstractGirlEntity girl : all()) {
            UUID owner = girl.getOwnerUUID();
            if (owner != null && owner.equals(ownerId)) return girl;
        }
        return null;
    }

    public static void clearAll() {
        CLIENT.clear();
        SERVER.clear();
    }
}