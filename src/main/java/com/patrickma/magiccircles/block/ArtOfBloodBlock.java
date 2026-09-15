package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.block.entity.ArtOfBloodBlockEntity;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Art of Blood, resting shut.
 *
 * <p>Unlike the Faye's own book this one does not fall open for anyone who touches it. It stays
 * closed until it is cut: right-click it with an athame and it takes a heart of yours before the
 * pages will part. Anything else - a bare hand, any other item - does nothing at all.
 *
 * <p>That price is paid every single time it is read, not once. The book is not locked; it is
 * simply hungry.
 */
public class ArtOfBloodBlock extends BaseEntityBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 3, 13);
    /** One heart, every time it is opened. */
    private static final float TOLL = 2.0f;

    public ArtOfBloodBlock(Properties properties)
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
        return new ArtOfBloodBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.ART_OF_BLOOD.get(), ArtOfBloodBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.ART_OF_BLOOD.get(),
                        com.patrickma.magiccircles.block.entity.ReadableBookBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!player.getItemInHand(hand).is(ModItems.ATHAME.get()))
        {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide)
        {
            player.hurt(level.damageSources().magic(), TOLL);
            level.playSound(null, pos, SoundEvents.PLAYER_HURT, SoundSource.BLOCKS, 0.7f, 0.6f);
            if (level.getBlockEntity(pos) instanceof ArtOfBloodBlockEntity book)
            {
                book.startReading(player);
            }
            return InteractionResult.CONSUME;
        }

        // Client side only - see BookOfTheFayeScreenOpener for why this can never be referenced
        // directly from a class the dedicated server also loads.
        com.patrickma.magiccircles.client.BookOfTheFayeScreenOpener.openArtOfBlood(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid)
    {
        if (!level.isClientSide && willHarvest)
        {
            popResource(level, pos, new ItemStack(ModItems.ART_OF_BLOOD.get()));
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.levelEvent(player, 2001, pos, Block.getId(state));
        return false;
    }
}
