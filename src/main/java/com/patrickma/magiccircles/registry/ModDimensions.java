package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Keys for the dimensions this mod adds - see data/magiccircles/dimension for the actual world definitions. */
public final class ModDimensions
{
    public static final ResourceKey<Level> FAIRY_REALM =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(MagicCircles.MOD_ID, "fairy_realm"));

    private ModDimensions()
    {
    }
}
