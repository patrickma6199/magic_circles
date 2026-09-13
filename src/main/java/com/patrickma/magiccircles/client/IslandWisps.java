package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.FairyRealmChunkGenerator;
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
 * Wisps scattered across the *whole* island, not just around the World Tree ({@link
 * WorldTreeWisps}) or the Wellspring ({@code WellspringWisps}) - each one anchored to a fixed
 * random point somewhere on the island (uniformly sampled over the disc, not just a square, so
 * they don't bunch up in the corners) and drifting in a small local loop around that anchor,
 * rather than orbiting one shared center the way the tree's own wisps do. Purely a client-side
 * visual, same pattern as every other wisp system in this mod - a plain client tick subscriber,
 * gated to only run while the player is actually in the Fairy Realm.
 *
 * <p>Anchors are placed at a fixed height rather than one that actually follows the generated
 * terrain underneath - {@link FairyRealmChunkGenerator}'s own height at an arbitrary point isn't
 * cheaply queryable from the client thread (it's a server-side generator), so this settles for a
 * height comfortably above the flat center's own ground level, which reads fine over most of the
 * island and only occasionally floats a little high over the tallest mountains or clips into one
 * from below - a minor visual imperfection, not a functional one.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class IslandWisps
{
    private static final int WISP_COUNT = 900;
    // Kept a little inside the island's own true edge so wisps never anchor out past the
    // shoreline into the void.
    private static final double MAX_ANCHOR_RADIUS = 235.0;
    private static final double LOCAL_ORBIT_RADIUS = 3.5;
    private static final float PARTICLE_SIZE = 0.8f;
    private static final int TRAIL_STEPS = 2;
    private static final long SEED = 20260907L;

    private static Wisp[] wisps;

    private IslandWisps()
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
            double x = wisp.anchorX + LOCAL_ORBIT_RADIUS * Math.cos(angle);
            double z = wisp.anchorZ + LOCAL_ORBIT_RADIUS * Math.sin(angle);
            double y = wisp.anchorY + wisp.bobAmplitude * Math.sin(time * 0.02 + wisp.phase * 1.7);

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
        double baseHeight = WorldTree.groundY() + 4;

        Wisp[] result = new Wisp[WISP_COUNT];
        for (int i = 0; i < WISP_COUNT; i++)
        {
            // Uniform sampling over a disc - sqrt(random) counteracts the bias a plain linear
            // radius pick would have toward the center (more area lives at a larger radius, so a
            // naive pick would leave the middle looking artificially dense).
            double r = MAX_ANCHOR_RADIUS * Math.sqrt(random.nextDouble());
            double theta = random.nextDouble() * Math.PI * 2.0;
            double anchorX = r * Math.cos(theta);
            double anchorZ = r * Math.sin(theta);
            double anchorY = baseHeight + random.nextDouble() * 30.0;
            double phase = random.nextDouble() * Math.PI * 2.0;
            double speed = (0.01 + random.nextDouble() * 0.02) * (random.nextBoolean() ? 1.0 : -1.0);
            double bobAmplitude = 0.5 + random.nextDouble() * 1.5;
            Vector3f color = colors[i % colors.length].wispColor();
            result[i] = new Wisp(anchorX, anchorY, anchorZ, phase, speed, bobAmplitude, color);
        }
        return result;
    }

    private static final class Wisp
    {
        final double anchorX;
        final double anchorY;
        final double anchorZ;
        final double phase;
        final double speed;
        final double bobAmplitude;
        final Vector3f color;
        double x;
        double y;
        double z;

        Wisp(double anchorX, double anchorY, double anchorZ, double phase, double speed, double bobAmplitude, Vector3f color)
        {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.phase = phase;
            this.speed = speed;
            this.bobAmplitude = bobAmplitude;
            this.color = color;
            this.x = anchorX;
            this.y = anchorY;
            this.z = anchorZ;
        }
    }
}
