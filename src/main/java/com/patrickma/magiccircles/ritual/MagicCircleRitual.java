package com.patrickma.magiccircles.ritual;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Shared geometry for "a complete magic circle" - a bird's-eye 5x5 footprint (0 = empty,
 * 1 = a rune) that always has something at its center too (a rune, or a Heart Core sitting
 * where a rune used to be):
 *
 * <pre>
 * 0 1 1 1 0
 * 1 0 0 0 1
 * 1 0 X 0 1
 * 1 0 0 0 1
 * 0 1 1 1 0
 * </pre>
 *
 * 12 ring positions ({@link #RING_OFFSETS}) plus the center (X) - 13 blocks total. The 4
 * corners are checked too, not just left alone - they have to actually be empty, which is
 * what keeps this shape from also matching a completed {@link PortalRitual} (its pattern
 * fills those exact corners with redstone). Callers are expected to check the center block
 * themselves, since what's valid there differs by caller (a rune drawing a new one, a Heart
 * Core checking it's still standing in a real circle, etc).
 */
public final class MagicCircleRitual
{
    public static final int[][] RING_OFFSETS = {
            {-1, -2}, {0, -2}, {1, -2},
            {-2, -1}, {2, -1},
            {-2, 0}, {2, 0},
            {-2, 1}, {2, 1},
            {-1, 2}, {0, 2}, {1, 2},
    };

    // The 4 true corners of the 5x5 footprint - deliberately outside RING_OFFSETS, since this
    // shape is meant to read as a rounded circle, not a full square border. They need to be
    // checked explicitly (not just ignored) because PortalRitual's pattern fills exactly these
    // 4 spots with redstone - without this check, any completed portal pattern also satisfied
    // this "rounded ring" (its 12 positions are a strict subset of the portal's), so building a
    // portal always got misread as a complete storm circle too. Requiring these to be empty is
    // what actually makes the two shapes mutually exclusive.
    private static final int[][] CORNER_OFFSETS = {
            {-2, -2}, {2, -2}, {-2, 2}, {2, 2},
    };

    private MagicCircleRitual()
    {
    }

    /**
     * True if all 12 ring positions around {@code center} (same Y) are magic circle runes and
     * the 4 corners just outside them are empty. Does not check {@code center} itself. Doesn't
     * care about color - a mixed-color ring counts as "complete" here, just not as any single
     * spell's ring (see {@link #hasCompleteRingOfColor}).
     */
    public static boolean hasCompleteRing(BlockGetter level, BlockPos center)
    {
        for (int[] offset : RING_OFFSETS)
        {
            BlockPos ringPos = center.offset(offset[0], 0, offset[1]);
            if (!level.getBlockState(ringPos).is(ModBlocks.MAGIC_CIRCLE.get()))
            {
                return false;
            }
        }
        for (int[] offset : CORNER_OFFSETS)
        {
            if (!level.getBlockState(center.offset(offset[0], 0, offset[1])).isAir())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * How many of each {@link RuneColor} make up {@code center}'s 12 ring runes - {@code null}
     * if the ring isn't complete at all. A solid ring comes back as one entry (count 12); a
     * two-color ring as two entries. Doesn't care about arrangement, only how many of each
     * color - see {@link HeartSpell#comboFor} for why that's the part that actually matters.
     */
    @Nullable
    public static Map<RuneColor, Integer> ringColorCounts(BlockGetter level, BlockPos center)
    {
        if (!hasCompleteRing(level, center))
        {
            return null;
        }
        Map<RuneColor, Integer> counts = new EnumMap<>(RuneColor.class);
        for (int[] offset : RING_OFFSETS)
        {
            BlockState state = level.getBlockState(center.offset(offset[0], 0, offset[1]));
            counts.merge(state.getValue(MagicCircleBlock.COLOR), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Which spell (if any) this ring casts - {@code null} if the ring isn't complete, or its
     * color composition doesn't match any known spell. A ring that's entirely one color casts
     * that color's solo spell ({@link HeartSpell#soloFor}); a ring split exactly 6-and-6
     * between two colors, in any arrangement (position doesn't matter, only the count), casts
     * that pair's combo spell ({@link HeartSpell#comboFor}) - not every one of the 10 possible
     * pairs has a spell yet, so this can still come back {@code null} even for a clean 6-6
     * split. Anything else (three-plus colors, an uneven split) isn't recognized at all.
     */
    @Nullable
    public static HeartSpell detectSpell(BlockGetter level, BlockPos center)
    {
        Map<RuneColor, Integer> counts = ringColorCounts(level, center);
        if (counts == null)
        {
            return null;
        }
        if (counts.size() == 1)
        {
            return HeartSpell.soloFor(counts.keySet().iterator().next());
        }
        if (counts.size() == 2)
        {
            Iterator<Map.Entry<RuneColor, Integer>> it = counts.entrySet().iterator();
            Map.Entry<RuneColor, Integer> first = it.next();
            Map.Entry<RuneColor, Integer> second = it.next();
            if (first.getValue() == 6 && second.getValue() == 6)
            {
                return HeartSpell.comboFor(first.getKey(), second.getKey());
            }
        }
        return null;
    }

    /**
     * True if {@code pos} - a magic circle rune - currently takes part in <em>any</em>
     * complete circle, whether as that circle's center or as one of its 12 ring members.
     * Used purely for cosmetics (see {@code MagicCircleBlockEntity}): a rune that's part of
     * a working circle stops glowing on its own and lets the circle's shared "dancing wisp"
     * effect (owned by whichever block is at the center) speak for it instead.
     */
    public static boolean isPartOfCompleteCircle(BlockGetter level, BlockPos pos)
    {
        if (hasCompleteRing(level, pos))
        {
            return true;
        }
        for (int[] offset : RING_OFFSETS)
        {
            BlockPos possibleCenter = pos.offset(-offset[0], 0, -offset[1]);
            if (isCenterBlock(level.getBlockState(possibleCenter)) && hasCompleteRing(level, possibleCenter))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isCenterBlock(BlockState state)
    {
        return state.is(ModBlocks.MAGIC_CIRCLE.get()) || state.is(ModBlocks.HEART_CORE.get());
    }
}
