package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The source/flowing pair backing {@code FairyPortalWaterBlock} - see {@link ModFluidTypes} for
 * what makes it behave like water at all, and that class's doc comment for why this needs to be
 * a real, distinct fluid rather than reusing vanilla water.
 */
public class ModFluids
{
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, MagicCircles.MOD_ID);

    public static final RegistryObject<FlowingFluid> PORTAL_WATER = FLUIDS.register("portal_water",
            () -> new ForgeFlowingFluid.Source(portalWaterProperties()));

    public static final RegistryObject<FlowingFluid> PORTAL_WATER_FLOWING = FLUIDS.register("flowing_portal_water",
            () -> new ForgeFlowingFluid.Flowing(portalWaterProperties()));

    public static final RegistryObject<FlowingFluid> WELLSPRING_WATER = FLUIDS.register("wellspring_water",
            () -> new ForgeFlowingFluid.Source(wellspringWaterProperties()));

    public static final RegistryObject<FlowingFluid> WELLSPRING_WATER_FLOWING = FLUIDS.register("flowing_wellspring_water",
            () -> new ForgeFlowingFluid.Flowing(wellspringWaterProperties()));

    // Every supplier here is a lambda, not a method reference, deliberately - ModBlocks and
    // ModFluids each reference a field declared in the other, and only wrapping those
    // references in lambdas (evaluated lazily, well after both classes finish their own static
    // init) avoids a circular-initialization null read.
    private static ForgeFlowingFluid.Properties portalWaterProperties()
    {
        return new ForgeFlowingFluid.Properties(
                () -> ModFluidTypes.PORTAL_WATER.get(),
                () -> PORTAL_WATER.get(),
                () -> PORTAL_WATER_FLOWING.get())
                .block(() -> ModBlocks.FAIRY_PORTAL_WATER.get())
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .explosionResistance(100.0f)
                .tickRate(5);
    }

    private static ForgeFlowingFluid.Properties wellspringWaterProperties()
    {
        return new ForgeFlowingFluid.Properties(
                () -> ModFluidTypes.WELLSPRING_WATER.get(),
                () -> WELLSPRING_WATER.get(),
                () -> WELLSPRING_WATER_FLOWING.get())
                .block(() -> ModBlocks.WELLSPRING_WATER.get())
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .explosionResistance(100.0f)
                .tickRate(5);
    }

    private ModFluids()
    {
    }
}
