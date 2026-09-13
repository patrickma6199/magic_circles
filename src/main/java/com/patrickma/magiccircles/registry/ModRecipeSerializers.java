package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.recipe.ChalkTintRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Recipe serializers the mod adds - see {@link ChalkTintRecipe} for why chalk needs one of its own. */
public final class ModRecipeSerializers
{
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MagicCircles.MOD_ID);

    public static final RegistryObject<RecipeSerializer<ChalkTintRecipe>> CHALK_TINT =
            RECIPE_SERIALIZERS.register("chalk_tint", ChalkTintRecipe.Serializer::new);

    private ModRecipeSerializers()
    {
    }
}
