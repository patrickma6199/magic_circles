package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The Shield spell's own real boundary loop, as a closed sequence of world-space points - either
 * a plain circle at {@link HeartCoreBlockEntity#shieldRadius()} around the heart (no outer rune
 * boundary was ever drawn), or the actual outer Purple loop's own footprint when one was (see
 * {@code HeartCoreBlockEntity#findValidWallShape}). Shared by {@link ClientCircleWisps} (whose 12
 * rune-sourced wisps always trace this) and {@link ShieldRingWisps} (which only ever adds
 * *supplementary* wisps along this same path, and only when it's long enough that 12 alone would
 * look sparse - see that class's own doc comment).
 */
final class ShieldBoundaryPath
{
    private ShieldBoundaryPath()
    {
    }

    static List<double[]> build(BlockPos center, @Nullable Set<BlockPos> wallFootprint, int wallBottomY, int wallTopY)
    {
        return wallFootprint != null && !wallFootprint.isEmpty()
                ? wallPath(wallFootprint, wallBottomY, wallTopY)
                : spherePath(center);
    }

    static double perimeter(List<double[]> path)
    {
        double total = 0.0;
        for (int i = 0; i < path.size(); i++)
        {
            double[] a = path.get(i);
            double[] b = path.get((i + 1) % path.size());
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double dz = b[2] - a[2];
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return total;
    }

    /** A plain horizontal circle at {@link HeartCoreBlockEntity#shieldRadius()} around the shield's own vertical center. */
    private static List<double[]> spherePath(BlockPos center)
    {
        double radius = HeartCoreBlockEntity.shieldRadius();
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 1.5;
        double cz = center.getZ() + 0.5;
        int segments = 32;
        List<double[]> path = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++)
        {
            double angle = (i / (double) segments) * 2.0 * Math.PI;
            path.add(new double[] {cx + radius * Math.cos(angle), cy, cz + radius * Math.sin(angle)});
        }
        return path;
    }

    /** The wall's own real footprint, ordered into a perimeter walk (sorted by angle around its own centroid) at its vertical midpoint. */
    private static List<double[]> wallPath(Set<BlockPos> footprint, int bottomY, int topY)
    {
        double sumX = 0;
        double sumZ = 0;
        for (BlockPos cell : footprint)
        {
            sumX += cell.getX();
            sumZ += cell.getZ();
        }
        double centroidX = sumX / footprint.size();
        double centroidZ = sumZ / footprint.size();
        double midY = (bottomY + topY) / 2.0 + 0.3;

        List<BlockPos> ordered = new ArrayList<>(footprint);
        ordered.sort(Comparator.comparingDouble(cell -> Math.atan2(cell.getZ() - centroidZ, cell.getX() - centroidX)));

        List<double[]> path = new ArrayList<>(ordered.size());
        for (BlockPos cell : ordered)
        {
            path.add(new double[] {cell.getX() + 0.5, midY, cell.getZ() + 0.5});
        }
        return path;
    }

    /** The point a fraction {@code t} (0-1) of the way around the closed loop. */
    static double[] pointAt(List<double[]> path, double t)
    {
        int size = path.size();
        double scaled = ((t % 1.0) + 1.0) % 1.0 * size;
        int index = (int) scaled;
        double frac = scaled - index;
        double[] a = path.get(index % size);
        double[] b = path.get((index + 1) % size);
        return new double[] {
                a[0] + (b[0] - a[0]) * frac,
                a[1] + (b[1] - a[1]) * frac,
                a[2] + (b[2] - a[2]) * frac};
    }
}
