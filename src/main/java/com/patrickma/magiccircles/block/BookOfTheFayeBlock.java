package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.block.entity.BookOfTheFayeBlockEntity;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Book of the Faye, placed - see {@code item/ModItems#BOOK_OF_THE_FAYE}'s own doc comment for
 * why the *held* item looks closed while this, the placed form, floats open with a single
 * stationary wisp above its pages ({@code client/BookOfTheFayeWisp}). "You have to place it and
 * use it to read it" - right-clicking the held item places this block (ordinary {@code BlockItem}
 * behavior - see {@code ModItems}), and right-clicking *this* block is what actually opens the
 * reading screen (see {@link #use}).
 *
 * <p>Same invisible-block-plus-renderer shape {@code HeartCoreBlock} already uses for its own
 * floating heart: {@link RenderShape#INVISIBLE} (everything visible comes from {@code
 * client/BookOfTheFayeBlockEntityRenderer}), empty collision (it's a small floating object, not
 * an obstacle), and breaking it drops the closed-book item back rather than nothing.
 */
public class BookOfTheFayeBlock extends BaseEntityBlock
{
    /** Which way the book faces - previously nonexistent, which is why it always rendered facing the same fixed direction no matter which way it was placed (see {@code client/BookOfTheFayeBlockEntityRenderer}, which now reads this to actually rotate the model). */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 6, 13);

    public BookOfTheFayeBlock(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new BookOfTheFayeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        // Client-only - nothing here needs to run server-side at all (see BookOfTheFayeBlockEntity's own doc comment).
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.BOOK_OF_THE_FAYE.get(), BookOfTheFayeBlockEntity::clientTick)
                : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (level.isClientSide)
        {
            // Delegates entirely to BookOfTheFayeScreenOpener - this class (loaded on a dedicated
            // server too, since it's a registered Block) must never itself reference
            // Minecraft/BookViewScreen directly - see that class's own doc comment.
            com.patrickma.magiccircles.client.BookOfTheFayeScreenOpener.open();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid)
    {
        if (!level.isClientSide && willHarvest)
        {
            popResource(level, pos, new ItemStack(ModItems.BOOK_OF_THE_FAYE.get()));
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.levelEvent(player, 2001, pos, Block.getId(state));
        return false;
    }
}
