package com.patrickma.magiccircles.block;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * What the water-filled pit around a Heart Core turns into once "Zuzo's Crossing" (the portal
 * spell - see {@code HeartCoreBlockEntity#openWaterPortal}) is cast - see
 * {@code FairyPortalManager} for the full mechanic.
 *
 * <p>A real, distinct {@link FlowingFluid} (see {@code ModFluids}) backs this block rather than
 * vanilla's own water - sharing {@code Fluids.WATER} between two different blocks risks the
 * fluid engine's own flow/update logic silently reverting this back to plain water on its own
 * schedule (its {@code createLegacyBlock()} always resolves to vanilla's block), which would
 * make "when it closes" something this mod couldn't actually control. Being a real fluid rather
 * than a fake non-solid block (an earlier version) is what gives this real swim physics and the
 * vanilla air-bubble HUD - both are driven generically by {@code FluidType}
 * (see {@code ModFluidTypes}), not hardcoded to water specifically, so a fluid with its own
 * distinct texture still gets full "acts like water" behavior for free. The actual "don't really
 * drown, get teleported instead" part is handled separately - see
 * {@code FairyPortalManager}'s {@code LivingDrownEvent} listener.
 *
 * <p>Splash/swim sounds and the underwater screen tint/ambient hum aren't free (see
 * {@code WaterLikeFluidSounds}/{@code client/WaterLikeAmbientSounds}/{@code ModFluidTypes} for
 * why and how each is reproduced by hand) - none of that lives on this class itself any more,
 * since {@link com.patrickma.magiccircles.registry.ModFluidTypes#WELLSPRING_WATER} needed the
 * exact same treatment and duplicating a whole block class just to share it made far less sense
 * than pulling the shared parts into their own classes once there were two fluids that needed
 * them.
 */
public class FairyPortalWaterBlock extends LiquidBlock
{
    public FairyPortalWaterBlock(Supplier<? extends FlowingFluid> fluid, Properties properties)
    {
        super(fluid, properties);
    }

    /** Always ticking, so a leftover block always gets its chance to go back to water - see {@link #randomTick}. */
    @Override
    public boolean isRandomlyTicking(net.minecraft.world.level.block.state.BlockState state)
    {
        return true;
    }

    /**
     * Portal fluid no open portal owns - left behind when the server forgot its portals on restart -
     * quietly becomes the plain water it was before the portal opened.
     */
    @Override
    public void randomTick(net.minecraft.world.level.block.state.BlockState state, net.minecraft.server.level.ServerLevel level,
                           net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random)
    {
        if (!com.patrickma.magiccircles.FairyPortalManager.isLivePortalWater(level, pos))
        {
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        }
    }
}
