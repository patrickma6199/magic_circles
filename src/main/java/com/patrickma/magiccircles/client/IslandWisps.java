package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The Fairy Realm's free wisps - the ones that belong to no tree ({@link WorldTreeWisps}), no well
 * ({@link WellspringWisps}) and no pixie. Most of them live on the rivers: the Wellspring's water
 * runs out past the World Tree through every river on the island, and the wisps follow it, hanging
 * low over the surface and circling slowly wherever it flows. A sprinkling still wander loose over
 * the rest of the island, each drifting in a small loop around a fixed random point in the air.
 *
 * <p>The rivers are carved by server-side noise whose seed the client never learns (see {@code
 * worldgen/FairyRealmChunkGenerator}), so the client cannot work out where they run - it finds them
 * instead, by looking at the water in the chunks it already has. The island is split into
 * {@value #RIVER_CELL}-block cells; each cell deterministically has or hasn't a wisp, at a fixed
 * spot inside it, so a stretch of river holds the same wisps every time you come back to it. A
 * cell's wisp exists only while the surface at that spot really is Wellspring Water, so a river
 * someone dams or fills loses its wisps along with its water.
 *
 * <p>Only cells near the player are looked at: vanilla never draws a particle more than 32 blocks
 * from the camera anyway, so a river wisp any further out would be invisible work.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class IslandWisps
{
    /** The loose ones - there used to be 900 of these; the rivers hold most of the wisps now. */
    private static final int WISP_COUNT = 220;
    // Kept a little inside the island's own true edge so wisps never anchor out past the
    // shoreline into the void.
    private static final double MAX_ANCHOR_RADIUS = 235.0;
    private static final double LOCAL_ORBIT_RADIUS = 3.5;
    private static final float PARTICLE_SIZE = 0.8f;
    private static final int TRAIL_STEPS = 2;
    private static final long SEED = 20260907L;

    /** One river cell's width, in blocks - never more than one wisp per cell. */
    private static final int RIVER_CELL = 6;
    /** The share of river cells that hold a wisp. */
    private static final float RIVER_CELL_CHANCE = 0.7f;
    /** A little past vanilla's 32-block particle cutoff, so wisps are already in place before they come into view. */
    private static final int RIVER_SCAN_RADIUS = 40;
    private static final int RIVER_SCAN_INTERVAL_TICKS = 20;
    /** The Wellspring itself, at the World Tree's foot, keeps its own wisps. */
    private static final double WELLSPRING_EXCLUSION_RADIUS = 14.0;
    private static final double RIVER_ORBIT_MIN = 0.8;
    private static final double RIVER_ORBIT_RANDOM = 1.6;
    /** Height above the water's own surface. */
    private static final double RIVER_HOVER_MIN = 0.5;
    private static final double RIVER_HOVER_RANDOM = 1.1;
    private static final long RIVER_SEED = 20260913L;

    private static Wisp[] wisps;
    /** River wisps currently near the player, keyed by their cell. */
    private static final Map<Long, Wisp> riverWisps = new HashMap<>();
    private static int riverScanCountdown;

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
        if (mc.level == null || mc.player == null || mc.level.dimension() != ModDimensions.FAIRY_REALM)
        {
            riverWisps.clear();
            riverScanCountdown = 0;
            return;
        }
        if (wisps == null)
        {
            wisps = createWisps();
        }
        if (--riverScanCountdown <= 0)
        {
            riverScanCountdown = RIVER_SCAN_INTERVAL_TICKS;
            scanRivers(mc.level, mc.player);
        }

        double time = mc.level.getGameTime();
        for (Wisp wisp : wisps)
        {
            drift(mc.level, wisp, time);
        }
        for (Wisp wisp : riverWisps.values())
        {
            drift(mc.level, wisp, time);
        }
    }

    private static void drift(ClientLevel level, Wisp wisp, double time)
    {
        double angle = wisp.phase + time * wisp.speed;
        double x = wisp.anchorX + wisp.orbitRadius * Math.cos(angle);
        double z = wisp.anchorZ + wisp.orbitRadius * Math.sin(angle);
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
            level.addParticle(new DustParticleOptions(wisp.color, PARTICLE_SIZE),
                    fromX + (x - fromX) * t,
                    fromY + (y - fromY) * t,
                    fromZ + (z - fromZ) * t,
                    0.0, 0.0, 0.0);
        }
    }

    /**
     * Finds the river cells around the player that should hold a wisp right now, keeps the wisps
     * already there (so their trails stay continuous), adds any newly found, and drops the rest.
     */
    private static void scanRivers(ClientLevel level, Player player)
    {
        Set<Long> present = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minCellX = Math.floorDiv(player.getBlockX() - RIVER_SCAN_RADIUS, RIVER_CELL);
        int maxCellX = Math.floorDiv(player.getBlockX() + RIVER_SCAN_RADIUS, RIVER_CELL);
        int minCellZ = Math.floorDiv(player.getBlockZ() - RIVER_SCAN_RADIUS, RIVER_CELL);
        int maxCellZ = Math.floorDiv(player.getBlockZ() + RIVER_SCAN_RADIUS, RIVER_CELL);
        RuneColor[] colors = RuneColor.values();

        for (int cellX = minCellX; cellX <= maxCellX; cellX++)
        {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++)
            {
                Random random = new Random(RIVER_SEED ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L));
                if (random.nextFloat() >= RIVER_CELL_CHANCE)
                {
                    continue;
                }
                int x = cellX * RIVER_CELL + random.nextInt(RIVER_CELL);
                int z = cellZ * RIVER_CELL + random.nextInt(RIVER_CELL);
                double fromTreeX = x - WorldTree.CENTER_X;
                double fromTreeZ = z - WorldTree.CENTER_Z;
                if (fromTreeX * fromTreeX + fromTreeZ * fromTreeZ < WELLSPRING_EXCLUSION_RADIUS * WELLSPRING_EXCLUSION_RADIUS
                        || !level.hasChunk(x >> 4, z >> 4))
                {
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (!level.getBlockState(cursor.set(x, surfaceY, z)).is(ModBlocks.WELLSPRING_WATER.get()))
                {
                    continue;
                }

                long key = ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
                present.add(key);
                Wisp existing = riverWisps.get(key);
                if (existing != null && existing.surfaceY == surfaceY)
                {
                    continue;
                }
                double anchorY = surfaceY + 1.0 + RIVER_HOVER_MIN + random.nextDouble() * RIVER_HOVER_RANDOM;
                double phase = random.nextDouble() * Math.PI * 2.0;
                double speed = (0.015 + random.nextDouble() * 0.025) * (random.nextBoolean() ? 1.0 : -1.0);
                double bobAmplitude = 0.15 + random.nextDouble() * 0.3;
                double orbitRadius = RIVER_ORBIT_MIN + random.nextDouble() * RIVER_ORBIT_RANDOM;
                Vector3f color = colors[random.nextInt(colors.length)].wispColor();
                Wisp wisp = new Wisp(x + 0.5, anchorY, z + 0.5, phase, speed, bobAmplitude, orbitRadius, color);
                wisp.surfaceY = surfaceY;
                riverWisps.put(key, wisp);
            }
        }
        riverWisps.keySet().retainAll(present);
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
            result[i] = new Wisp(anchorX, anchorY, anchorZ, phase, speed, bobAmplitude, LOCAL_ORBIT_RADIUS, color);
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
        final double orbitRadius;
        final Vector3f color;
        /** For a river wisp, the water surface it was placed over - it is re-placed if that changes. */
        int surfaceY;
        double x;
        double y;
        double z;

        Wisp(double anchorX, double anchorY, double anchorZ, double phase, double speed, double bobAmplitude,
             double orbitRadius, Vector3f color)
        {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.phase = phase;
            this.speed = speed;
            this.bobAmplitude = bobAmplitude;
            this.orbitRadius = orbitRadius;
            this.color = color;
            this.x = anchorX;
            this.y = anchorY;
            this.z = anchorZ;
        }
    }
}
