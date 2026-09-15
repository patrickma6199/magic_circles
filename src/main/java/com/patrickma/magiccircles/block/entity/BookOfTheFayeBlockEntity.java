package com.patrickma.magiccircles.block.entity;

import com.patrickma.magiccircles.client.BookOfTheFayeWisp;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The placed Book of the Faye. Rests shut at a Lectern's angle, and opens up toward whoever is
 * reading it ({@link ReadableBookBlockEntity}). Also drives the single stationary wisp {@code
 * client/BookOfTheFayeWisp} draws above it, which rises along with the book when it lifts.
 */
public class BookOfTheFayeBlockEntity extends ReadableBookBlockEntity
{
    private static final double RESTING_HEIGHT = 0.45;
    /** A Lectern's own resting angle - see {@code client/BookOfTheFayeBlockEntityRenderer}. */
    private static final float RESTING_TILT = 67.5F;

    public BookOfTheFayeBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.BOOK_OF_THE_FAYE.get(), pos, state);
    }

    @Override
    public float restingTilt()
    {
        return RESTING_TILT;
    }

    @Override
    public double restingHeight()
    {
        return RESTING_HEIGHT;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BookOfTheFayeBlockEntity blockEntity)
    {
        blockEntity.tickClient(level);
        BookOfTheFayeWisp.tick(level, pos, blockEntity.lift(1.0F));
    }
}
