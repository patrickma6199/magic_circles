package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.FerrymanEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the dead from touching the living world.
 *
 * <p>Most of this only ever fires for rite casters. An ordinary ghost is a real spectator (see
 * {@link LimboRegistry#ghostifyPlayer}) and vanilla already stops them from breaking, placing,
 * picking up or hitting anything - but a rite caster keeps a living body, because they have to be
 * able to fight the phantoms hunting them, so every restriction spectator mode would have given
 * for free has to be spelled out here instead. It stays keyed on "behind the veil" rather than on
 * which of the two you are, so neither can ever slip through.
 *
 * <p>Two things are deliberately still allowed: right-clicking the Ferryman, which is the entire
 * way home, and swinging at something that is also behind the veil, which is what the rite caster
 * brought a weapon for. Neither reaches the living world.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GhostRules
{
    private GhostRules()
    {
    }

    private static boolean isDead(Player player)
    {
        return LimboState.isInLimbo(player);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (isDead(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (isDead(event.getPlayer()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getEntity() instanceof Player player && isDead(player))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (isDead(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (isDead(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        if (isDead(event.getEntity()) && !(event.getTarget() instanceof FerrymanEntity))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)
    {
        if (isDead(event.getEntity()) && !(event.getTarget() instanceof FerrymanEntity))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event)
    {
        // The dead can fight each other and the things hunting them; they cannot reach back
        // through the veil and strike something alive.
        if (isDead(event.getEntity()) && !Veil.isBehindVeil(event.getTarget()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event)
    {
        if (isDead(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event)
    {
        if (isDead(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event)
    {
        if (isDead(event.getPlayer()))
        {
            event.setCanceled(true);
        }
    }
}
