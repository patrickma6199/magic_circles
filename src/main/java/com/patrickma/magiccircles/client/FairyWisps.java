package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.FairyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Two or three wisps circling every fairy at chest height, in flower colours - petal pink, pollen
 * gold, new-leaf green and white - wherever the fairy happens to be. Each fairy keeps the same wisps
 * for as long as it stays loaded (seeded off its entity id), the same way {@link PixieWisps} keeps a
 * pixie's. A fairy's ghost gets none: the wisps would give away something the living can't see.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FairyWisps
{
    private static final double ORBIT_RADIUS = 1.1;
    private static final double ORBIT_SPEED = 0.07;
    private static final double ORBIT_HEIGHT = 1.1;
    private static final float PARTICLE_SIZE = 0.6f;
    private static final int TRAIL_STEPS = 2;
    private static final Vector3f[] FLORAL = {
            new Vector3f(1.0f, 0.6f, 0.8f),
            new Vector3f(1.0f, 0.88f, 0.4f),
            new Vector3f(0.7f, 1.0f, 0.6f),
            new Vector3f(1.0f, 1.0f, 1.0f)
    };

    private static final Map<Integer, Wisp[]> ESCORTS = new HashMap<>();

    private FairyWisps()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
        {
            return;
        }

        double time = level.getGameTime();
        for (Entity entity : level.entitiesForRendering())
        {
            // The queen's wisps are her own - see QueenWisps.
            if (!(entity instanceof FairyEntity fairy) || fairy.isInvisible()
                    || fairy instanceof com.patrickma.magiccircles.entity.FairyQueenEntity)
            {
                continue;
            }
            Wisp[] wisps = ESCORTS.computeIfAbsent(fairy.getId(), FairyWisps::createWisps);
            double cx = fairy.getX();
            double cy = fairy.getY() + ORBIT_HEIGHT;
            double cz = fairy.getZ();
            for (Wisp wisp : wisps)
            {
                double angle = wisp.phase + time * ORBIT_SPEED * wisp.direction;
                double targetX = cx + ORBIT_RADIUS * Math.cos(angle);
                double targetY = cy + 0.35 * Math.sin(time * 0.05 + wisp.phase);
                double targetZ = cz + ORBIT_RADIUS * Math.sin(angle);

                double fromX = wisp.x;
                double fromY = wisp.y;
                double fromZ = wisp.z;
                wisp.x += (targetX - wisp.x) * 0.25;
                wisp.y += (targetY - wisp.y) * 0.25;
                wisp.z += (targetZ - wisp.z) * 0.25;

                for (int step = 1; step <= TRAIL_STEPS; step++)
                {
                    double t = (double) step / TRAIL_STEPS;
                    level.addParticle(new DustParticleOptions(wisp.color, PARTICLE_SIZE),
                            fromX + (wisp.x - fromX) * t, fromY + (wisp.y - fromY) * t, fromZ + (wisp.z - fromZ) * t,
                            0.0, 0.0, 0.0);
                }
            }
        }

        if (ESCORTS.size() > 4096)
        {
            ESCORTS.clear();
        }
    }

    private static Wisp[] createWisps(int entityId)
    {
        Random random = new Random(entityId * 3266489917L);
        int count = 2 + random.nextInt(2);
        Wisp[] wisps = new Wisp[count];
        for (int i = 0; i < count; i++)
        {
            wisps[i] = new Wisp(FLORAL[random.nextInt(FLORAL.length)], Math.PI * 2.0 * i / count + random.nextDouble() * 0.5,
                    random.nextBoolean() ? 1.0 : -1.0);
        }
        return wisps;
    }

    private static final class Wisp
    {
        final Vector3f color;
        final double phase;
        final double direction;
        double x;
        double y;
        double z;

        Wisp(Vector3f color, double phase, double direction)
        {
            this.color = color;
            this.phase = phase;
            this.direction = direction;
        }
    }
}
