package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * While a player is Blessed by the Wellspring (see {@code registry/ModEffects#BLESSED_BY_WELLSPRING}),
 * exactly one wisp of every {@link RuneColor} continuously orbits them - a small, permanent halo
 * marking the blessing, independent of whatever spell or ritual (if any) is currently running.
 * Reuses the same colored-dust-trail look every other wisp system in this mod already uses (see
 * e.g. {@code WellspringWisps}), just anchored to the moving player each tick instead of a fixed
 * world position.
 *
 * <p>Runs for every blessed player in the client's render distance (not just the local player),
 * since {@code hasEffect} is synced to every tracking client the same way potion particles are -
 * so this is visible on other blessed players too, not only from a first-person view.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlessedWispOrbit
{
    private static final double RADIUS = 1.6;
    private static final double BASE_HEIGHT = 1.1;
    private static final double BOB_AMPLITUDE = 0.15;
    private static final double ANGLE_SPEED = 0.05;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int TRAIL_STEPS = 2;

    private static final Map<UUID, double[]> lastPositions = new HashMap<>();
    private static ClientLevel lastLevel;

    private BlessedWispOrbit()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel)
        {
            lastPositions.clear();
            lastLevel = level;
        }
        if (level == null)
        {
            return;
        }

        RuneColor[] colors = RuneColor.values();
        double time = level.getGameTime();

        for (Player player : level.players())
        {
            if (!player.isAlive() || player.isSpectator() || !player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
            {
                lastPositions.remove(player.getUUID());
                continue;
            }

            double[] current = computePositions(colors, player.getX(), player.getY(), player.getZ(), time);
            double[] previous = lastPositions.getOrDefault(player.getUUID(), current);

            for (int i = 0; i < colors.length; i++)
            {
                double fromX = previous[i * 3];
                double fromY = previous[i * 3 + 1];
                double fromZ = previous[i * 3 + 2];
                double toX = current[i * 3];
                double toY = current[i * 3 + 1];
                double toZ = current[i * 3 + 2];

                for (int step = 1; step <= TRAIL_STEPS; step++)
                {
                    double t = (double) step / TRAIL_STEPS;
                    level.addParticle(new DustParticleOptions(colors[i].wispColor(), PARTICLE_SIZE),
                            fromX + (toX - fromX) * t,
                            fromY + (toY - fromY) * t,
                            fromZ + (toZ - fromZ) * t,
                            0.0, 0.0, 0.0);
                }
            }

            lastPositions.put(player.getUUID(), current);
        }
    }

    private static double[] computePositions(RuneColor[] colors, double px, double py, double pz, double time)
    {
        double[] result = new double[colors.length * 3];
        for (int i = 0; i < colors.length; i++)
        {
            double angle = time * ANGLE_SPEED + i * (Math.PI * 2.0 / colors.length);
            result[i * 3] = px + RADIUS * Math.cos(angle);
            result[i * 3 + 1] = py + BASE_HEIGHT + BOB_AMPLITUDE * Math.sin(time * 0.1 + i);
            result[i * 3 + 2] = pz + RADIUS * Math.sin(angle);
        }
        return result;
    }
}
