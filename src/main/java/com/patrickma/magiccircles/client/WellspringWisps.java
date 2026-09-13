package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

/**
 * Extra wisps orbiting close around the Wellspring itself (see {@code worldgen/WorldTree.java}'s
 * well) - on top of {@link WorldTreeWisps}' much wider orbit around the whole tree - cycling
 * through every {@link RuneColor} plus white, matching the water's own color-cycling texture
 * (see {@code tools/gen_wellspring_water_texture.py}): "this water holds mana."
 *
 * <p>While a nearby Heart Core has Mana Font running (charging its mana from this Wellspring -
 * see {@code HeartCoreBlockEntity#startManaFontSpell}), the handful of wisps already orbiting
 * closest to the well peel off one at a time and fly straight into that heart, as if being drawn
 * in and absorbed, then reappear back at the well to rejoin their orbit - a continuous stream for
 * as long as the spell runs. Every farther-orbiting wisp is unaffected, which is what makes it
 * read as "closer wisps" rather than all of them.
 *
 * <p>Finding the active heart is a small per-interval scan of already-loaded chunks around the
 * well ({@code ChunkSource#getChunkNow} never triggers a load of its own - {@code null} for
 * anything not already there), not a link to any specific block entity - there's no other way
 * for a stand-alone, fixed-position client system like this one to know about a nearby,
 * otherwise unrelated block entity's state.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WellspringWisps
{
    private static final int WISP_COUNT = 30;
    private static final int ABSORB_COUNT = 8; // the closest-orbiting few are the ones eligible to fly in
    private static final double MIN_RADIUS = 2.5;
    private static final double MAX_RADIUS = 7.5;
    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int TRAIL_STEPS = 3;
    private static final long SEED = 20260906L * 2L;

    private static final int HEART_SEARCH_RADIUS = 24;
    private static final int HEART_SEARCH_INTERVAL = 10;
    private static final double ABSORB_SPEED = 0.045;
    private static final int ABSORB_COOLDOWN_MIN = 20;
    private static final int ABSORB_COOLDOWN_RANDOM = 40;

    private static Wisp[] wisps;
    private static int heartSearchCounter;
    private static BlockPos activeHeartPos;

    private WellspringWisps()
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
        if (wisps == null)
        {
            wisps = createWisps();
        }

        if (heartSearchCounter <= 0)
        {
            activeHeartPos = findActiveManaFontHeart(level);
            heartSearchCounter = HEART_SEARCH_INTERVAL;
        }
        heartSearchCounter--;

        double time = level.getGameTime();
        BlockPos wellCenter = WorldTree.wellCenter();
        for (int i = 0; i < wisps.length; i++)
        {
            Wisp wisp = wisps[i];
            double angle = wisp.phase + time * wisp.speed;
            double orbitX = wellCenter.getX() + 0.5 + wisp.radius * Math.cos(angle);
            double orbitZ = wellCenter.getZ() + 0.5 + wisp.radius * Math.sin(angle);
            double orbitY = wellCenter.getY() + wisp.baseHeight + wisp.bobAmplitude * Math.sin(time * 0.05 + wisp.phase * 1.7);

            double x;
            double y;
            double z;
            boolean eligible = activeHeartPos != null && i < ABSORB_COUNT;
            if (!eligible)
            {
                wisp.absorbProgress = -1.0;
                x = orbitX;
                y = orbitY;
                z = orbitZ;
            }
            else if (wisp.absorbProgress < 0.0)
            {
                if (wisp.cooldown > 0)
                {
                    wisp.cooldown--;
                }
                else
                {
                    wisp.absorbProgress = 0.0;
                    wisp.absorbFromX = orbitX;
                    wisp.absorbFromY = orbitY;
                    wisp.absorbFromZ = orbitZ;
                }
                x = orbitX;
                y = orbitY;
                z = orbitZ;
            }
            else
            {
                wisp.absorbProgress += ABSORB_SPEED;
                if (wisp.absorbProgress >= 1.0)
                {
                    wisp.absorbProgress = -1.0;
                    wisp.cooldown = ABSORB_COOLDOWN_MIN + wisp.random.nextInt(ABSORB_COOLDOWN_RANDOM);
                    x = orbitX;
                    y = orbitY;
                    z = orbitZ;
                }
                else
                {
                    double t = wisp.absorbProgress;
                    x = wisp.absorbFromX + (activeHeartPos.getX() + 0.5 - wisp.absorbFromX) * t;
                    y = wisp.absorbFromY + (activeHeartPos.getY() + 0.5 - wisp.absorbFromY) * t;
                    z = wisp.absorbFromZ + (activeHeartPos.getZ() + 0.5 - wisp.absorbFromZ) * t;
                }
            }

            double fromX = wisp.x;
            double fromY = wisp.y;
            double fromZ = wisp.z;
            wisp.x = x;
            wisp.y = y;
            wisp.z = z;

            for (int step = 1; step <= TRAIL_STEPS; step++)
            {
                double t = (double) step / TRAIL_STEPS;
                level.addParticle(new DustParticleOptions(wisp.color, PARTICLE_SIZE),
                        fromX + (x - fromX) * t,
                        fromY + (y - fromY) * t,
                        fromZ + (z - fromZ) * t,
                        0.0, 0.0, 0.0);
            }
        }
    }

    /** The first Mana-Font-active Heart Core found within {@value #HEART_SEARCH_RADIUS} blocks of the well, or {@code null}. */
    private static BlockPos findActiveManaFontHeart(ClientLevel level)
    {
        BlockPos wellCenter = WorldTree.wellCenter();
        int chunkRadius = (HEART_SEARCH_RADIUS >> 4) + 1;
        int centerChunkX = wellCenter.getX() >> 4;
        int centerChunkZ = wellCenter.getZ() >> 4;
        long radiusSq = (long) HEART_SEARCH_RADIUS * HEART_SEARCH_RADIUS;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++)
        {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet())
                {
                    if (!(entry.getValue() instanceof HeartCoreBlockEntity heart) || !heart.isManaFontActiveSynced())
                    {
                        continue;
                    }
                    BlockPos pos = entry.getKey();
                    long dx = pos.getX() - wellCenter.getX();
                    long dz = pos.getZ() - wellCenter.getZ();
                    if (dx * dx + dz * dz <= radiusSq)
                    {
                        return pos.above();
                    }
                }
            }
        }
        return null;
    }

    private static Wisp[] createWisps()
    {
        Random random = new Random(SEED);
        RuneColor[] runeColors = RuneColor.values();
        Vector3f[] colors = new Vector3f[runeColors.length + 1];
        for (int i = 0; i < runeColors.length; i++)
        {
            colors[i] = runeColors[i].wispColor();
        }
        colors[runeColors.length] = new Vector3f(1.0f, 1.0f, 1.0f);

        // Sorted ascending so indices 0..ABSORB_COUNT-1 are always the closest-orbiting wisps -
        // the ones eligible to be pulled toward an active Mana Font.
        double[] radii = new double[WISP_COUNT];
        for (int i = 0; i < WISP_COUNT; i++)
        {
            radii[i] = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
        }
        Arrays.sort(radii);

        Wisp[] result = new Wisp[WISP_COUNT];
        for (int i = 0; i < WISP_COUNT; i++)
        {
            double radius = radii[i];
            double baseHeight = 1.0 + random.nextDouble() * 3.5;
            double phase = (i * GOLDEN_ANGLE) % (Math.PI * 2.0);
            double speed = (0.01 + random.nextDouble() * 0.02) * (random.nextBoolean() ? 1.0 : -1.0);
            double bobAmplitude = 0.4 + random.nextDouble() * 0.8;
            Vector3f color = colors[i % colors.length];
            result[i] = new Wisp(radius, baseHeight, phase, speed, bobAmplitude, color, new Random(random.nextLong()));
        }
        return result;
    }

    private static final class Wisp
    {
        final double radius;
        final double baseHeight;
        final double phase;
        final double speed;
        final double bobAmplitude;
        final Vector3f color;
        final Random random;
        double x;
        double y;
        double z;
        double absorbProgress = -1.0;
        double absorbFromX;
        double absorbFromY;
        double absorbFromZ;
        int cooldown;

        Wisp(double radius, double baseHeight, double phase, double speed, double bobAmplitude, Vector3f color, Random random)
        {
            this.radius = radius;
            this.baseHeight = baseHeight;
            this.phase = phase;
            this.speed = speed;
            this.bobAmplitude = bobAmplitude;
            this.color = color;
            this.random = random;
            this.cooldown = random.nextInt(ABSORB_COOLDOWN_MIN + ABSORB_COOLDOWN_RANDOM);
            this.x = radius;
            this.y = baseHeight;
            this.z = 0.0;
        }
    }
}
