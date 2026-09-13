package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The shared "flash gold for an instant, then ripple the same flash outward" visual every shield
 * uses when struck - {@link FairyRealmShield}'s own orb-less boundary (a purely geometric ripple
 * across the ellipsoid's surface, since there are no orbs to propagate to anymore - see that
 * class) and every *other* shield still made of real {@link ShieldOrbEntity} instances (a
 * player-cast Shield/Tempest Ward, or a hand-cast Shield from a commanded Heartstone - see {@link
 * ShieldOrbEntity#hurt}), which still propagates to physically nearby orbs.
 *
 * <p>Extracted out of {@link FairyRealmShield} once that class stopped having any orbs of its own
 * to hit - {@link ShieldOrbEntity#hurt} still needs somewhere to report a real hit to, and the
 * actual "flash, then schedule the same flash nearby after a delay" mechanics are identical
 * either way, just with a different notion of "nearby" (physically nearby orbs here; nearby
 * points on the ellipsoid's own surface over there).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldFlashEffects
{
    /** The Fairy Realm's own boundary, which nothing is attacking - still the original gold. */
    private static final Vector3f FLASH_COLOR = new Vector3f(1.0f, 0.82f, 0.3f);
    /** A shield spell actually taking a hit, which reads as damage rather than as a boundary. */
    private static final Vector3f DAMAGE_FLASH_COLOR = new Vector3f(0.64f, 0.25f, 0.95f);
    private static final float FLASH_PARTICLE_SIZE = 1.4f;
    private static final int FLASH_PARTICLE_COUNT = 5;
    private static final double FLASH_PROPAGATION_RADIUS = 10.0;
    // Blocks/second the "damage rippling outward" flash travels at - tuned purely by feel, fast
    // enough that a radius-10 ripple finishes in well under a second.
    private static final double FLASH_PROPAGATION_SPEED = 28.0;

    private static final List<PendingFlash> pendingFlashes = new ArrayList<>();

    private ShieldFlashEffects()
    {
    }

    /** Called by {@link ShieldOrbEntity#hurt} whenever something actually strikes an orb belonging to a real (not the realm-boundary) shield. */
    public static void onOrbHit(ServerLevel level, ShieldOrbEntity hitOrb)
    {
        Vec3 hitPos = hitOrb.position();
        spawnFlash(level, hitPos, DAMAGE_FLASH_COLOR);

        AABB searchBox = new AABB(hitPos, hitPos).inflate(FLASH_PROPAGATION_RADIUS);
        for (ShieldOrbEntity neighbor : level.getEntitiesOfClass(ShieldOrbEntity.class, searchBox, orb -> true))
        {
            if (neighbor == hitOrb)
            {
                continue;
            }
            scheduleFlashAt(level, neighbor.position(), hitPos, DAMAGE_FLASH_COLOR);
        }
    }

    /** Called by {@link FairyRealmShield#onBoundaryCrossed} for each geometric point it generates on the ellipsoid's own surface. */
    public static void scheduleFlashAt(ServerLevel level, Vec3 point, Vec3 origin)
    {
        scheduleFlashAt(level, point, origin, FLASH_COLOR);
    }

    private static void scheduleFlashAt(ServerLevel level, Vec3 point, Vec3 origin, Vector3f color)
    {
        double distance = point.distanceTo(origin);
        if (distance > FLASH_PROPAGATION_RADIUS)
        {
            return;
        }
        if (distance < 1.0E-4)
        {
            spawnFlash(level, point, color);
            return;
        }
        int delayTicks = Math.max(1, (int) Math.round(distance / FLASH_PROPAGATION_SPEED * 20.0));
        pendingFlashes.add(new PendingFlash(level, point, delayTicks, color));
    }

    public static void spawnFlash(ServerLevel level, Vec3 pos)
    {
        spawnFlash(level, pos, FLASH_COLOR);
    }

    private static void spawnFlash(ServerLevel level, Vec3 pos, Vector3f color)
    {
        level.sendParticles(new DustParticleOptions(color, FLASH_PARTICLE_SIZE),
                pos.x, pos.y, pos.z, FLASH_PARTICLE_COUNT, 0.4, 0.4, 0.4, 0.0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || pendingFlashes.isEmpty())
        {
            return;
        }
        Iterator<PendingFlash> iterator = pendingFlashes.iterator();
        while (iterator.hasNext())
        {
            PendingFlash pending = iterator.next();
            pending.ticksRemaining--;
            if (pending.ticksRemaining <= 0)
            {
                spawnFlash(pending.level, pending.pos, pending.color);
                iterator.remove();
            }
        }
    }

    private static final class PendingFlash
    {
        final ServerLevel level;
        final Vec3 pos;
        final Vector3f color;
        int ticksRemaining;

        PendingFlash(ServerLevel level, Vec3 pos, int ticksRemaining, Vector3f color)
        {
            this.level = level;
            this.pos = pos;
            this.ticksRemaining = ticksRemaining;
            this.color = color;
        }
    }
}
