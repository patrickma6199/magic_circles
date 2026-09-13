package com.patrickma.magiccircles.block;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * The water filling the Wellspring, at the base of the World Tree (see
 * {@code worldgen/WorldTree.java}'s well) - a real, distinct fluid for the same reason
 * {@link FairyPortalWaterBlock} is (its own animated, color-cycling texture, and avoiding the
 * risk of a shared vanilla fluid reverting this back to plain water on its own). Unlike the
 * portal water, this one has no special server-side behavior of its own - it's a purely
 * decorative/thematic pool ("this water holds mana," not "step in and something happens"), so
 * there's nothing here beyond the plain {@link LiquidBlock} constructor.
 */
public class WellspringWaterBlock extends LiquidBlock
{
    public WellspringWaterBlock(Supplier<? extends FlowingFluid> fluid, Properties properties)
    {
        super(fluid, properties);
    }
}
