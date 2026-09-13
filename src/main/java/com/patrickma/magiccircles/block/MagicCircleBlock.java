package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.block.entity.MagicCircleBlockEntity;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A rune drawn in chalk. Flat like redstone dust - a sliver-thin outline shape for
 * selection/highlighting, and no collision at all so it's simply part of the floor to
 * walk on - plus a {@link MagicCircleBlockEntity} that animates a particle drifting up
 * from the rune's tip every tick.
 *
 * <p>Each placement picks one of {@value #VARIANT_COUNT} different rune symbols at random
 * (see {@link #VARIANT} and the {@code magic_circle_0.json}..{@code magic_circle_19.json}
 * models/textures) purely for visual variety - the symbol has no gameplay effect. Separately,
 * {@link #COLOR} tracks which chalk drew it (see {@link RuneColor}) - unlike the symbol, this
 * one really matters: it decides the color of that rune's wisp and, once a ring is a uniform
 * color, which spell a Heart Core built from it can cast.
 */
public class MagicCircleBlock extends BaseEntityBlock
{
    public static final int VARIANT_COUNT = 20;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANT_COUNT - 1);
    public static final EnumProperty<RuneColor> COLOR = EnumProperty.create("color", RuneColor.class);

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 1, 16);

    public MagicCircleBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(VARIANT, COLOR);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState()
                .setValue(VARIANT, context.getLevel().getRandom().nextInt(VARIANT_COUNT))
                .setValue(COLOR, RuneColor.BLUE);
    }

    /**
     * A rune needs real solid ground underneath it - without this, nothing stopped one from
     * being drawn floating in midair, or (since a rune itself isn't a solid, sturdy block face)
     * directly on top of *another* rune, since the default placement pipeline
     * ({@code BlockItem#getPlacementState}, which {@link com.patrickma.magiccircles.item.ChalkItem}
     * defers to) already rejects any placement whose target state fails {@code canSurvive} - this
     * one override is what makes both of those cases fail on their own, with no extra checks
     * needed anywhere else.
     */
    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos)
    {
        BlockPos belowPos = pos.below();
        return level.getBlockState(belowPos).isFaceSturdy(level, belowPos, Direction.UP);
    }

    /**
     * The other half of {@link #canSurvive}: actually acting on it. Vanilla's default {@code
     * updateShape} never re-checks {@code canSurvive} on its own (that's opt-in per block, the
     * same pattern {@code BushBlock}/{@code DoorBlock}/etc. use) - without this override, breaking
     * the block a rune sits on left the rune floating there untouched instead of breaking with it.
     * {@code false} for the drop flag: there's no item that hands a rune back (chalk is a drawing
     * tool with its own durability, not a placeable block item pointing at this block), so this
     * just removes it the same way vanilla removes a torch that loses its support, minus the "pop
     * off as a dropped item" part that only applies to blocks that actually have one.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving)
    {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (neighborPos.equals(pos.below()) && !state.canSurvive(level, pos))
        {
            level.destroyBlock(pos, false);
        }
    }

    /**
     * Every chalk color is a separate {@code BlockItem} wrapping this same block, so Forge's
     * block-to-item lookup (used for creative-mode pick-block) only remembers whichever one
     * was constructed last - without this override, pick-blocking *any* rune would always
     * hand over whichever chalk happened to register last. This makes pick-block match
     * whatever color was actually drawn instead.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player)
    {
        return new ItemStack(chalkItemFor(state.getValue(COLOR)));
    }

    private static Item chalkItemFor(RuneColor color)
    {
        return switch (color)
        {
            case GOLD -> ModItems.GOLD_CHALK.get();
            case PURPLE -> ModItems.PURPLE_CHALK.get();
            case RED -> ModItems.RED_CHALK.get();
            case GREEN -> ModItems.GREEN_CHALK.get();
            default -> ModItems.CHALK.get();
        };
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
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new MagicCircleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        // Client-only cosmetics now - see MagicCircleBlockEntity's doc comment for where the
        // server-side portal check that used to live here moved to.
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.MAGIC_CIRCLE.get(), MagicCircleBlockEntity::clientTick)
                : null;
    }
}
