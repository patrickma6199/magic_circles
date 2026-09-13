package com.patrickma.magiccircles.worldgen;

import com.mojang.serialization.Codec;
import com.patrickma.magiccircles.registry.ModTreeDecorators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces every Living Wood Tree log that sits at the end of a branch (or the main trunk's own
 * crown) with a Shroomlight, unconditionally - always, not a chance roll, per how the tree was
 * asked for. "End of a branch" is found generically rather than by walking the trunk
 * placer's own branch list (which {@link net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator.Context}
 * never exposes to a decorator anyway - only the flattened set of log positions actually placed):
 * a log position with at most one other log among its 26 neighbors is a dead end in the log
 * "graph" - true for any branch tip, and for the main trunk's own topmost log once it stops
 * having a log directly above it. The trunk's *base* (the single lowest log, always sitting on
 * the ground with a real neighbor above it but none below) is excluded explicitly - it can end
 * up with exactly one neighbor too, but replacing the tree's own roots with a floating light
 * source isn't what "the top of a branch" means.
 */
public class ShroomlightBranchDecorator extends TreeDecorator
{
    public static final Codec<ShroomlightBranchDecorator> CODEC = Codec.unit(ShroomlightBranchDecorator::new);

    @Override
    protected TreeDecoratorType<?> type()
    {
        return ModTreeDecorators.SHROOMLIGHT_BRANCH.get();
    }

    @Override
    public void place(Context context)
    {
        List<BlockPos> logs = context.logs();
        if (logs.isEmpty())
        {
            return;
        }
        Set<BlockPos> logSet = new HashSet<>(logs);
        // Context's own constructor sorts both lists ascending by Y - the first entry is always
        // the trunk's real base.
        int baseY = logs.get(0).getY();

        for (BlockPos pos : logs)
        {
            if (pos.getY() == baseY)
            {
                continue;
            }
            int neighbors = 0;
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        if (dx == 0 && dy == 0 && dz == 0)
                        {
                            continue;
                        }
                        if (logSet.contains(pos.offset(dx, dy, dz)))
                        {
                            neighbors++;
                        }
                    }
                }
            }
            if (neighbors <= 1)
            {
                context.setBlock(pos, Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
    }
}
