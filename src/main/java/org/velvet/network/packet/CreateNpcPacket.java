package org.velvet.network.packet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.velvet.Velvet;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlRegistry;
import org.velvet.menu.GirlEditorMenu;
import org.velvet.registry.VelvetItems;

/**
 * C2S: "New NPC" under the list of girls in the editor: a blank NPC next to the player, his, and the editor
 * turns to her. In survival it costs a spawn egg from his inventory.
 */
public record CreateNpcPacket() implements CustomPacketPayload {

    public static final Type<CreateNpcPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Velvet.MODID, "create_npc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateNpcPacket> STREAM_CODEC = StreamCodec.unit(new CreateNpcPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CreateNpcPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof GirlEditorMenu menu)) return;
            if (!player.getAbilities().instabuild && !takeEgg(player.getInventory())) {
                player.displayClientMessage(Component.translatable("editor.velvet.new_npc.needs_egg").withStyle(ChatFormatting.RED), true);
                return;
            }
            AbstractGirlEntity girl = GirlRegistry.NPC.get().create(player.serverLevel());
            if (girl == null) return;
            Vec3 at = spot(player);
            girl.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
            girl.setYHeadRot(player.getYRot() + 180);
            girl.setYBodyRot(player.getYRot() + 180);
            girl.setOwnerUUID(player.getUUID());
            girl.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(BlockPos.containing(at)),
                    MobSpawnType.SPAWN_EGG, null);
            player.serverLevel().addFreshEntity(girl);
            menu.retarget(player, girl);
        });
    }

    /** Two blocks in front of him where there's room, else where he stands. */
    private static Vec3 spot(ServerPlayer player) {
        Vec3 ahead = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(2));
        BlockPos p = BlockPos.containing(ahead);
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos q = p.above(dy);
            if (player.level().getBlockState(q).getCollisionShape(player.level(), q).isEmpty()
                    && player.level().getBlockState(q.above()).getCollisionShape(player.level(), q.above()).isEmpty()
                    && !player.level().getBlockState(q.below()).getCollisionShape(player.level(), q.below()).isEmpty()) {
                return new Vec3(ahead.x, q.getY(), ahead.z);
            }
        }
        return player.position();
    }

    private static boolean takeEgg(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(VelvetItems.NPC_SPAWN_EGG.get())) {
                inv.getItem(i).shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }
}
