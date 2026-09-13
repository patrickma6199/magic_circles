package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carrying {@code registry/ModEffects#MARKED_BY_THE_DARK} (see {@code limbo/RiteOfPassage}, the
 * only thing that ever applies it): normal magic refuses a marked player, and the one cure is 10
 * continuous seconds swimming in Wellspring Water, which only exists in the Fairy Realm - so
 * clearing the mark genuinely requires making that trip.
 *
 * <p>This used to also send phantoms after a marked player out in the living world. That's gone:
 * phantoms belong to the veil and nowhere else, they hunt only while their victim is still across
 * it, and they stop the instant that person comes home (see {@code RealmOfTheDeadManager} and
 * {@code entity/GhostPhantomEntity}). Being marked now costs you the heartstone's favor until you
 * reach the Wellspring, not a permanent escort of monsters.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MarkedByTheDarkManager
{
    private static final int CURE_SWIM_TICKS = 20 * 10;

    private static final Map<UUID, Integer> wellspringSwimTicks = new HashMap<>();

    private MarkedByTheDarkManager()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        if (!(player.level() instanceof ServerLevel level))
        {
            return;
        }
        UUID id = player.getUUID();

        if (!player.hasEffect(ModEffects.MARKED_BY_THE_DARK.get()))
        {
            wellspringSwimTicks.remove(id);
            return;
        }

        tickWellspringCure(level, player, id);
    }

    private static void tickWellspringCure(ServerLevel level, ServerPlayer player, UUID id)
    {
        boolean inWellspring = player.isInFluidType((type, height) -> type == ModFluidTypes.WELLSPRING_WATER.get(), false);
        if (!inWellspring)
        {
            wellspringSwimTicks.remove(id);
            return;
        }

        int ticks = wellspringSwimTicks.merge(id, 1, Integer::sum);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
        if (ticks >= CURE_SWIM_TICKS)
        {
            player.removeEffect(ModEffects.MARKED_BY_THE_DARK.get());
            wellspringSwimTicks.remove(id);
            player.displayClientMessage(Component.translatable("effect.magiccircles.marked_by_the_dark.cured"), true);
        }
    }
}
