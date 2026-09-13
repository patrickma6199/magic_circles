package com.patrickma.magiccircles.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * A single, stationary wisp hovering above a placed Book of the Faye's open pages - not orbiting,
 * not flying anywhere, just a quiet white light marking "this book is open and readable," gently
 * bobbing in place. Called directly from {@code BookOfTheFayeBlockEntity#clientTick}, one call per
 * placed book (there's no shared per-block state to key by, unlike the mod's other wisp systems -
 * a single fixed point needs nothing more than its own position each tick).
 */
public final class BookOfTheFayeWisp
{
    private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final double HEIGHT_ABOVE_BLOCK = 0.9;
    private static final double BOB_AMPLITUDE = 0.05;
    private static final float PARTICLE_SIZE = 0.9f;

    private BookOfTheFayeWisp()
    {
    }

    public static void tick(Level level, BlockPos pos)
    {
        double time = level.getGameTime();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + HEIGHT_ABOVE_BLOCK + BOB_AMPLITUDE * Math.sin(time * 0.04);
        double z = pos.getZ() + 0.5;
        level.addParticle(new DustParticleOptions(WHITE, PARTICLE_SIZE), x, y, z, 0.0, 0.0, 0.0);
    }
}
