package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import com.patrickma.magiccircles.ritual.WispChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One flying, colored wisp per ring rune (12 per complete circle - see
 * {@link MagicCircleRitual}), owned by whichever block is currently at the circle's center.
 * While the center is a plain rune, the wisps wander loosely over the whole circle; the
 * moment it's a Heart Core instead, they ease into an orbit around it - and because each
 * wisp's position is tracked here rather than reset per block entity, that transition (and
 * the reverse one, if the heart later turns back into a rune) plays out as a smooth flight
 * rather than a teleport, even though the block *entity* ticking the circle's center changes
 * identity in the middle of it. While a spell is channeling (see {@link WispChannel}), the
 * heart's orbit target is overridden entirely - e.g. the storm spell sends them {@link WispChannel#UP}.
 *
 * <p>Each wisp leaves a short trail: rather than one particle at its current point, several
 * are laid down along the segment it just moved through, so a fast-moving wisp reads as a
 * continuous thread instead of a dotted line.
 *
 * <p>This is a completely separate system from {@link ClientHeartWisps}, the fixed set of 6
 * white wisps that show a *prolonged* spell (storm, shield, ...) is active at all - these
 * ring wisps exist for every complete circle regardless of whether anything is casting, and
 * are colored by the ring runes themselves rather than always white.
 *
 * <p>Purely cosmetic and purely client-side: nothing here is saved, and losing an entry (say,
 * on world unload) just means the wisps re-appear at their home runes next time the circle is
 * ticked, same as when it was first built.
 *
 * <p>A heart can now run two spells at once (see {@code HeartCoreBlockEntity#activeSpellCount}),
 * which opens up a specific case worth handling deliberately: casting one spell, then repainting
 * the ring to cast a second one while a wisp from the *first* cast is still mid-burst (flying
 * out to a heal target, say) and hasn't rejoined the ring yet. By the time it would, its home
 * rune is a different color than when it left. Rather than let it just snap into place showing
 * a color that doesn't match what it was doing, each wisp remembers the color its home rune had
 * the last time it was calmly orbiting ({@link Wisp#restColor}); the moment a burst ends and
 * that no longer matches the rune's current color, it fades out and back in
 * ({@link Wisp#fadeTicks}) instead of reappearing abruptly - by the time it's fully back, it's
 * already showing its new color.
 */
public final class ClientCircleWisps
{
    private static final double EASE = 0.04;
    // TO_PLAYER and TO_TARGETS represent something already resolved server-side (the orb/heal
    // already happened) - they need to visibly catch up fast, or the burst reads as sluggish
    // next to an effect that's already over. Every other channel keeps the slower base EASE.
    private static final double FAST_EASE = 0.14;
    private static final double ORBIT_RADIUS_MIN = 1.1;
    private static final double ORBIT_RADIUS_MAX = 1.9;
    private static final double CHANNEL_REACH = 40.0;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int TRAIL_STEPS = 4;
    // How long the fade-out-then-back-in plays when a returning wisp finds its home rune
    // repainted - a full round trip (shrink to nothing, then grow back), not just a fade-out.
    private static final int FADE_TICKS = 20;
    // Golden-ratio spread keeps 12 tilts spaced apart with no near-duplicates or symmetry -
    // avoids some wisps ending up on (near-)parallel planes, which is what read as "rings".
    private static final double GOLDEN_ANGLE = 2.399963229728653;
    private static final int[] NO_TARGETS = new int[0];

    private static final Map<BlockPos, Wisp[]> CIRCLES = new HashMap<>();

    private ClientCircleWisps()
    {
    }

    /** Call every client tick from a rune acting as a circle's center - always plain wandering. */
    public static void tick(Level level, BlockPos center)
    {
        tick(level, center, WispChannel.ORBIT, NO_TARGETS, false);
    }

    /** Call every client tick from a Heart Core, passing its current (synced) wisp channel. */
    public static void tick(Level level, BlockPos center, WispChannel channel)
    {
        tick(level, center, channel, NO_TARGETS, false);
    }

    /**
     * Same as {@link #tick(Level, BlockPos, WispChannel)}, but for {@link WispChannel#TO_TARGETS}:
     * wisp {@code i} flies toward the entity with id {@code targetEntityIds[i]} (if it exists
     * and is loaded), rather than a shared destination. Wisps beyond the target list's length
     * (fewer targets than 12 ring wisps) just orbit instead of sitting idle at the heart.
     *
     * @param depleted when true, the center is a Heart Core with too little mana left to cast
     *                 anything - treated the same as "not a heart yet" so the ring wisps drift
     *                 back to loose wandering instead of orbiting a heart that can't do anything.
     */
    public static void tick(Level level, BlockPos center, WispChannel channel, int[] targetEntityIds, boolean depleted)
    {
        tick(level, center, channel, targetEntityIds, depleted, null, 0, 0);
    }

    /** Same as the 5-arg {@link #tick}, but also carrying the Shield spell's own real boundary data (see {@link WispChannel#TRACE_SHIELD}) - {@code wallFootprint} null means the plain sphere. */
    public static void tick(Level level, BlockPos center, WispChannel channel, int[] targetEntityIds, boolean depleted,
                             @org.jetbrains.annotations.Nullable java.util.Set<BlockPos> wallFootprint, int wallBottomY, int wallTopY)
    {
        Wisp[] wisps = CIRCLES.computeIfAbsent(center.immutable(), c -> createWisps(level, c));
        boolean isHeart = !depleted && level.getBlockState(center).is(ModBlocks.HEART_CORE.get());
        double time = level.getGameTime();
        double ease = (channel == WispChannel.TO_PLAYER || channel == WispChannel.TO_TARGETS) ? FAST_EASE : EASE;

        for (int i = 0; i < wisps.length; i++)
        {
            Wisp wisp = wisps[i];
            Vector3f currentColor = colorFor(level, center, i);

            // A burst (anything but ORBIT) just ended - if this wisp's home rune has since been
            // repainted to a different color than it had the last time this wisp was calmly
            // orbiting, fade it out and back in rather than let it snap straight to the new color.
            boolean burstJustEnded = isHeart && channel == WispChannel.ORBIT && wisp.lastChannel != WispChannel.ORBIT;
            if (burstJustEnded && wisp.restColor != null && !wisp.restColor.equals(currentColor))
            {
                wisp.fadeTicks = FADE_TICKS;
            }
            wisp.lastChannel = isHeart ? channel : WispChannel.ORBIT;
            if (!isHeart || channel == WispChannel.ORBIT)
            {
                wisp.restColor = currentColor;
            }

            double[] target;
            if (!isHeart)
            {
                target = danceTarget(center, i, wisps.length, time);
            }
            else if (channel == WispChannel.ORBIT)
            {
                target = orbitTarget(center, i, wisps.length, time);
            }
            else
            {
                target = channelTarget(channel, level, center, wisp, i, wisps.length, time, targetEntityIds, wallFootprint, wallBottomY, wallTopY);
            }

            double fromX = wisp.x;
            double fromY = wisp.y;
            double fromZ = wisp.z;
            wisp.x += (target[0] - wisp.x) * ease;
            wisp.y += (target[1] - wisp.y) * ease;
            wisp.z += (target[2] - wisp.z) * ease;

            float particleSize = PARTICLE_SIZE;
            if (wisp.fadeTicks > 0)
            {
                wisp.fadeTicks--;
                float t = wisp.fadeTicks / (float) FADE_TICKS;
                // Never quite zero - some particle types reject a non-positive size outright,
                // and this is already small enough to read as "gone" at the fade's midpoint.
                particleSize = Math.max(0.01f, particleSize * Math.abs(2.0f * t - 1.0f));
            }

            for (int step = 1; step <= TRAIL_STEPS; step++)
            {
                double t = (double) step / TRAIL_STEPS;
                level.addParticle(new DustParticleOptions(currentColor, particleSize),
                        fromX + (wisp.x - fromX) * t,
                        fromY + (wisp.y - fromY) * t,
                        fromZ + (wisp.z - fromZ) * t,
                        0.0, 0.0, 0.0);
            }
        }
    }

    /** Call once a circle's ring is found broken, so a rebuilt circle later starts fresh. */
    public static void remove(BlockPos center)
    {
        CIRCLES.remove(center);
    }

    private static Wisp[] createWisps(Level level, BlockPos center)
    {
        Wisp[] wisps = new Wisp[MagicCircleRitual.RING_OFFSETS.length];
        for (int i = 0; i < wisps.length; i++)
        {
            int[] offset = MagicCircleRitual.RING_OFFSETS[i];
            Wisp wisp = new Wisp(
                    center.getX() + offset[0] + 0.5,
                    center.getY() + 0.3,
                    center.getZ() + offset[1] + 0.5);
            wisp.restColor = colorFor(level, center, i);
            wisps[i] = wisp;
        }
        return wisps;
    }

    private static Vector3f colorFor(Level level, BlockPos center, int index)
    {
        int[] offset = MagicCircleRitual.RING_OFFSETS[index];
        BlockPos ringPos = center.offset(offset[0], 0, offset[1]);
        BlockState state = level.getBlockState(ringPos);
        RuneColor color = state.is(ModBlocks.MAGIC_CIRCLE.get()) ? state.getValue(MagicCircleBlock.COLOR) : RuneColor.BLUE;
        return color.wispColor();
    }

    /** Loose, organic wandering somewhere over the circle's whole footprint, one phase per wisp. */
    private static double[] danceTarget(BlockPos center, int index, int count, double time)
    {
        double phase = index * (Math.PI * 2.0 / count);
        double radius = 1.2 + 0.8 * Math.sin(time * 0.007 + phase * 1.7);
        double angle = phase + time * 0.004 + Math.sin(time * 0.01 + phase) * 0.6;
        double x = center.getX() + 0.5 + radius * Math.cos(angle);
        double z = center.getZ() + 0.5 + radius * Math.sin(angle);
        double y = center.getY() + 0.6 + 0.4 * Math.sin(time * 0.02 + phase * 2.1);
        return new double[] {x, y, z};
    }

    /**
     * Each wisp gets its own orbital plane (tilted by a golden-angle spread per index) and its
     * own radius/speed, centered on the heart's actual floating height - inclined, distinct
     * ellipses rather than 12 dots sharing one flat ring.
     */
    private static double[] orbitTarget(BlockPos center, int index, int count, double time)
    {
        double tilt = (index * GOLDEN_ANGLE) % (Math.PI * 2.0);
        double radius = ORBIT_RADIUS_MIN + (ORBIT_RADIUS_MAX - ORBIT_RADIUS_MIN) * (0.5 + 0.5 * Math.sin(index * 1.7));
        double speed = 0.035 + 0.02 * ((index % 5) / 5.0);
        double phase = index * (Math.PI * 2.0 / count);
        double angle = time * speed + phase;

        double localX = radius * Math.cos(angle);
        double localY = radius * Math.sin(angle);

        double x = center.getX() + 0.5 + localX * Math.cos(tilt);
        double y = center.getY() + 1.5 + localY * Math.sin(tilt * 0.7 + 0.3);
        double z = center.getZ() + 0.5 + localX * Math.sin(tilt) + localY * Math.cos(tilt) * 0.3;
        return new double[] {x, y, z};
    }

    /** Where a wisp flies while a spell is actively channeling through the circle. */
    private static double[] channelTarget(WispChannel channel, Level level, BlockPos center, Wisp wisp, int index, int count, double time, int[] targetEntityIds,
                                           @org.jetbrains.annotations.Nullable java.util.Set<BlockPos> wallFootprint, int wallBottomY, int wallTopY)
    {
        double cx = center.getX() + 0.5;
        double cz = center.getZ() + 0.5;
        double phase = index * (Math.PI * 2.0 / count);

        return switch (channel)
        {
            case UP -> new double[] {
                    cx + 0.3 * Math.cos(phase + time * 0.05),
                    wisp.y + CHANNEL_REACH,
                    cz + 0.3 * Math.sin(phase + time * 0.05)};
            case DOWN -> new double[] {
                    cx + 0.3 * Math.cos(phase + time * 0.05),
                    wisp.y - CHANNEL_REACH,
                    cz + 0.3 * Math.sin(phase + time * 0.05)};
            case TO_PLAYER -> playerTarget(level, center);
            case OUTWARD -> {
                double angle = phase + time * 0.02;
                double radius = 2.0 + ((time * 0.15 + index * 3.0) % 20.0);
                yield new double[] {cx + radius * Math.cos(angle), center.getY() + 1.0, cz + radius * Math.sin(angle)};
            }
            case TO_TARGETS -> targetTarget(level, center, index, count, time, targetEntityIds);
            case FROM_HEART -> {
                double heartY = center.getY() + 2.0;
                // Each wisp's own phase drives BOTH its pulse and its angle here, not a scaled
                // copy of it (an earlier version used phase*1.3 for the pulse, which - across 12
                // evenly-spaced phases - aliases into several wisps sharing nearly the same
                // effective pulse and reading as one snake-like blob moving together rather than
                // 12 independent wisps).
                double pulse = 0.5 + 0.5 * Math.sin(time * 0.06 + phase);
                double radius = 0.4 + pulse * 2.5;
                double angle = phase + time * 0.05;
                yield new double[] {cx + radius * Math.cos(angle), heartY + 0.3 * Math.sin(time * 0.08 + phase), cz + radius * Math.sin(angle)};
            }
            case TRACE_SHIELD -> {
                List<double[]> path = ShieldBoundaryPath.build(center, wallFootprint, wallBottomY, wallTopY);
                double t = (double) index / count;
                yield ShieldBoundaryPath.pointAt(path, t);
            }
            default -> orbitTarget(center, index, count, time);
        };
    }

    private static double[] playerTarget(Level level, BlockPos center)
    {
        Player player = Minecraft.getInstance().player;
        if (player == null)
        {
            return new double[] {center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5};
        }
        Vec3 eyes = player.getEyePosition();
        return new double[] {eyes.x, eyes.y, eyes.z};
    }

    private static double[] targetTarget(Level level, BlockPos center, int index, int count, double time, int[] targetEntityIds)
    {
        if (index < targetEntityIds.length)
        {
            Entity entity = level.getEntity(targetEntityIds[index]);
            if (entity != null && entity.isAlive())
            {
                Vec3 pos = entity.position();
                return new double[] {pos.x, pos.y + entity.getBbHeight() * 0.5, pos.z};
            }
        }
        return orbitTarget(center, index, count, time);
    }

    private static final class Wisp
    {
        double x;
        double y;
        double z;
        // The color this wisp's home rune had the last time it was calmly orbiting (not mid-
        // burst) - compared against the rune's current color when a burst ends, to decide
        // whether this wisp needs to fade rather than just snap into place. Null only before
        // createWisps ever sets it, which never actually happens - see there.
        Vector3f restColor;
        WispChannel lastChannel = WispChannel.ORBIT;
        int fadeTicks;

        Wisp(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
