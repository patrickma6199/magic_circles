package com.patrickma.magiccircles.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A companion to {@link HeartCoreBlock}, placed one cell above it, that exists purely to give
 * the *floating heart itself* something to click or break - Minecraft can't give a block a
 * hitbox that reaches into a neighboring block position, and the heart floats a block and a
 * half above its real block, mostly inside this cell. Every interaction just forwards straight
 * back to {@link HeartCoreBlock#interact}/{@link HeartCoreBlock#destroy} on the position below.
 * It's invisible and has no block entity of its own - all real state lives on the block below.
 */
public class HeartCoreTopBlock extends Block
{
    private static final VoxelShape SHAPE = box(2, 1, 2, 14, 15, 14);

    public HeartCoreTopBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        return HeartCoreBlock.interact(level, pos.below(), player, hand);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid)
    {
        BlockPos heartPos = pos.below();
        HeartCoreBlock.destroy(level, heartPos, level.getBlockState(heartPos), player, willHarvest);
        return false;
    }
}
