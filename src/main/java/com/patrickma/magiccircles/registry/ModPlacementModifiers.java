package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.worldgen.FairyLandmarkExclusionFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registers {@link FairyLandmarkExclusionFilter}'s type - same reasoning as {@link ModTreeDecorators}: the vanilla registry is frozen before mods run, so this goes through a {@code DeferredRegister} targeting it instead of a raw registration call. */
public class ModPlacementModifiers
{
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MagicCircles.MOD_ID);

    public static final RegistryObject<PlacementModifierType<FairyLandmarkExclusionFilter>> FAIRY_LANDMARK_EXCLUSION =
            PLACEMENT_MODIFIER_TYPES.register("fairy_landmark_exclusion", () -> () -> FairyLandmarkExclusionFilter.CODEC);
}
