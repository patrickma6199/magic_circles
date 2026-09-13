package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.block.RuneColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * *Supplementary* purple wisps along the Shield spell's own real boundary loop ({@code
 * ShieldBoundaryPath}) - on top of, never instead of, the 12 rune-sourced wisps {@code
 * ClientCircleWisps} (see {@link com.patrickma.magiccircles.ritual.WispChannel#TRACE_SHIELD})
 * already flies out to trace that same loop. This class only ever adds *new* wisps, and only once
 * the loop is long enough (see {@link #MIN_PERIMETER_FOR_EXTRA}) that spacing 12 alone around it
 * would look sparse - a small custom-drawn wall, or the default sphere sitting right at that
 * threshold, gets no extra wisps summoned "out of nowhere" at all.
 *
 * <p>An earlier version of this class was the *only* thing tracing the boundary - a full second,
 * entirely separate set of 12 wisps that simply appeared, independent of the ring's own wisps,
 * which is exactly what read as "wisps that come out of nowhere."
 */
public final class ShieldRingWisps
{
    private static final Vector3f PURPLE = RuneColor.PURPLE.wispColor();
    // Below this real perimeter, 12 wisps (the rune-sourced ones ClientCircleWisps already
    // provides) are considered close enough together already - no supplementary ones needed.
    private static final double MIN_PERIMETER_FOR_EXTRA = 40.0;
    // Roughly one supplementary wisp per this many blocks of perimeter *beyond* what the base 12
    // already cover well.
    private static final double BLOCKS_PER_EXTRA_WISP = 4.0;
    private static final int BASE_WISP_COUNT = 12;

    private static final double SPEED = 0.022;
    private static final double EASE = 0.2;
    private static final float PARTICLE_SIZE = 0.8f;
    private static final int TRAIL_STEPS = 3;

    private static final Map<BlockPos, Wisp[]> RINGS = new HashMap<>();

    private ShieldRingWisps()
    {
    }

    /** Call every client tick from a Heart Core, passing its current (synced) Shield state - {@code wallFootprint} null means the plain sphere. */
    public static void tick(Level level, BlockPos center, boolean shieldActive, @Nullable Set<BlockPos> wallFootprint, int wallBottomY, int wallTopY)
    {
        List<double[]> path = ShieldBoundaryPath.build(center, wallFootprint, wallBottomY, wallTopY);
        double perimeter = ShieldBoundaryPath.perimeter(path);
        int extraCount = perimeter >= MIN_PERIMETER_FOR_EXTRA
                ? Math.max(0, (int) Math.round(perimeter / BLOCKS_PER_EXTRA_WISP) - BASE_WISP_COUNT)
                : 0;

        if (!shieldActive || extraCount <= 0)
        {
            RINGS.remove(center);
            return;
        }

        Wisp[] wisps = RINGS.computeIfAbsent(center.immutable(), c -> new Wisp[0]);
        if (wisps.length != extraCount)
        {
            wisps = createWisps(extraCount);
            RINGS.put(center.immutable(), wisps);
        }

        double time = level.getGameTime();
        // Offset by half a base-wisp spacing so these never sit exactly on top of
        // ClientCircleWisps' own 12 rune-sourced points along the same path.
        double phaseOffset = 0.5 / BASE_WISP_COUNT;
        double progress = (time * SPEED) / path.size() + phaseOffset;

        for (int i = 0; i < wisps.length; i++)
        {
            double t = progress + (double) i / wisps.length;
            double[] target = ShieldBoundaryPath.pointAt(path, t);

            Wisp wisp = wisps[i];
            double fromX = wisp.x;
            double fromY = wisp.y;
            double fromZ = wisp.z;
            wisp.x += (target[0] - wisp.x) * EASE;
            wisp.y += (target[1] - wisp.y) * EASE;
            wisp.z += (target[2] - wisp.z) * EASE;

            for (int step = 1; step <= TRAIL_STEPS; step++)
            {
                double f = (double) step / TRAIL_STEPS;
                level.addParticle(new DustParticleOptions(PURPLE, PARTICLE_SIZE),
                        fromX + (wisp.x - fromX) * f,
                        fromY + (wisp.y - fromY) * f,
                        fromZ + (wisp.z - fromZ) * f,
                        0.0, 0.0, 0.0);
            }
        }
    }

    private static Wisp[] createWisps(int count)
    {
        Wisp[] wisps = new Wisp[count];
        for (int i = 0; i < wisps.length; i++)
        {
            wisps[i] = new Wisp();
        }
        return wisps;
    }

    private static final class Wisp
    {
        double x;
        double y;
        double z;
    }
}
