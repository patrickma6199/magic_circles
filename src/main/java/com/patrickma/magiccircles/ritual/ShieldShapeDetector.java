package com.patrickma.magiccircles.ritual;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Looks for an optional second, larger boundary of Purple runes drawn somewhere outside a
 * Shield ring, which - if found and valid - turns the Shield spell's usual sphere into a
 * flat-topped-and-bottomed box that follows that outer shape instead (see
 * {@code HeartCoreBlockEntity#startShieldSpell}). Entirely separate from
 * {@link MagicCircleRitual}/{@link PortalRitual}: those match one fixed pattern; this looks for
 * *any* enclosed loop of Purple runes, wherever the player happened to draw one.
 *
 * <p>"Enclosed" is checked the same way you'd check it by eye: flood-fill outward from just
 * outside the inner ring, treating Purple runes as walls the fill can't cross. If the fill
 * escapes past {@link #MAX_FLOOD_CELLS} cells without being fully bounded, there's either no
 * outer shape at all or it isn't actually closed, and the caller falls back to the normal
 * sphere.
 *
 * <p>The fill itself only tracks X/Z - a rune's own Y is allowed to vary from column to column
 * (search up and down a short window, {@link #VERTICAL_SEARCH_RANGE}, for each one), so the
 * outer loop can follow a slope or a staircase instead of needing to sit dead level with the
 * inner ring. That per-rune height only ever feeds into the *box's* height, though (see
 * {@code HeartCoreBlockEntity#findValidWallShape}) - the box itself still ends up with one flat
 * top and one flat bottom, not a wall that itself follows the terrain.
 *
 * <p><b>The inner ring's own footprint is skipped entirely by the fill</b>, not just started
 * outside it: a plain Shield ring is *solid Purple* (it's Purple's solo spell), so without this
 * the fill would wrap all the way around the ring and treat its own 12 rune cells as "wall"
 * cells too, indistinguishable from the actual outer loop the player drew. That alone wouldn't
 * necessarily break detection - except {@link #touchesInnerRing} checks whether any wall cell
 * is *adjacent* to a ring position, and the ring's 12 positions are all mutually adjacent to
 * each other (they're packed into one 5x5 area) - so once the ring's own cells leaked into
 * {@code wallCells}, every outer shape was rejected as "touching the ring," regardless of how
 * far away it actually was. Any (x, z) inside the inner 5x5 footprint is now excluded from the
 * fill outright - neither "open" nor a "wall," just never visited - so it can never contribute
 * bogus wall cells, and the fill naturally flows around the outside of that footprint instead.
 *
 * <p>{@link #closeDiagonalGaps} handles a separate issue from any of the above: two wall cells
 * that only touch at a shared corner (a checkerboard-style diagonal pair) leave a real gap a
 * continuous-space entity can walk straight through diagonally, even though the flood fill
 * itself never actually leaks there (4-directional movement can't hop between two diagonal
 * cells at all, so "the interior is enclosed" and "the wall has no physical gaps" turned out to
 * be two different questions).
 */
public final class ShieldShapeDetector
{
    private static final int START_OFFSET = 3;
    private static final int MAX_FLOOD_CELLS = 6000;
    private static final int VERTICAL_SEARCH_RANGE = 6;
    // Half-width of the inner ring's own 5x5 footprint (RING_OFFSETS/CORNER_OFFSETS both stay
    // within +-2 of center) - the fill treats every cell in this box as neither open nor wall.
    private static final int INNER_FOOTPRINT_RADIUS = 2;

    private ShieldShapeDetector()
    {
    }

    /**
     * An outer Purple loop's boundary cells (one per X/Z column, at whatever Y that column's
     * rune actually sits on) plus the 2D area it encloses - the number of open floor cells the
     * flood fill found before hitting a wall in every direction, i.e. what
     * {@code HeartCoreBlockEntity} sizes the box's total height from.
     */
    public static final class WallShape
    {
        public final Set<BlockPos> wallCells;
        public final int area;
        // Every X/Z column actually enclosed by wallCells - the flood-filled interior plus the
        // inner ring's own 5x5 footprint (excluded from the fill itself, but still physically
        // inside the shape the player drew around it). Lets anything that needs to cover "the
        // enclosed floor/ceiling" (see HeartCoreBlockEntity#spawnWallLidOrbs) follow the actual
        // rune outline instead of falling back to its bounding rectangle, which - for anything
        // but a perfect square - includes plenty of area outside the real shape.
        private final Set<Long> interiorXZ;

        private WallShape(Set<BlockPos> wallCells, int area, Set<Long> interiorXZ)
        {
            this.wallCells = wallCells;
            this.area = area;
            this.interiorXZ = interiorXZ;
        }

        /** True if this X/Z column is actually inside the enclosed shape (ring footprint included), not just within its bounding rectangle. */
        public boolean containsColumn(int x, int z)
        {
            return interiorXZ.contains(packXZ(x, z));
        }
    }

    /**
     * The nearest enclosed Purple loop outside {@code center}'s ring, or {@code null} if there
     * isn't one (no outer Purple loop at all, or it isn't closed). Doesn't check adjacency to
     * the inner ring - see {@code HeartCoreBlockEntity} for that, since rejecting on adjacency
     * happens after this returns a real shape.
     */
    @Nullable
    public static WallShape findOuterWallShape(Level level, BlockPos center)
    {
        int startX = center.getX() + START_OFFSET;
        int startZ = center.getZ();
        if (findRuneY(level, startX, startZ, center.getY()) != null)
        {
            // The ring itself (or its corners) reaching this close would be a different bug
            // entirely, but bail out rather than flood-fill from inside a wall either way.
            return null;
        }

        Set<Long> visitedOpen = new HashSet<>();
        Set<BlockPos> wallCells = new HashSet<>();
        Set<Long> wallXZ = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        visitedOpen.add(packXZ(startX, startZ));
        queue.add(new int[] {startX, startZ});

        while (!queue.isEmpty())
        {
            if (visitedOpen.size() > MAX_FLOOD_CELLS)
            {
                return null;
            }
            int[] cell = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                int nx = cell[0] + direction.getStepX();
                int nz = cell[1] + direction.getStepZ();
                long key = packXZ(nx, nz);
                if (visitedOpen.contains(key) || wallXZ.contains(key))
                {
                    continue;
                }
                if (Math.abs(nx - center.getX()) <= INNER_FOOTPRINT_RADIUS && Math.abs(nz - center.getZ()) <= INNER_FOOTPRINT_RADIUS)
                {
                    // Inside the inner ring's own 5x5 footprint - never a candidate for either
                    // the interior area or the outer wall, so leave it out of both entirely
                    // rather than let the ring's own solid-Purple cells masquerade as the wall
                    // the player actually drew outside it.
                    continue;
                }
                Integer runeY = findRuneY(level, nx, nz, center.getY());
                if (runeY != null)
                {
                    wallXZ.add(key);
                    wallCells.add(new BlockPos(nx, runeY, nz));
                }
                else
                {
                    visitedOpen.add(key);
                    queue.add(new int[] {nx, nz});
                }
            }
        }

        if (wallCells.isEmpty())
        {
            return null;
        }

        Set<Long> interiorXZ = new HashSet<>(visitedOpen);
        for (int dx = -INNER_FOOTPRINT_RADIUS; dx <= INNER_FOOTPRINT_RADIUS; dx++)
        {
            for (int dz = -INNER_FOOTPRINT_RADIUS; dz <= INNER_FOOTPRINT_RADIUS; dz++)
            {
                interiorXZ.add(packXZ(center.getX() + dx, center.getZ() + dz));
            }
        }

        return new WallShape(closeDiagonalGaps(wallCells), visitedOpen.size(), interiorXZ);
    }

    /**
     * A physical entity moves through continuous space, not cell-by-cell - so two wall cells
     * that only touch at a shared *corner* (diagonally adjacent, like a checkerboard pair) still
     * leave a real gap a player or mob can walk straight through diagonally, even though the
     * flood fill above already correctly recognizes the interior as fully enclosed (4-directional
     * flood movement can't hop between two diagonal cells at all, so it never actually leaks
     * through a pinch like this - "enclosed" and "physically walled with no gaps" turned out to
     * be two different questions). For every diagonal pair of wall cells where *neither* of the
     * two cells that would flank the corner is already a wall cell itself, one of those flanking
     * cells is added too, closing the corner - the same kind of fix a grid-based game usually
     * needs wherever diagonal movement can cut through a single-cell-wide diagonal wall.
     */
    private static Set<BlockPos> closeDiagonalGaps(Set<BlockPos> wallCells)
    {
        Map<Long, BlockPos> byXZ = new HashMap<>();
        for (BlockPos cell : wallCells)
        {
            byXZ.put(packXZ(cell.getX(), cell.getZ()), cell);
        }

        Set<BlockPos> additions = new HashSet<>();
        for (BlockPos cell : wallCells)
        {
            for (int dx : new int[] {-1, 1})
            {
                for (int dz : new int[] {-1, 1})
                {
                    BlockPos diagonal = byXZ.get(packXZ(cell.getX() + dx, cell.getZ() + dz));
                    if (diagonal == null)
                    {
                        continue;
                    }
                    boolean flank1 = byXZ.containsKey(packXZ(cell.getX() + dx, cell.getZ()));
                    boolean flank2 = byXZ.containsKey(packXZ(cell.getX(), cell.getZ() + dz));
                    if (flank1 || flank2)
                    {
                        continue;
                    }
                    int avgY = (cell.getY() + diagonal.getY()) / 2;
                    additions.add(new BlockPos(cell.getX() + dx, avgY, cell.getZ()));
                }
            }
        }

        if (additions.isEmpty())
        {
            return wallCells;
        }
        Set<BlockPos> closed = new HashSet<>(wallCells);
        closed.addAll(additions);
        return closed;
    }

    /** True if any cell in {@code wallCells} is (8-directionally) adjacent to one of {@code center}'s 12 inner ring positions, ignoring Y - the outer loop can sit at a different height than the ring it surrounds. */
    public static boolean touchesInnerRing(BlockPos center, Set<BlockPos> wallCells)
    {
        Set<Long> wallXZ = new HashSet<>();
        for (BlockPos cell : wallCells)
        {
            wallXZ.add(packXZ(cell.getX(), cell.getZ()));
        }
        for (int[] offset : MagicCircleRitual.RING_OFFSETS)
        {
            int ringX = center.getX() + offset[0];
            int ringZ = center.getZ() + offset[1];
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0)
                    {
                        continue;
                    }
                    if (wallXZ.contains(packXZ(ringX + dx, ringZ + dz)))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Searches a short vertical window centered on {@code centerY} for a Purple rune at this X/Z column, closest to {@code centerY} first. */
    @Nullable
    private static Integer findRuneY(Level level, int x, int z, int centerY)
    {
        if (isPurpleRune(level, x, centerY, z))
        {
            return centerY;
        }
        for (int dy = 1; dy <= VERTICAL_SEARCH_RANGE; dy++)
        {
            if (isPurpleRune(level, x, centerY + dy, z))
            {
                return centerY + dy;
            }
            if (isPurpleRune(level, x, centerY - dy, z))
            {
                return centerY - dy;
            }
        }
        return null;
    }

    private static boolean isPurpleRune(Level level, int x, int y, int z)
    {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.is(ModBlocks.MAGIC_CIRCLE.get()) && state.getValue(MagicCircleBlock.COLOR) == RuneColor.PURPLE;
    }

    private static long packXZ(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
