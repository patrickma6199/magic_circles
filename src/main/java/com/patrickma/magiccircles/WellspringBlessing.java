package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What being Blessed by the Wellspring does while you're actually in Wellspring Water: you breathe
 * it, and it slowly tops the blessing back up.
 *
 * <p>The recharge deliberately only works on someone who is <em>already</em> Blessed. Swimming
 * here is how you keep a blessing going, not how you get one - that still costs a Mana Wyrm. So a
 * player who lets it lapse mid-swim stops recharging on the spot and has to eat their way back in.
 *
 * <p>Sight is the other half of this and lives client-side in {@code client/WellspringVision}.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WellspringBlessing
{
    /** Ticks of blessing granted per tick spent swimming, so roughly four seconds gained per one spent. */
    private static final int RECHARGE_TICKS_PER_TICK = 4;
    /** Ceiling on the stored duration, so a long swim can't bank an effectively permanent blessing. */
    private static final int RECHARGE_CAP_TICKS = 20 * 60 * 5;

    private WellspringBlessing()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        MobEffectInstance blessed = player.getEffect(ModEffects.BLESSED_BY_WELLSPRING.get());
        if (blessed == null || !inWellspring(player))
        {
            return;
        }

        // Breathing it. Refilling the air supply every tick is what actually stops the drowning
        // clock, since the fluid itself still counts as drownable for everyone else.
        player.setAirSupply(player.getMaxAirSupply());

        rechargeBlessing(player, blessed);
    }

    private static boolean inWellspring(ServerPlayer player)
    {
        return player.isInFluidType((type, height) -> type == ModFluidTypes.WELLSPRING_WATER.get(), false);
    }

    private static void rechargeBlessing(ServerPlayer player, MobEffectInstance blessed)
    {
        if (blessed.getDuration() >= RECHARGE_CAP_TICKS)
        {
            return;
        }
        int topped = Math.min(RECHARGE_CAP_TICKS, blessed.getDuration() + RECHARGE_TICKS_PER_TICK);
        player.addEffect(new MobEffectInstance(ModEffects.BLESSED_BY_WELLSPRING.get(), topped,
                blessed.getAmplifier(), blessed.isAmbient(), blessed.isVisible(), blessed.showIcon()));

        if (player.level() instanceof ServerLevel level && player.tickCount % 10 == 0)
        {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                    1, 0.3, 0.4, 0.3, 0.0);
        }
    }
}
