package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.entity.PixieEntity;
import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 1-2 wisps of random colors trailing every Pixie, wherever it goes - "they love flying with
 * wisps," per how these were asked for. Entirely dimension-gated rather than needing any
 * per-entity "am I allowed wisps" flag: this only ever runs while the *client* is actually in the
 * Fairy Realm (nothing here has any other dimension's chunks/entities to iterate anyway), which
 * already satisfies "if they're in any other dimension, no wisps follow them" - a Pixie that
 * wandered elsewhere just isn't something this system ever sees.
 *
 * <p>Each pixie's own wisp count/colors are chosen once (keyed by entity id) and kept stable for
 * as long as that pixie stays loaded, rather than rerolled every tick - "1-2 of random colors"
 * reads as those specific wisps belonging to that specific pixie, not a flickering random count.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PixieWisps
{
    private static final double ORBIT_RADIUS = 1.4;
    private static final double ORBIT_SPEED = 0.05;
    private static final float PARTICLE_SIZE = 0.7f;
    private static final int TRAIL_STEPS = 2;

    private static final Map<Integer, Wisp[]> ESCORTS = new HashMap<>();

    private PixieWisps()
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
        if (level == null || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }

        double time = level.getGameTime();
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering())
        {
            if (!(entity instanceof PixieEntity pixie))
            {
                continue;
            }
            Wisp[] wisps = ESCORTS.computeIfAbsent(pixie.getId(), id -> createWisps(pixie.getId()));

            double px = pixie.getX();
            double py = pixie.getY() + 0.3;
            double pz = pixie.getZ();

            for (int i = 0; i < wisps.length; i++)
            {
                Wisp wisp = wisps[i];
                double angle = wisp.phase + time * ORBIT_SPEED;
                double targetX = px + ORBIT_RADIUS * Math.cos(angle);
                double targetY = py + 0.3 * Math.sin(time * 0.03 + wisp.phase);
                double targetZ = pz + ORBIT_RADIUS * Math.sin(angle);

                double fromX = wisp.x;
                double fromY = wisp.y;
                double fromZ = wisp.z;
                wisp.x += (targetX - wisp.x) * 0.2;
                wisp.y += (targetY - wisp.y) * 0.2;
                wisp.z += (targetZ - wisp.z) * 0.2;

                for (int step = 1; step <= TRAIL_STEPS; step++)
                {
                    double t = (double) step / TRAIL_STEPS;
                    level.addParticle(new DustParticleOptions(wisp.color, PARTICLE_SIZE),
                            fromX + (wisp.x - fromX) * t,
                            fromY + (wisp.y - fromY) * t,
                            fromZ + (wisp.z - fromZ) * t,
                            0.0, 0.0, 0.0);
                }
            }
        }

        // Entities that despawned/unloaded since the last tick don't need to keep their entry
        // around forever - a plain size cap keeps this from growing unbounded over a long session
        // even if a pixie unloads without this system ever explicitly hearing about it.
        if (ESCORTS.size() > 4096)
        {
            ESCORTS.clear();
        }
    }

    private static Wisp[] createWisps(int entityId)
    {
        Random random = new Random(entityId * 2246822519L);
        int count = 1 + random.nextInt(2);
        RuneColor[] colors = RuneColor.values();
        Wisp[] wisps = new Wisp[count];
        for (int i = 0; i < count; i++)
        {
            Vector3f color = colors[random.nextInt(colors.length)].wispColor();
            double phase = random.nextDouble() * Math.PI * 2.0;
            wisps[i] = new Wisp(color, phase);
        }
        return wisps;
    }

    private static final class Wisp
    {
        final Vector3f color;
        final double phase;
        double x;
        double y;
        double z;

        Wisp(Vector3f color, double phase)
        {
            this.color = color;
            this.phase = phase;
        }
    }
}
