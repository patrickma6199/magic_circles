package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Random;

/**
 * 100 wisps - 20 of each {@link RuneColor}, cycling through all 5 - orbiting the World Tree
 * (see {@code worldgen/WorldTree.java}) at every height from just above its roots to the very
 * top of its canopy, and at every radius from just past its trunk out to the furthest reach of
 * its leaves. Purely a client-side visual, exactly like {@code ClientCircleWisps}/
 * {@code ClientHeartWisps}, but tied to one fixed world position rather than a block entity - so
 * there's no per-block-entity tick to hook into, and this instead runs off a plain client tick
 * subscriber, gated to only do anything while the player is actually in the Fairy Realm.
 *
 * <p>Unlike the ring/heart wisps, each of these 100 orbits are fully deterministic functions of
 * game time (a fixed radius, height, phase, and angular speed per wisp, seeded once and never
 * changed) rather than something eased toward a moving target - there's no "state" to transition
 * between here, just one continuous circle each wisp has always been on and always will be.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorldTreeWisps
{
    private static final int WISP_COUNT = 100;
    private static final double MIN_RADIUS = 8.0;
    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final float PARTICLE_SIZE = 0.9f;
    private static final int TRAIL_STEPS = 3;
    private static final long SEED = 20260906L;

    private static Wisp[] wisps;

    private WorldTreeWisps()
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
        if (mc.level == null || mc.level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }
        if (wisps == null)
        {
            wisps = createWisps();
        }

        double time = mc.level.getGameTime();
        for (Wisp wisp : wisps)
        {
            double angle = wisp.phase + time * wisp.speed;
            double x = WorldTree.CENTER_X + 0.5 + wisp.radius * Math.cos(angle);
            double z = WorldTree.CENTER_Z + 0.5 + wisp.radius * Math.sin(angle);
            double y = wisp.baseY + wisp.bobAmplitude * Math.sin(time * 0.02 + wisp.phase * 1.7);

            double fromX = wisp.x;
            double fromY = wisp.y;
            double fromZ = wisp.z;
            wisp.x = x;
            wisp.y = y;
            wisp.z = z;

            for (int step = 1; step <= TRAIL_STEPS; step++)
            {
                double t = (double) step / TRAIL_STEPS;
                mc.level.addParticle(new DustParticleOptions(wisp.color, PARTICLE_SIZE),
                        fromX + (x - fromX) * t,
                        fromY + (y - fromY) * t,
                        fromZ + (z - fromZ) * t,
                        0.0, 0.0, 0.0);
            }
        }
    }

    private static Wisp[] createWisps()
    {
        Random random = new Random(SEED);
        RuneColor[] colors = RuneColor.values();
        int groundY = WorldTree.groundY();
        double maxRadius = WorldTree.maxRadius();
        double maxHeight = WorldTree.topY();
        double minHeight = groundY + 3;

        Wisp[] result = new Wisp[WISP_COUNT];
        for (int i = 0; i < WISP_COUNT; i++)
        {
            double radius = MIN_RADIUS + random.nextDouble() * Math.max(1.0, maxRadius - MIN_RADIUS);
            double baseY = minHeight + random.nextDouble() * (maxHeight - minHeight);
            double phase = (i * GOLDEN_ANGLE) % (Math.PI * 2.0);
            double speed = (0.003 + random.nextDouble() * 0.009) * (random.nextBoolean() ? 1.0 : -1.0);
            double bobAmplitude = 1.0 + random.nextDouble() * 2.0;
            Vector3f color = colors[i % colors.length].wispColor();
            result[i] = new Wisp(radius, baseY, phase, speed, bobAmplitude, color);
        }
        return result;
    }

    private static final class Wisp
    {
        final double radius;
        final double baseY;
        final double phase;
        final double speed;
        final double bobAmplitude;
        final Vector3f color;
        double x;
        double y;
        double z;

        Wisp(double radius, double baseY, double phase, double speed, double bobAmplitude, Vector3f color)
        {
            this.radius = radius;
            this.baseY = baseY;
            this.phase = phase;
            this.speed = speed;
            this.bobAmplitude = bobAmplitude;
            this.color = color;
            this.x = WorldTree.CENTER_X + radius;
            this.y = baseY;
            this.z = WorldTree.CENTER_Z;
        }
    }
}
