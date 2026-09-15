package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.block.RuneColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Wisps circling something the way electrons are drawn around a nucleus: one per colour, each on
 * its own tilted plane and in its own direction, so from any angle there is always one sweeping
 * past. The Fairy Queen's ({@link QueenWisps}) and a Blessed player's ({@link BlessedWispOrbit})
 * are the same orbits, at different sizes.
 *
 * <p>Each orbit is a circle in the plane of two perpendicular directions ({@code u}, {@code v}), both
 * square to that orbit's own tilted axis. The axes are spread around the compass and leaned at
 * different angles, so no two planes line up.
 */
public final class TiltedOrbits
{
    private static final double BASE_SPEED = 0.09;

    private record Orbit(Vec3 u, Vec3 v, double phase, double speed, Vector3f color)
    {
    }

    private final Orbit[] orbits;
    private final double radius;
    private final float particleSize;
    private final int trailSteps;

    public TiltedOrbits(RuneColor[] colors, double radius, float particleSize, int trailSteps)
    {
        this.radius = radius;
        this.particleSize = particleSize;
        this.trailSteps = trailSteps;
        this.orbits = new Orbit[colors.length];
        for (int i = 0; i < colors.length; i++)
        {
            double tilt = Math.toRadians(40 + (i * 37) % 60);
            double azimuth = Math.PI * 2.0 * i / colors.length;
            Vec3 axis = new Vec3(Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt), Math.sin(tilt) * Math.sin(azimuth));
            Vec3 reference = Math.abs(axis.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
            Vec3 u = axis.cross(reference).normalize();
            Vec3 v = axis.cross(u).normalize();
            double direction = i % 2 == 0 ? 1.0 : -1.0;
            double speed = BASE_SPEED * direction * (0.85 + 0.1 * (i % 3));
            this.orbits[i] = new Orbit(u, v, Math.PI * 2.0 * i / colors.length, speed, colors[i].wispColor());
        }
    }

    public int count()
    {
        return this.orbits.length;
    }

    /** Where wisp {@code i} is at {@code time}, around {@code center}. */
    public Vec3 point(int i, double time, Vec3 center)
    {
        Orbit orbit = this.orbits[i];
        double angle = orbit.phase() + time * orbit.speed();
        return center.add(orbit.u().scale(Math.cos(angle) * this.radius)).add(orbit.v().scale(Math.sin(angle) * this.radius));
    }

    /**
     * One tick of every wisp: draws each from where it was last tick to where it is now, so the
     * trails join up however fast the centre moves, and records the new positions in {@code last}
     * (one slot per wisp, null until first drawn).
     */
    public void tick(ClientLevel level, double time, Vec3 center, Vec3[] last)
    {
        tick(level, time, center, last, null);
    }

    /** As {@link #tick(ClientLevel, double, Vec3, Vec3[])}, leaving out every wisp marked {@code away} - its orbit stands empty. */
    public void tick(ClientLevel level, double time, Vec3 center, Vec3[] last, boolean[] away)
    {
        for (int i = 0; i < this.orbits.length; i++)
        {
            if (away != null && away[i])
            {
                last[i] = null;
                continue;
            }
            Vec3 point = point(i, time, center);
            Vec3 from = last[i] == null ? point : last[i];
            for (int step = 1; step <= this.trailSteps; step++)
            {
                Vec3 at = from.lerp(point, (double) step / this.trailSteps);
                level.addParticle(new DustParticleOptions(this.orbits[i].color(), this.particleSize), at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
            last[i] = point;
        }
    }
}
