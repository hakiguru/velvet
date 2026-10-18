package org.velvet.entity.girl.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;
import org.velvet.entity.girl.AbstractGirlEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What she sees around her besides monsters (those are {@link org.velvet.entity.girl.survival.Threats}):
 * ores and natural blocks to build with that show (not through walls), and arrows and other shots flying at
 * her. Blocks are looked at every second, shots every tick.
 */
public final class Senses {

    /** How far she notices ores, if they show. */
    public static final int ORE_SIGHT = 12;
    /** Building material only counts close by: she takes it from where she is. */
    private static final int MATERIAL_SIGHT = 5;
    private static final int SCAN_INTERVAL = 20;

    private final AbstractGirlEntity girl;
    private List<BlockPos> ores = List.of();
    private List<BlockPos> material = List.of();
    @Nullable private Projectile incoming;

    public Senses(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public void tick() {
        incoming = findIncoming();
        if (girl.tickCount % SCAN_INTERVAL == 0) scanBlocks();
    }

    /** Ores she can see, nearest first. */
    public List<BlockPos> ores() {
        return ores;
    }

    /** Natural blocks around her she could take for building, nearest first. */
    public List<BlockPos> material() {
        return material;
    }

    /** A shot flying at her right now (to block or dodge), or null. */
    @Nullable
    public Projectile incoming() {
        return incoming;
    }

    private void scanBlocks() {
        Level level = girl.level();
        BlockPos at = girl.blockPosition();
        List<BlockPos> foundOres = new ArrayList<>();
        List<BlockPos> foundMaterial = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-ORE_SIGHT, -6, -ORE_SIGHT), at.offset(ORE_SIGHT, 6, ORE_SIGHT))) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            boolean ore = state.is(Tags.Blocks.ORES);
            boolean near = Math.abs(p.getX() - at.getX()) <= MATERIAL_SIGHT && Math.abs(p.getZ() - at.getZ()) <= MATERIAL_SIGHT
                    && p.getY() >= at.getY() - 2 && p.getY() <= at.getY() + 3;
            if (!ore && !near) continue;
            if (!exposed(level, p)) continue;
            if (ore) {
                if (foundOres.size() < 16 && BlockWork.visible(girl, p)) foundOres.add(p.immutable());
            } else if (foundMaterial.size() < 24 && BlockWork.isBuildingMaterial(level, p, state) && !p.equals(at.below())
                    && BlockWork.visible(girl, p)) {
                foundMaterial.add(p.immutable());
            }
        }
        Vec3 eye = girl.getEyePosition();
        Comparator<BlockPos> nearest = Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(eye));
        foundOres.sort(nearest);
        foundMaterial.sort(nearest);
        ores = foundOres;
        material = foundMaterial;
    }

    /** At least one side open to the air: buried blocks don't show. */
    private static boolean exposed(Level level, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (!level.getBlockState(p.relative(d)).isSolidRender(level, p.relative(d))) return true;
        }
        return false;
    }

    @Nullable
    private Projectile findIncoming() {
        Vec3 me = girl.position().add(0, girl.getBbHeight() * 0.6, 0);
        Projectile best = null;
        double bestDist = Double.MAX_VALUE;
        for (Projectile p : girl.level().getEntitiesOfClass(Projectile.class, girl.getBoundingBox().inflate(14))) {
            Entity owner = p.getOwner();
            if (owner == girl || owner != null && owner.getUUID().equals(girl.getOwnerUUID())) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            Vec3 to = me.subtract(p.position());
            // heading at her: the line of flight passes within a block of her
            double along = to.dot(v.normalize());
            if (along <= 0) continue;
            double miss = to.subtract(v.normalize().scale(along)).length();
            if (miss > 1.2) continue;
            if (along < bestDist) {
                bestDist = along;
                best = p;
            }
        }
        return best;
    }
}
