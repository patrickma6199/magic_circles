package com.patrickma.magiccircles.block.entity;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.client.ClientCircleWisps;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Holds the animation state for a placed {@link com.patrickma.magiccircles.block.MagicCircleBlock}.
 *
 * <p>A rune that isn't part of any working circle just glows quietly where it sits (see
 * {@link #spawnStationaryGlow}), colored by which chalk drew it (see {@code RuneColor}).
 * Once it becomes part of a complete circle (see {@link MagicCircleRitual}) - whether as the
 * center or one of the 12 ring members - that stationary glow stops; instead, whichever rune
 * actually <em>is</em> the center hands the whole circle off to {@link ClientCircleWisps},
 * which flies one colored wisp per ring rune loosely over the circle's footprint. If a Heart
 * Core later takes this rune's center position, {@link HeartCoreBlockEntity} takes over the
 * same wisps and tightens them into an orbit instead; if the ring is ever broken, whichever of
 * the two is active tells {@link ClientCircleWisps} to forget the circle, and any now-isolated
 * runes go back to glowing individually.
 *
 * <p>Purely client-side cosmetics now - there's no server-side ticking left to do here.
 * Checking for a completed {@code PortalRitual} pattern used to happen here too, but opening a
 * portal is a Heart Core spell like any other now (costs mana - see
 * {@code HeartCoreBlock#interact}), so that check moved to {@link HeartCoreBlockEntity} along
 * with everything else a Fairy Horn can cast.
 */
public class MagicCircleBlockEntity extends BlockEntity
{
    // All 20 rune variants share this same anchor point for their topmost stroke (see
    // gen_runes.py) - the stationary glow floats directly above it, dead center of the block.
    private static final double GLOW_LOCAL_X = 0.5;
    private static final double GLOW_LOCAL_Z = 0.5;
    private static final int RING_CHECK_INTERVAL = 10;

    private int age = 0;
    private boolean isCenterOfCompleteCircle;
    private boolean partOfCompleteCircle;

    public MagicCircleBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.MAGIC_CIRCLE.get(), pos, state);
    }

    /** Runs once per client tick for every loaded magic circle. See MagicCircleBlock#getTicker. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, MagicCircleBlockEntity blockEntity)
    {
        blockEntity.age++;

        if (blockEntity.age % RING_CHECK_INTERVAL == 0)
        {
            boolean wasCenter = blockEntity.isCenterOfCompleteCircle;
            blockEntity.isCenterOfCompleteCircle = MagicCircleRitual.hasCompleteRing(level, pos);
            blockEntity.partOfCompleteCircle = blockEntity.isCenterOfCompleteCircle
                    || MagicCircleRitual.isPartOfCompleteCircle(level, pos);

            if (wasCenter && !blockEntity.isCenterOfCompleteCircle)
            {
                ClientCircleWisps.remove(pos);
            }
        }

        if (blockEntity.isCenterOfCompleteCircle)
        {
            ClientCircleWisps.tick(level, pos);
        }
        else if (!blockEntity.partOfCompleteCircle && blockEntity.age % 3 == 0)
        {
            spawnStationaryGlow(level, pos, state);
        }
    }

    private static void spawnStationaryGlow(Level level, BlockPos pos, BlockState state)
    {
        double x = pos.getX() + GLOW_LOCAL_X + (level.random.nextDouble() - 0.5) * 0.05;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + GLOW_LOCAL_Z + (level.random.nextDouble() - 0.5) * 0.05;
        Vector3f color = state.getValue(MagicCircleBlock.COLOR).wispColor();
        level.addParticle(new DustParticleOptions(color, 0.8f), x, y, z, 0.0, 0.02, 0.0);
    }
}
