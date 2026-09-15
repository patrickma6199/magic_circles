package com.patrickma.magiccircles.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Flying a fairy somewhere it must actually arrive - to the Fairy Court, above all (see {@code
 * worldgen/FairyThroneRoom#routeTo}). The course is a list of waypoints planned up front; each is
 * flown in whichever way works:
 *
 * <ul>
 *   <li>Straight at it, whenever the way is clear for the whole body - no pathfinding at all, so
 *       distance never matters.</li>
 *   <li>Otherwise by vanilla flying paths, but only ever to a point at most {@value #PATH_REACH}
 *       blocks off: vanilla gives up on anything past its follow range, which is what left the
 *       queen circling the tree unable to find her own throne.</li>
 *   <li>And if it makes no headway at all for {@value #BLINK_TICKS} ticks, it simply slips through
 *       the branches in a flicker of light and carries on from the waypoint - a fairy is never
 *       left stuck against the bark.</li>
 * </ul>
 */
final class CourtFlight
{
    private static final double REACHED = 1.6;
    private static final double REACHED_FINAL = 0.5;
    private static final double PATH_REACH = 32.0;
    private static final int REPATH_TICKS = 20;
    private static final int BLINK_TICKS = 20 * 6;
    /** Slows for the last few blocks, so it settles on the spot rather than overshooting it. */
    private static final double SETTLE_DISTANCE = 3.0;

    private final FairyEntity fairy;
    private final List<Vec3> course = new ArrayList<>();
    private int index;
    private double best;
    private int stalled;
    private int repath;

    CourtFlight(FairyEntity fairy)
    {
        this.fairy = fairy;
    }

    void plan(List<Vec3> waypoints)
    {
        this.course.clear();
        this.course.addAll(waypoints);
        this.index = 0;
        this.best = Double.MAX_VALUE;
        this.stalled = 0;
        this.repath = 0;
        this.fairy.getNavigation().stop();
    }

    boolean arrived()
    {
        return this.index >= this.course.size();
    }

    @Nullable
    Vec3 destination()
    {
        return this.course.isEmpty() ? null : this.course.get(this.course.size() - 1);
    }

    /** One tick along the course; true once the last waypoint is reached. */
    boolean tick(double speed)
    {
        if (arrived())
        {
            return true;
        }
        Vec3 target = this.course.get(this.index);
        boolean last = this.index == this.course.size() - 1;
        double distance = this.fairy.position().distanceTo(target);
        if (distance < (last ? REACHED_FINAL : REACHED))
        {
            next();
            return arrived();
        }
        if (distance < this.best - 0.05)
        {
            this.best = distance;
            this.stalled = 0;
        }
        else if (++this.stalled > BLINK_TICKS)
        {
            blink(target);
            next();
            return arrived();
        }

        this.fairy.getLookControl().setLookAt(target.x, target.y + this.fairy.getEyeHeight(), target.z);
        if (clearFlight(target))
        {
            this.fairy.getNavigation().stop();
            double pace = last && distance < SETTLE_DISTANCE ? speed * 0.5 : speed;
            this.fairy.getMoveControl().setWantedPosition(target.x, target.y, target.z, pace);
        }
        else if (--this.repath <= 0 || this.fairy.getNavigation().isDone())
        {
            this.repath = REPATH_TICKS;
            Vec3 aim = distance > PATH_REACH
                    ? this.fairy.position().add(target.subtract(this.fairy.position()).normalize().scale(PATH_REACH))
                    : target;
            if (!this.fairy.getNavigation().moveTo(aim.x, aim.y, aim.z, speed))
            {
                // No path at all - count that toward slipping through, rather than waiting it out.
                this.stalled += REPATH_TICKS / 2;
            }
        }
        return false;
    }

    private void next()
    {
        this.index++;
        this.best = Double.MAX_VALUE;
        this.stalled = 0;
        this.repath = 0;
        this.fairy.getNavigation().stop();
    }

    /** Whether the whole body - feet, middle and head - has a clear line to the same heights at {@code target}. */
    private boolean clearFlight(Vec3 target)
    {
        double height = this.fairy.getBbHeight();
        for (double y : new double[]{0.15, height * 0.5, height - 0.15})
        {
            Vec3 from = this.fairy.position().add(0.0, y, 0.0);
            Vec3 to = target.add(0.0, y, 0.0);
            if (this.fairy.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.fairy))
                    .getType() != HitResult.Type.MISS)
            {
                return false;
            }
        }
        return true;
    }

    private void blink(Vec3 target)
    {
        if (this.fairy.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.END_ROD, this.fairy.getX(), this.fairy.getY() + 1.0, this.fairy.getZ(), 16, 0.3, 0.6, 0.3, 0.04);
            this.fairy.teleportTo(target.x, target.y, target.z);
            level.sendParticles(ParticleTypes.END_ROD, target.x, target.y + 1.0, target.z, 16, 0.3, 0.6, 0.3, 0.04);
            // Through the fairy itself, so a ghost's is only heard by those who can see it.
            this.fairy.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
        }
        this.fairy.setDeltaMovement(Vec3.ZERO);
        this.fairy.getNavigation().stop();
    }
}
