package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.limbo.GhostVisibility;
import com.patrickma.magiccircles.limbo.Veil;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Deathsight - the red, green and black split ring (see {@link TriColourRite#DEATHSIGHT}): looking
 * across the veil without stepping over it.
 *
 * <p>Seeing is two separate things, and this handles both. Whether the entities behind the veil are
 * sent to the caster's client at all is decided in {@code limbo/Veil#visibleTo}, which the tracking
 * mixin consults. Whether they then render - rather than being received but drawn invisible - is
 * the veil team, which is why the caster is kept on it for exactly as long as the sight lasts. The
 * caster is not behind the veil, so nothing else about them changes: they don't fly, and they
 * stay wholly in the living world.
 *
 * <p>Neither side can touch the other. The dead are already kept from reaching the living ({@code
 * limbo/GhostRules}); this adds the mirror of that, which never mattered before because until now
 * the living could never see anything behind the veil to click on.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Deathsight
{
    private Deathsight()
    {
    }

    public static boolean has(Player player)
    {
        return player.hasEffect(ModEffects.DEATHSIGHT.get());
    }

    /** Keeps the caster on the veil team while the sight lasts, and takes them off again after. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide)
        {
            return;
        }
        Player player = event.player;
        boolean sees = has(player);
        boolean onTeam = GhostVisibility.isOnVeilTeam(player);
        if (sees && !onTeam)
        {
            GhostVisibility.join(player);
        }
        else if (!sees && onTeam && !Veil.isBehindVeil(player))
        {
            GhostVisibility.leave(player);
        }
    }

    /** The living reaching for something that is on the other side. */
    private static boolean reachesAcross(Player actor, Entity target)
    {
        return !Veil.isBehindVeil(actor) && Veil.isBehindVeil(target);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event)
    {
        if (reachesAcross(event.getEntity(), event.getTarget()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event)
    {
        if (reachesAcross(event.getEntity(), event.getTarget()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)
    {
        if (reachesAcross(event.getEntity(), event.getTarget()))
        {
            event.setCanceled(true);
        }
    }
}
