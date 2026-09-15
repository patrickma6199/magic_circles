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
    /** Ticks of blessing granted per tick spent swimming, so roughly eight seconds gained per one spent. */
    private static final int RECHARGE_TICKS_PER_TICK = 8;
    /**
     * Ceiling on the stored duration, so a long swim can't bank an effectively permanent blessing:
     * forty minutes - two full days and nights, "two moons" in the Book of the Faye.
     */
    private static final int RECHARGE_CAP_TICKS = 20 * 60 * 40;

    /** Ticks of Blessing a raw and a cooked Mana Wyrm add - on top of whatever is left, up to the same ceiling. */
    private static final int RAW_WYRM_TICKS = 20 * 60;
    private static final int COOKED_WYRM_TICKS = 20 * 60 * 5;

    private WellspringBlessing()
    {
    }

    /**
     * Eating a Mana Wyrm: its Blessing is added to whatever is left rather than replacing it, up to
     * the same two-moon ceiling the Wellspring tops up to. Neither wyrm carries the Blessing as an
     * ordinary food effect for exactly that reason - vanilla only ever swaps a shorter effect for a
     * longer one, and never adds two together.
     */
    @SubscribeEvent
    public static void onFinishEating(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event)
    {
        net.minecraft.world.entity.LivingEntity eater = event.getEntity();
        if (eater.level().isClientSide)
        {
            return;
        }
        net.minecraft.world.item.ItemStack eaten = event.getItem();
        int gain = eaten.is(com.patrickma.magiccircles.registry.ModItems.COOKED_MANA_WYRM.get()) ? COOKED_WYRM_TICKS
                : eaten.is(com.patrickma.magiccircles.registry.ModItems.RAW_MANA_WYRM.get()) ? RAW_WYRM_TICKS : 0;
        if (gain == 0)
        {
            return;
        }
        MobEffectInstance current = eater.getEffect(ModEffects.BLESSED_BY_WELLSPRING.get());
        int left = current == null ? 0 : current.getDuration();
        int total = Math.min(RECHARGE_CAP_TICKS, left + gain);
        if (total > left)
        {
            eater.addEffect(new MobEffectInstance(ModEffects.BLESSED_BY_WELLSPRING.get(), total, 0));
        }
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
