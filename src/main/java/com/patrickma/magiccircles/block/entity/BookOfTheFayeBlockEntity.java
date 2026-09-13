package com.patrickma.magiccircles.block.entity;

import com.patrickma.magiccircles.client.BookOfTheFayeWisp;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Just an age counter for {@code client/BookOfTheFayeBlockEntityRenderer}'s own gentle page-flip
 * animation, plus driving the single stationary wisp {@code client/BookOfTheFayeWisp} draws above
 * the open pages - nothing here needs saving, and nothing runs server-side at all (see {@code
 * block/BookOfTheFayeBlock#getTicker}, which only ever registers the client tick).
 */
public class BookOfTheFayeBlockEntity extends BlockEntity
{
    private int age;

    public BookOfTheFayeBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.BOOK_OF_THE_FAYE.get(), pos, state);
    }

    public int getAge()
    {
        return age;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BookOfTheFayeBlockEntity blockEntity)
    {
        blockEntity.age++;
        BookOfTheFayeWisp.tick(level, pos);
    }
}
