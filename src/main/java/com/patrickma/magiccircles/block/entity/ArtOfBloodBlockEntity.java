package com.patrickma.magiccircles.block.entity;

import com.patrickma.magiccircles.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The placed Art of Blood. It lies flat and shut, opening only for someone who has paid to read it
 * ({@link ReadableBookBlockEntity}) - and leaks the occasional wisp of soul-smoke either way.
 */
public class ArtOfBloodBlockEntity extends ReadableBookBlockEntity
{
    private static final int SMOKE_INTERVAL_TICKS = 25;
    private static final double RESTING_HEIGHT = 0.22;
    /** Flat on its back - see {@code client/ArtOfBloodBlockEntityRenderer}. */
    private static final float RESTING_TILT = 90.0F;

    public ArtOfBloodBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.ART_OF_BLOOD.get(), pos, state);
    }

    @Override
    public float restingTilt()
    {
        return RESTING_TILT;
    }

    @Override
    public double restingHeight()
    {
        return RESTING_HEIGHT;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ArtOfBloodBlockEntity blockEntity)
    {
        blockEntity.tickClient(level);
        if (blockEntity.getAge() % SMOKE_INTERVAL_TICKS != 0)
        {
            return;
        }
        double x = pos.getX() + 0.35 + level.random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.35 + level.random.nextDouble() * 0.3;
        double y = pos.getY() + 0.25 + blockEntity.lift(1.0F);
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.008, 0.0);
        if (level.random.nextInt(3) == 0)
        {
            level.addParticle(ParticleTypes.SCULK_SOUL, x, y + 0.05, z, 0.0, 0.01, 0.0);
        }
    }
}
