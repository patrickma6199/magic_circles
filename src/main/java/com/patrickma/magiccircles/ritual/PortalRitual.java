package com.patrickma.magiccircles.ritual;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The second ritual shape - a 5x5 pattern of redstone dust and both chalk colors around a
 * chalk-rune center, read bird's-eye (0 = must be empty, r = redstone dust, n = a *regular*
 * rune, g = a *gold* rune, a = either color rune - only the center allows either):
 *
 * <pre>
 * r n g n r
 * g 0 0 0 g
 * n 0 a 0 n
 * g 0 0 0 g
 * r n g n r
 * </pre>
 *
 * Unlike {@link MagicCircleRitual}'s rounded ring, this one uses the full 5x5 border. The
 * whole pattern is a palindrome in both directions, so checking it "as drawn" and again with
 * rows/columns swapped covers being built along either horizontal axis, without needing to
 * try all four rotations separately.
 *
 * <p>The center ('a') accepts either a plain rune (any color, before a Heartstone's been used
 * on it) or a Heart Core (after) - opening the portal itself is a Heart Core spell like any
 * other now (see {@code HeartCoreBlock#interact}), so the pattern needs to keep matching once
 * the center rune has actually become one.
 */
public final class PortalRitual
{
    private static final char[][] PATTERN = {
            {'r', 'n', 'g', 'n', 'r'},
            {'g', '0', '0', '0', 'g'},
            {'n', '0', 'a', '0', 'n'},
            {'g', '0', '0', '0', 'g'},
            {'r', 'n', 'g', 'n', 'r'},
    };

    private PortalRitual()
    {
    }

    /** True if a valid portal pattern is centered on {@code center}, in either horizontal orientation. */
    public static boolean matches(BlockGetter level, BlockPos center)
    {
        return matches(level, center, false, false) || matches(level, center, true, false);
    }

    /**
     * Same border requirements (the {@code r}/{@code n}/{@code g} cells) as {@link #matches},
     * but doesn't care what's in the 8 {@code 0} cells immediately around center at all. Once
     * the portal spell's water precondition has been dug and filled there (see
     * {@code FairyPortalManager#hasWaterPit}), those cells hold water rather than air, which
     * {@link #matches} would reject - this is what {@code HeartCoreBlock#interact} actually
     * checks for casting the spell, since by the time anyone's ready to cast it, those cells
     * are never still empty.
     */
    public static boolean matchesBorder(BlockGetter level, BlockPos center)
    {
        return matches(level, center, false, true) || matches(level, center, true, true);
    }

    private static boolean matches(BlockGetter level, BlockPos center, boolean transposed, boolean ignoreCenterRing)
    {
        for (int row = 0; row < PATTERN.length; row++)
        {
            for (int col = 0; col < PATTERN[row].length; col++)
            {
                char required = PATTERN[row][col];
                if (ignoreCenterRing && required == '0')
                {
                    continue;
                }
                int dx = col - 2;
                int dz = row - 2;
                if (transposed)
                {
                    int swap = dx;
                    dx = dz;
                    dz = swap;
                }
                if (!matchesCell(level.getBlockState(center.offset(dx, 0, dz)), required))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesCell(BlockState state, char required)
    {
        return switch (required)
        {
            case '0' -> state.isAir();
            case 'r' -> state.is(Blocks.REDSTONE_WIRE);
            case 'n' -> state.is(ModBlocks.MAGIC_CIRCLE.get()) && state.getValue(MagicCircleBlock.COLOR) == RuneColor.BLUE;
            case 'g' -> state.is(ModBlocks.MAGIC_CIRCLE.get()) && state.getValue(MagicCircleBlock.COLOR) == RuneColor.GOLD;
            case 'a' -> state.is(ModBlocks.MAGIC_CIRCLE.get()) || state.is(ModBlocks.HEART_CORE.get());
            default -> false;
        };
    }
}
