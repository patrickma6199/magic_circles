package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Free flight for anyone Blessed by the Wellspring (see {@code
 * registry/ModEffects#BLESSED_BY_WELLSPRING}) - toggled on/off by double-tapping jump, the exact
 * same physical gesture Creative mode's own flight toggle uses (see {@code
 * client/FairyFlightInput}, which detects the double-tap and sends {@code
 * network/ToggleFairyFlightPacket} here), not something that just automatically engages the
 * moment you're airborne.
 *
 * <p>{@link #toggleFairyFlight} is the server-authoritative half - re-checks Blessed/Creative/
 * Spectator itself rather than trusting the packet alone (a client could in principle send this
 * unprompted; toggling your own flight on/off isn't dangerous, but there's no reason to honor it
 * outside the one case it's meant for). Left alone entirely for Creative/Spectator players -
 * they already have real flight permanently, on their own terms.
 *
 * <p>{@link #onPlayerTick} is the ongoing per-tick half: turns fairy-flight back off the instant
 * Blessed lapses (mid-flight or not - the same "ability genuinely ends when the buff runs out"
 * rule every other Blessed-gated thing in this mod already follows), and - while actually
 * airborne and flying - forces the swimming pose ({@link Player#setSwimming}) so the body
 * animates with real swimming's own arm-stroke, not vanilla's flat creative-flight standing pose.
 * {@code client/FairyWingsLayer} is what actually opens the wings for this - it keys off exactly
 * this same {@code isSwimming() && !isInWater()} combination (a synced flag, so every observing
 * client sees the same thing), not anything additional synced from here.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyFlightManager
{
    private FairyFlightManager()
    {
    }

    /** Called from {@code network/ToggleFairyFlightPacket}'s own handler. */
    public static void toggleFairyFlight(ServerPlayer player)
    {
        if (player.isCreative() || player.isSpectator())
        {
            return;
        }
        if (!player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            return;
        }

        boolean newState = !player.getAbilities().flying;
        player.getAbilities().flying = newState;
        player.getAbilities().mayfly = newState;
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide)
        {
            return;
        }
        Player player = event.player;
        if (player.isCreative() || player.isSpectator())
        {
            return;
        }
        // The dead fly on entirely different terms (see limbo/LimboRegistry#ghostifyPlayer) - the
        // Blessed check below would otherwise strip a ghost's flight the tick after they died.
        if (com.patrickma.magiccircles.limbo.LimboState.isInLimbo(player))
        {
            return;
        }

        boolean blessed = player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get());
        if (!blessed && player.getAbilities().flying)
        {
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }

        boolean shouldSwim = player.getAbilities().flying && !player.onGround() && !player.isInWater();
        if (player.isSwimming() != shouldSwim)
        {
            player.setSwimming(shouldSwim);
        }
    }

    /**
     * No fall damage while Blessed at all, flying or not - covers landing right after closing
     * your wings mid-dive (double-tap jump again to fold them back up - see {@code
     * client/FairyFlightInput}) the same as any other fall, per an explicit request.
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event)
    {
        if (event.getEntity() instanceof Player player && player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            event.setCanceled(true);
        }
    }
}
