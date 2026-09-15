package com.patrickma.magiccircles.block;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A log (or a block of wood) an axe can strip, the way it strips any vanilla log - vanilla keeps its
 * own log-to-stripped table private, so a modded log answers Forge's tool hook instead. Keeps the
 * way the log was lying.
 */
public class StrippableLogBlock extends RotatedPillarBlock
{
    private final Supplier<? extends Block> stripped;

    public StrippableLogBlock(Supplier<? extends Block> stripped, Properties properties)
    {
        super(properties);
        this.stripped = stripped;
    }

    @Nullable
    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate)
    {
        if (toolAction == ToolActions.AXE_STRIP)
        {
            return this.stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }
}
