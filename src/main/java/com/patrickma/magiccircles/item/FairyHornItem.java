package com.patrickma.magiccircles.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/**
 * The wand used to interact with a Heart Core - see {@code HeartCoreBlock#interact} for
 * everything it actually triggers (casting a spell, or - while sneaking - just reporting the
 * heart's mana).
 *
 * <p>That sneak-to-query behavior needed this dedicated class: vanilla's own
 * {@code ServerPlayerGameMode#useItemOn} skips a block's {@code use()} entirely whenever the
 * player is sneaking *while holding a non-empty item* - a deliberate vanilla mechanic (it's why
 * sneak-right-clicking a chest with something in hand lets you place a block against it instead
 * of opening the chest), gated by {@code Item#doesSneakBypassUse} - default {@code false} for a
 * plain {@link Item}, which is exactly what silently swallowed every sneak-right-click on a
 * Heart Core before this class existed: {@code HeartCoreBlock#interact} was never even reached,
 * so its shift-click mana message could never have shown up no matter how correct the block-side
 * code was. Overriding this to {@code true} tells vanilla the Fairy Horn has no sneak-specific
 * behavior of its own, so sneaking should still let the block handle the click normally.
 */
public class FairyHornItem extends Item
{
    public FairyHornItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player)
    {
        return true;
    }
}
