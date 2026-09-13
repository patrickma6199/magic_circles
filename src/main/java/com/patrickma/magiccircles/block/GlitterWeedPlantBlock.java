package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModFluids;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** The body segments below {@link GlitterWeedBlock}'s own growing tip - see that class's own doc comment for why {@link #getFluidState} is overridden the same way. */
public class GlitterWeedPlantBlock extends KelpPlantBlock
{
    public GlitterWeedPlantBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock()
    {
        return (GrowingPlantHeadBlock) ModBlocks.GLITTER_WEED.get();
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return ModFluids.WELLSPRING_WATER.get().getSource(false);
    }
}
