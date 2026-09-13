package com.patrickma.magiccircles.recipe;

import com.google.gson.JsonObject;
import com.patrickma.magiccircles.item.ChalkItem;
import com.patrickma.magiccircles.registry.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;

/**
 * Tinting a piece of chalk without wasting what's left of it.
 *
 * <p>A plain shapeless recipe always hands back a brand-new item, so dyeing a nearly-spent piece of
 * chalk used to silently refill it - and dyeing a fresh one you'd already drawn with quietly lost
 * nothing, which made the durability meaningless either way. This is an ordinary shapeless recipe
 * in every respect except that it carries the damage value across from whichever ingredient was
 * chalk, so a half-used piece stays half-used whatever color it ends up.
 */
public class ChalkTintRecipe extends ShapelessRecipe
{
    public ChalkTintRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                           ItemStack result, NonNullList<Ingredient> ingredients)
    {
        super(id, group, category, result, ingredients);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries)
    {
        ItemStack result = super.assemble(container, registries);
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack ingredient = container.getItem(slot);
            if (ingredient.getItem() instanceof ChalkItem && ingredient.isDamageableItem())
            {
                result.setDamageValue(ingredient.getDamageValue());
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return ModRecipeSerializers.CHALK_TINT.get();
    }

    /** Defers entirely to vanilla's shapeless parsing - only the resulting recipe class differs. */
    public static class Serializer implements RecipeSerializer<ChalkTintRecipe>
    {
        private final RecipeSerializer<ShapelessRecipe> parent = RecipeSerializer.SHAPELESS_RECIPE;

        @Override
        public ChalkTintRecipe fromJson(ResourceLocation id, JsonObject json)
        {
            return upgrade(parent.fromJson(id, json));
        }

        @Override
        public ChalkTintRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            ShapelessRecipe base = parent.fromNetwork(id, buffer);
            return base == null ? null : upgrade(base);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ChalkTintRecipe recipe)
        {
            parent.toNetwork(buffer, recipe);
        }

        private static ChalkTintRecipe upgrade(ShapelessRecipe base)
        {
            return new ChalkTintRecipe(base.getId(), base.getGroup(), base.category(),
                    base.getResultItem(RegistryAccess.EMPTY), base.getIngredients());
        }
    }
}
