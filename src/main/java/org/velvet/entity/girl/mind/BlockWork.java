package org.velvet.entity.girl.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import org.velvet.entity.girl.AbstractGirlEntity;
import org.velvet.entity.girl.GirlLoot;

/**
 * Breaking and placing blocks the way a player does: within reach, only what she can see, at the speed her
 * tool allows (the player's formula: a tool that fits breaks it in hardness × 1.5 seconds divided by its
 * speed, the wrong one takes more than three times as long), and what drops goes into her inventory. Both
 * need the mobGriefing rule.
 */
public final class BlockWork {

    /** A player's reach. */
    public static final double REACH = 4.5;

    private final AbstractGirlEntity girl;
    private BlockPos breaking = BlockPos.ZERO;
    private float progress;
    private int ticks;

    public BlockWork(AbstractGirlEntity girl) {
        this.girl = girl;
    }

    public static boolean mayGrief(AbstractGirlEntity girl) {
        return EventHooks.canEntityGrief(girl.level(), girl);
    }

    /** She can reach it and sees it (nothing else in the way to some side of it). */
    public static boolean inReach(AbstractGirlEntity girl, BlockPos pos) {
        return girl.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH && visible(girl, pos);
    }

    /** The block shows: looking from her eyes, the first thing hit is this block. */
    public static boolean visible(AbstractGirlEntity girl, BlockPos pos) {
        Vec3 eye = girl.getEyePosition();
        Vec3 centre = Vec3.atCenterOf(pos);
        BlockHitResult hit = girl.level().clip(new ClipContext(eye, centre, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, girl));
        if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) return true;
        // the centre may be hidden by a neighbour while a face isn't: try the faces towards her
        for (Direction d : Direction.values()) {
            Vec3 face = centre.add(d.getStepX() * 0.49, d.getStepY() * 0.49, d.getStepZ() * 0.49);
            if (face.subtract(eye).dot(Vec3.atLowerCornerOf(d.getNormal())) > 0) continue;
            hit = girl.level().clip(new ClipContext(eye, face, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, girl));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }

    // ---- Breaking ----

    /**
     * One tick of breaking {@code pos} with what's in her hand; true once it's broken (its drops are in her
     * inventory then, or on the ground if there's no room).
     */
    public boolean breakStep(BlockPos pos) {
        Level level = girl.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!pos.equals(breaking)) {
            reset();
            breaking = pos.immutable();
        }
        girl.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return false; // bedrock and the like
        ItemStack tool = girl.getMainHandItem();
        boolean fits = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float speed = Math.max(1, tool.getDestroySpeed(state));
        if (girl.isInWater()) speed /= 5;
        if (!girl.onGround()) speed /= 5;
        progress += hardness == 0 ? 1 : speed / hardness / (fits ? 30 : 100);
        if (++ticks % 4 == 0) {
            girl.swing(InteractionHand.MAIN_HAND);
            var sound = state.getSoundType(level, pos, girl);
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 8, sound.getPitch() * 0.5F);
        }
        level.destroyBlockProgress(girl.getId(), pos, Math.min(9, (int) (progress * 10)));
        if (progress < 1) return false;
        destroy(pos, state, fits);
        reset();
        return true;
    }

    private void destroy(BlockPos pos, BlockState state, boolean fits) {
        Level level = girl.level();
        ItemStack tool = girl.getMainHandItem();
        if (level instanceof ServerLevel server && fits) {
            for (ItemStack drop : Block.getDrops(state, server, pos, level.getBlockEntity(pos), girl, tool)) {
                ItemStack rest = GirlLoot.addToBag(girl, drop);
                if (!rest.isEmpty()) Block.popResource(level, pos, rest);
            }
            state.spawnAfterBreak(server, pos, tool, true);
        }
        level.destroyBlock(pos, false, girl);
        level.gameEvent(girl, GameEvent.BLOCK_DESTROY, pos);
        if (fits) girl.addSkillXp(org.velvet.entity.girl.GirlSkill.TOOLS, 1);
        if (!tool.isEmpty() && state.getDestroySpeed(level, pos) != 0) tool.hurtAndBreak(1, girl, EquipmentSlot.MAINHAND);
    }

    public void reset() {
        if (!breaking.equals(BlockPos.ZERO)) girl.level().destroyBlockProgress(girl.getId(), breaking, -1);
        breaking = BlockPos.ZERO;
        progress = 0;
        ticks = 0;
    }

    // ---- Placing ----

    /** Room for a block there: nothing solid in it and no creature standing in the way. */
    public static boolean canPlaceAt(Level level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos).canBeReplaced() && level.isUnobstructed(state, pos, CollisionContext.empty());
    }

    /** Places {@code block} from her inventory (already taken out of it) at {@code pos}; false if it doesn't fit. */
    public boolean place(BlockPos pos, Block block) {
        Level level = girl.level();
        BlockState state = block.defaultBlockState();
        if (!canPlaceAt(level, pos, state)) return false;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var sound = state.getSoundType(level, pos, girl);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
        level.gameEvent(girl, GameEvent.BLOCK_PLACE, pos);
        girl.swing(InteractionHand.MAIN_HAND);
        girl.addSkillXp(org.velvet.entity.girl.GirlSkill.BUILDING, 1);
        girl.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return true;
    }

    // ---- What's worth taking ----

    /**
     * Natural ground she may take for building: dirt, stone and the like (no ores, nothing a player put
     * together, nothing that falls or has contents), and only where taking it doesn't open lava or water or
     * pull the floor from under someone.
     */
    public static boolean isBuildingMaterial(Level level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity() || state.getBlock() instanceof FallingBlock || state.is(Tags.Blocks.ORES)) return false;
        boolean natural = state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Tags.Blocks.SANDSTONE_BLOCKS) || state.is(net.minecraft.world.level.block.Blocks.END_STONE)
                || state.is(BlockTags.TERRACOTTA);
        if (!natural || !state.isCollisionShapeFullBlock(level, pos)) return false;
        return safeToRemove(level, pos);
    }

    /** Taking it out lets no liquid in and doesn't leave anyone without ground under their feet. */
    public static boolean safeToRemove(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            if (!level.getFluidState(pos.relative(d)).isEmpty()) return false;
        }
        return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos.above()).inflate(0.3, 0, 0.3)).isEmpty();
    }
}
