package com.patrickma.magiccircles.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * A generic "a prolonged spell is running here" indicator, entirely separate from
 * {@link ClientCircleWisps}'s per-rune colored wisps: plain white wisps that live at the
 * Heart Core itself. Idle, they hover tucked in close to the heart; the moment the spell they
 * track is active they ease out into a slow orbit around the *circle's* outside, well beyond
 * the 12-rune ring, then ease back in the moment it ends - covering every duration-based spell
 * (storm, shield, the tempest ward, the mana font, the vigor buff) with one shared mechanism
 * rather than something bespoke per spell, since "is something ongoing happening here" is the
 * same question regardless of which spell it is. One-shot spells (flowers, heal, XP, ...)
 * never touch this at all - they're over before a slow fly-out would even finish playing.
 *
 * <p>Since one heart can now run two prolonged spells at once (see
 * {@code HeartCoreBlockEntity#activeSpellCount}), there are two independent rings of these:
 * {@link #tick} - the original 6, right at the heart's own height - tracks "at least one spell
 * is active," and {@link #tickSecondary} - a second, smaller ring of 3, one block higher and
 * orbiting the *opposite* direction so the two read as clearly distinct rather than one messy
 * crowd - tracks "a *second* spell is active alongside the first." Both share the exact same
 * dock/guard/depleted-fall logic, just parameterized by {@link Ring} rather than duplicated.
 *
 * <p>A third state, {@code depleted} (the heart's mana dropped below
 * {@link com.patrickma.magiccircles.ritual.HeartSpell#minManaCost()}), overrides both of the
 * above for either ring: rather than docking or guarding, each wisp waits its turn (staggered
 * by index, so they don't all go at once) and then sinks straight down into the ground at the
 * heart and stops - a visible "this heart is empty" tell. The moment the heart recharges past
 * that threshold, every wisp (grounded or still waiting) is freed to ease back up toward its
 * normal dock/guard position, reading as the wisps climbing back out of the ground.
 */
public final class ClientHeartWisps
{
    private static final double EASE = 0.03;
    private static final double GUARD_RADIUS = 3.2;
    private static final double DOCK_RADIUS = 0.35;
    private static final float PARTICLE_SIZE = 0.7f;
    private static final int TRAIL_STEPS = 3;
    // Each wisp starts falling this many ticks after the previous one, so all of a ring's wisps
    // drop in a staggered sequence rather than all at once.
    private static final double FALL_STAGGER_TICKS = 5.0;
    private static final double FALL_SPEED = 0.07;
    private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);

    private static final Ring PRIMARY = new Ring(6, 0.0, 1.0, new HashMap<>());
    private static final Ring SECONDARY = new Ring(3, 1.0, -1.0, new HashMap<>());

    private ClientHeartWisps()
    {
    }

    /** Call every client tick from a Heart Core, passing whether *any* prolonged spell (and separately, mana depletion) is active. */
    public static void tick(Level level, BlockPos heartPos, boolean active, boolean depleted)
    {
        tick(PRIMARY, level, heartPos, active, depleted);
    }

    /** Same as {@link #tick}, but for the second, smaller ring that only lights up once a *second* prolonged spell joins the first. */
    public static void tickSecondary(Level level, BlockPos heartPos, boolean active, boolean depleted)
    {
        tick(SECONDARY, level, heartPos, active, depleted);
    }

    private static void tick(Ring ring, Level level, BlockPos heartPos, boolean active, boolean depleted)
    {
        HeartWisps state = ring.registry.computeIfAbsent(heartPos.immutable(), p -> new HeartWisps(createWisps(ring, p)));
        Wisp[] wisps = state.wisps;
        double time = level.getGameTime();

        if (depleted != state.wasDepleted)
        {
            state.depletedStartTick = time;
            for (Wisp wisp : wisps)
            {
                wisp.grounded = false;
            }
            state.wasDepleted = depleted;
        }

        for (int i = 0; i < wisps.length; i++)
        {
            Wisp wisp = wisps[i];

            if (depleted)
            {
                if (wisp.grounded)
                {
                    continue;
                }

                double elapsed = time - state.depletedStartTick;
                if (elapsed < i * FALL_STAGGER_TICKS)
                {
                    tickToward(level, wisp, dockTarget(ring, heartPos, i, time), EASE);
                    continue;
                }

                double fromX = wisp.x;
                double fromY = wisp.y;
                double fromZ = wisp.z;
                double groundY = heartPos.getY();
                wisp.y = Math.max(groundY, wisp.y - FALL_SPEED);
                if (wisp.y <= groundY)
                {
                    wisp.grounded = true;
                }
                emitTrail(level, fromX, fromY, fromZ, wisp.x, wisp.y, wisp.z);
                continue;
            }

            double[] target = active ? guardTarget(ring, heartPos, i, time) : dockTarget(ring, heartPos, i, time);
            tickToward(level, wisp, target, EASE);
        }
    }

    private static void tickToward(Level level, Wisp wisp, double[] target, double ease)
    {
        double fromX = wisp.x;
        double fromY = wisp.y;
        double fromZ = wisp.z;
        wisp.x += (target[0] - wisp.x) * ease;
        wisp.y += (target[1] - wisp.y) * ease;
        wisp.z += (target[2] - wisp.z) * ease;
        emitTrail(level, fromX, fromY, fromZ, wisp.x, wisp.y, wisp.z);
    }

    private static void emitTrail(Level level, double fromX, double fromY, double fromZ, double toX, double toY, double toZ)
    {
        for (int step = 1; step <= TRAIL_STEPS; step++)
        {
            double t = (double) step / TRAIL_STEPS;
            level.addParticle(new DustParticleOptions(WHITE, PARTICLE_SIZE),
                    fromX + (toX - fromX) * t,
                    fromY + (toY - fromY) * t,
                    fromZ + (toZ - fromZ) * t,
                    0.0, 0.0, 0.0);
        }
    }

    private static Wisp[] createWisps(Ring ring, BlockPos heartPos)
    {
        Wisp[] wisps = new Wisp[ring.wispCount];
        for (int i = 0; i < wisps.length; i++)
        {
            wisps[i] = new Wisp(heartPos.getX() + 0.5, heartPos.getY() + 1.5 + ring.yOffset, heartPos.getZ() + 0.5);
        }
        return wisps;
    }

    /** Tucked in close, in a tight little orbit right at the heart, while nothing is casting. */
    private static double[] dockTarget(Ring ring, BlockPos heartPos, int index, double time)
    {
        double phase = index * (Math.PI * 2.0 / ring.wispCount);
        double angle = phase + time * 0.03 * ring.direction;
        double x = heartPos.getX() + 0.5 + DOCK_RADIUS * Math.cos(angle);
        double y = heartPos.getY() + 1.5 + ring.yOffset + DOCK_RADIUS * Math.sin(angle * 1.3);
        double z = heartPos.getZ() + 0.5 + DOCK_RADIUS * Math.sin(angle);
        return new double[] {x, y, z};
    }

    /** A slow patrol around the outside of the whole circle - well past the 12-rune ring. */
    private static double[] guardTarget(Ring ring, BlockPos heartPos, int index, double time)
    {
        double phase = index * (Math.PI * 2.0 / ring.wispCount);
        double angle = phase + time * 0.015 * ring.direction;
        double x = heartPos.getX() + 0.5 + GUARD_RADIUS * Math.cos(angle);
        double y = heartPos.getY() + 0.5 + ring.yOffset + 0.2 * Math.sin(time * 0.05 + phase);
        double z = heartPos.getZ() + 0.5 + GUARD_RADIUS * Math.sin(angle);
        return new double[] {x, y, z};
    }

    /** One ring's fixed shape: how many wisps, how high above the primary ring, and which way it turns. */
    private static final class Ring
    {
        final int wispCount;
        final double yOffset;
        final double direction;
        final Map<BlockPos, HeartWisps> registry;

        Ring(int wispCount, double yOffset, double direction, Map<BlockPos, HeartWisps> registry)
        {
            this.wispCount = wispCount;
            this.yOffset = yOffset;
            this.direction = direction;
            this.registry = registry;
        }
    }

    private static final class Wisp
    {
        double x;
        double y;
        double z;
        boolean grounded;

        Wisp(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** A ring's wisps, plus the bookkeeping needed to stagger their fall when depleted. */
    private static final class HeartWisps
    {
        final Wisp[] wisps;
        boolean wasDepleted;
        double depletedStartTick;

        HeartWisps(Wisp[] wisps)
        {
            this.wisps = wisps;
        }
    }
}
