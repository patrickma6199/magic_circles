package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.worldgen.ShroomlightBranchDecorator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers {@link ShroomlightBranchDecorator}'s type - same reasoning as {@link ModWorldgen}'s
 * own chunk generator codec: {@code BuiltInRegistries.TREE_DECORATOR_TYPE} is frozen before mods
 * run, so this goes through a {@code DeferredRegister} targeting the vanilla registry key rather
 * than a raw registration call.
 */
public class ModTreeDecorators
{
    public static final DeferredRegister<TreeDecoratorType<?>> TREE_DECORATOR_TYPES =
            DeferredRegister.create(Registries.TREE_DECORATOR_TYPE, MagicCircles.MOD_ID);

    public static final RegistryObject<TreeDecoratorType<ShroomlightBranchDecorator>> SHROOMLIGHT_BRANCH =
            TREE_DECORATOR_TYPES.register("shroomlight_branch", () -> new TreeDecoratorType<>(ShroomlightBranchDecorator.CODEC));
}
