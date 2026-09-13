package com.patrickma.magiccircles.item;

import com.patrickma.magiccircles.FairyPortalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Right-click to be pulled straight to the Fairy Realm's one shared portal (see
 * {@link FairyPortalManager#useWaystone}) - a one-way trip, and the stone itself is spent the
 * moment it lands you there (unless you're in creative, which never spends anything). There's no
 * way to craft or find one yet; for now it only exists in the creative inventory.
 *
 * <p>Getting back is the same drowning-in-the-portal-water crossing every other portal uses -
 * this item only ever gets you there, never back, which is exactly why it's consumed on arrival
 * rather than on use: a waystone that failed to actually open a destination (Fairy Realm not
 * loaded yet, say) shouldn't disappear for nothing.
 */
public class LostWaystoneItem extends Item
{
    public LostWaystoneItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResultHolder.success(stack);
        }

        boolean traveled = FairyPortalManager.useWaystone(serverPlayer);
        if (traveled && !serverPlayer.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        return traveled ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }
}
