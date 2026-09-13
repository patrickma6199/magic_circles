package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModFluids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A reskinned Kelp (see {@code tools/gen_glitter_weed_texture.py}) grown through {@code
 * worldgen/WellspringOcean}'s own seabed - the growing tip, same as vanilla {@code Blocks.KELP} is
 * to {@code Blocks.KELP_PLANT} (see {@link GlitterWeedPlantBlock}, the body segments below).
 *
 * <p>Only {@link #canGrowInto}/{@link #getFluidState} are overridden from vanilla's own {@link
 * KelpBlock} - both are hardcoded there to {@code Blocks.WATER}/{@code Fluids.WATER} specifically
 * (confirmed by decompiling it), which would otherwise mean this can never actually grow while
 * standing in Wellspring Water (a distinct {@code FluidType}/{@code Fluid}, not vanilla water) and
 * would misreport its own submerged fluid as vanilla water rather than Wellspring Water. Growth,
 * survival, and breaking behavior are all otherwise identical to real Kelp - capped in practice by
 * {@code GlitterWeedSacBlock} sitting solidly on top (a growing tip can't extend into a solid
 * block), the same way {@code WellspringOcean} places a Sac after growing each patch to a set
 * height, rather than needing any code here to stop growth explicitly.
 */
public class GlitterWeedBlock extends KelpBlock
{
    public GlitterWeedBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    protected boolean canGrowInto(BlockState state)
    {
        return state.is(Blocks.WATER) || state.is(ModBlocks.WELLSPRING_WATER.get());
    }

    @Override
    protected Block getBodyBlock()
    {
        return ModBlocks.GLITTER_WEED_PLANT.get();
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return ModFluids.WELLSPRING_WATER.get().getSource(false);
    }
}
