package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.FairyRealmChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Fairy Realm's own permanent boundary - a real, invisible ellipsoid enforced purely by
 * teleporting anything found outside it back onto the surface every tick ({@link #onServerTick}).
 *
 * <p><b>No entities at all make up this boundary anymore.</b> An earlier version spawned
 * thousands of {@link ShieldOrbEntity} instances across the ellipsoid's surface, both for a
 * (never actually load-bearing - see the old revision's own doc comment) sense of physical
 * presence and to give an arrow/hit something to register a "damaged" flash against. At the
 * scale of a 250-block-radius dimension boundary, even a few thousand orbs are still sparse
 * enough that an arrow shot at the boundary would only sometimes actually hit one, and having
 * visible gaps between "shield segments" read as broken rather than like a real, continuous
 * membrane. Removing the orbs entirely and detecting the boundary crossing itself - {@link
 * #onServerTick} already computes every entity's normalized distance from center for containment,
 * so noticing the tick an entity's distance crosses exactly 1.0 costs nothing extra - gives a
 * flash that fires *every* time, anywhere on the boundary, not just where an orb happened to be
 * sitting.
 *
 * <p>Shaped like an ellipsoid, not a true sphere - a real sphere at {@link #RADIUS_H} (the
 * island's own radius, 250) would need just as much room vertically (500 blocks top to bottom),
 * but this dimension is only 256 blocks tall floor to ceiling, so a true sphere at that radius
 * simply cannot fit; a cylinder-with-flat-caps (an earlier version of this class) fit the height
 * fine but read as an obviously flat-topped can rather than a bubble. Squashing the sphere's
 * vertical radius down to {@link #RADIUS_V} - chosen to just fit the dimension's real height with
 * a little headroom - keeps the *rounded* silhouette (no flat cap, no vertical wall) while still
 * actually fitting.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyRealmShield
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double RADIUS_H = FairyRealmChunkGenerator.islandRadius();
    // Half the dimension's own height (256), minus a little headroom - a true sphere at RADIUS_H
    // would need RADIUS_H of vertical room both above and below center, far more than this
    // dimension actually has.
    private static final double RADIUS_V = 125.0;
    private static final double CENTER_Y = 128.0;
    private static final double REPEL_PADDING_FRACTION = 0.01;

    // "Damaged" flash - triggered by #onServerTick noticing a crossing, not by hitting any orb -
    // see ShieldFlashEffects for the actual particle/propagation mechanics this shares with every
    // other (still orb-based) shield.
    private static final double FLASH_PROPAGATION_RADIUS = 10.0;
    private static final int RIPPLE_RINGS = 3;
    private static final int RIPPLE_POINTS_PER_RING = 8;
    // How long, after triggering, before the same entity can trigger another flash - without
    // this, a player standing (or an arrow lodged) exactly at the boundary would spam a flash
    // every single tick for as long as it stayed there.
    private static final int FLASH_COOLDOWN_TICKS = 10;

    // Per-entity bookkeeping for crossing detection - rebuilt fresh from this tick's entity list
    // every tick (see #onServerTick) so a despawned arrow/removed entity's entry never lingers.
    private static Map<UUID, Double> lastNormDist = new HashMap<>();
    private static Map<UUID, Integer> flashCooldowns = new HashMap<>();

    private FairyRealmShield()
    {
    }

    public static double radiusH()
    {
        return RADIUS_H;
    }

    public static double radiusV()
    {
        return RADIUS_V;
    }

    public static double centerY()
    {
        return CENTER_Y;
    }

    /** Purges any {@link ShieldOrbEntity} left over in an existing save from before this class stopped spawning them - identified by the {@code BlockPos.ZERO} owner sentinel this class used to set on every one it spawned. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }
        int removed = 0;
        for (ShieldOrbEntity orb : fairyRealm.getEntitiesOfClass(ShieldOrbEntity.class, new AABB(-RADIUS_H, CENTER_Y - RADIUS_V, -RADIUS_H, RADIUS_H, CENTER_Y + RADIUS_V, RADIUS_H)))
        {
            if (orb.getOwnerPos().equals(BlockPos.ZERO))
            {
                orb.discard();
                removed++;
            }
        }
        if (removed > 0)
        {
            LOGGER.info("Removed {} leftover realm-boundary shield orb(s) from an earlier version - the boundary is orb-free now.", removed);
        }
    }

    /**
     * Triggered the instant {@link #onServerTick} notices an entity's normalized distance from
     * center cross exactly 1.0 (either direction) - the boundary itself was "hit." Spawns the
     * instant flash right at the crossing point, then schedules the same flash rippling outward
     * across the ellipsoid's own surface (see {@link #ripplePoints}) - a purely geometric
     * substitute for "propagate to nearby orbs," since there are no orbs to propagate to anymore.
     */
    private static void onBoundaryCrossed(ServerLevel level, Vec3 crossingPointOnSurface)
    {
        ShieldFlashEffects.spawnFlash(level, crossingPointOnSurface);
        for (Vec3 ripplePoint : ripplePoints(crossingPointOnSurface))
        {
            ShieldFlashEffects.scheduleFlashAt(level, ripplePoint, crossingPointOnSurface);
        }
    }

    /**
     * A handful of rings of points spreading outward from {@code origin}, each first approximated
     * in the local tangent plane (flat is an excellent approximation at this scale - {@link
     * #FLASH_PROPAGATION_RADIUS}, 10 blocks, is tiny next to the ellipsoid's own 250-block
     * radius) and then re-projected exactly onto the true ellipsoid surface via {@link
     * #projectOntoSurface} - so every generated point is a real position on the boundary, not
     * just near one.
     */
    private static List<Vec3> ripplePoints(Vec3 origin)
    {
        Vec3 normal = new Vec3(origin.x / (RADIUS_H * RADIUS_H), (origin.y - CENTER_Y) / (RADIUS_V * RADIUS_V), origin.z / (RADIUS_H * RADIUS_H)).normalize();
        Vec3 reference = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tangent1 = normal.cross(reference).normalize();
        Vec3 tangent2 = normal.cross(tangent1).normalize();

        List<Vec3> points = new ArrayList<>(RIPPLE_RINGS * RIPPLE_POINTS_PER_RING);
        for (int ring = 1; ring <= RIPPLE_RINGS; ring++)
        {
            double radius = FLASH_PROPAGATION_RADIUS * ring / (double) RIPPLE_RINGS;
            for (int p = 0; p < RIPPLE_POINTS_PER_RING; p++)
            {
                double angle = 2.0 * Math.PI * p / RIPPLE_POINTS_PER_RING;
                Vec3 approx = origin.add(tangent1.scale(radius * Math.cos(angle))).add(tangent2.scale(radius * Math.sin(angle)));
                points.add(projectOntoSurface(approx));
            }
        }
        return points;
    }

    /** The nearest point on the ellipsoid's own surface, along the ray from the ellipsoid's center through {@code p}. */
    private static Vec3 projectOntoSurface(Vec3 p)
    {
        double nx = p.x / RADIUS_H;
        double ny = (p.y - CENTER_Y) / RADIUS_V;
        double nz = p.z / RADIUS_H;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6)
        {
            return p;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        return new Vec3(nx * RADIUS_H, CENTER_Y + ny * RADIUS_V, nz * RADIUS_H);
    }

    // ------------------------------------------------------------------
    // Striking the boundary
    // ------------------------------------------------------------------

    /** Generous next to the client's own reach, so lag never makes a real strike miss - see {@link #strikeFrom}. */
    private static final double SERVER_STRIKE_REACH = 6.0;
    /** A punch's pace - holding the button down doesn't turn the boundary into a strobe. */
    private static final int STRIKE_COOLDOWN_TICKS = 4;
    private static final Map<UUID, Long> lastStrike = new HashMap<>();

    /**
     * Where the straight line from {@code from} to {@code to} first meets the boundary, or {@code
     * null} if it never does. Scaling each axis by its own radius turns the ellipsoid into a unit
     * sphere, where this is an ordinary line-sphere intersection - the nearer of the two crossings
     * that falls within the segment. Pure maths on constants, so the client can ask it too.
     */
    public static Vec3 firstCrossing(Vec3 from, Vec3 to)
    {
        double px = from.x / RADIUS_H;
        double py = (from.y - CENTER_Y) / RADIUS_V;
        double pz = from.z / RADIUS_H;
        double dx = (to.x - from.x) / RADIUS_H;
        double dy = (to.y - from.y) / RADIUS_V;
        double dz = (to.z - from.z) / RADIUS_H;

        double a = dx * dx + dy * dy + dz * dz;
        if (a < 1.0E-12)
        {
            return null;
        }
        double b = 2.0 * (px * dx + py * dy + pz * dz);
        double c = px * px + py * py + pz * pz - 1.0;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0)
        {
            return null;
        }
        double root = Math.sqrt(discriminant);
        double near = (-b - root) / (2.0 * a);
        double far = (-b + root) / (2.0 * a);
        double t = near >= 0.0 && near <= 1.0 ? near : far >= 0.0 && far <= 1.0 ? far : -1.0;
        return t < 0.0 ? null : from.add(to.subtract(from).scale(t));
    }

    /** The boundary struck at {@code point}: the flash and its ripple, and a clear ring to go with them. */
    public static void strike(ServerLevel level, Vec3 point)
    {
        onBoundaryCrossed(level, point);
        level.playSound(null, point.x, point.y, point.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.5f, 0.6f);
    }

    /**
     * A player swinging at the boundary - from {@code network/RealmShieldStrikePacket}. The client
     * only says that it happened; where is worked out again here from the player's own eye and look,
     * so nobody can make the boundary flare somewhere they aren't.
     */
    public static void strikeFrom(net.minecraft.server.level.ServerPlayer player)
    {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }
        long now = level.getGameTime();
        Long last = lastStrike.get(player.getUUID());
        if (last != null && now - last < STRIKE_COOLDOWN_TICKS)
        {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 hit = firstCrossing(eye, eye.add(player.getViewVector(1.0f).scale(SERVER_STRIKE_REACH)));
        if (hit == null)
        {
            return;
        }
        lastStrike.put(player.getUUID(), now);
        strike(level, hit);
    }

    /**
     * Nothing is hit through the boundary, in either direction: a blow between someone on one side
     * and something on the other lands on the boundary instead, and it ripples where it did.
     */
    @SubscribeEvent
    public static void onAttackAcross(net.minecraftforge.event.entity.player.AttackEntityEvent event)
    {
        if (!(event.getEntity().level() instanceof ServerLevel level) || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }
        Vec3 hit = firstCrossing(event.getEntity().getEyePosition(), event.getTarget().getBoundingBox().getCenter());
        if (hit != null)
        {
            event.setCanceled(true);
            strike(level, hit);
        }
    }

    /** Nothing here belongs to the next world a singleplayer game opens. */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event)
    {
        lastStrike.clear();
    }

    /**
     * Explosions that go off *outside* the boundary (a creeper or TNT that wandered/was placed
     * past the shield, or in the void beyond it) never affect anything inside it - blocks and
     * entities on the protected side are simply dropped from the event before vanilla applies any
     * of it. An explosion that starts *inside* the shield is left completely alone; only the
     * "outside reaching in" direction is guarded against, matching what was asked for.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }
        Vec3 source = event.getExplosion().getPosition();
        if (normalizedDistance(source) <= 1.0)
        {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> normalizedDistance(Vec3.atCenterOf(pos)) <= 1.0);
        event.getAffectedEntities().removeIf(entity -> normalizedDistance(entity.position()) <= 1.0);
    }

    private static double normalizedDistance(Vec3 pos)
    {
        double nx = pos.x / RADIUS_H;
        double ny = (pos.y - CENTER_Y) / RADIUS_V;
        double nz = pos.z / RADIUS_H;
        return Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    /**
     * The real boundary: anything (player or otherwise) outside the ellipsoid gets pushed
     * straight back onto its surface - the same "shove anything crossing the line back inward"
     * idea {@code HeartCoreBlockEntity#tickShieldBoundary} uses for a player-cast shield, except
     * this one has no owner to exempt, since nobody's ever meant to cross it (creative flight and
     * spectator-speed movement included - this runs every tick regardless of game mode).
     * Normalizing each axis by its own radius first turns the ellipsoid into a unit sphere, where
     * "outside" is just "length > 1" and pushing back onto the surface is a plain rescale.
     *
     * <p>The very same loop also drives {@link #onBoundaryCrossed}: {@link #lastNormDist} (rebuilt
     * fresh every tick into {@code nextNormDist}/{@code nextCooldowns} so a removed entity's entry
     * never lingers) remembers each entity's normalized distance from the *previous* tick: the
     * instant that value and this tick's both straddle 1.0 (an actual crossing, not just "close
     * to the edge"), the boundary was hit right there.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }

        double maxNormDist = 1.0 - REPEL_PADDING_FRACTION;
        Map<UUID, Double> nextNormDist = new HashMap<>();
        Map<UUID, Integer> nextCooldowns = new HashMap<>();

        for (Entity entity : fairyRealm.getAllEntities())
        {
            double x = entity.getX();
            double y = entity.getY() - CENTER_Y;
            double z = entity.getZ();

            double nx = x / RADIUS_H;
            double ny = y / RADIUS_V;
            double nz = z / RADIUS_H;
            double normDist = Math.sqrt(nx * nx + ny * ny + nz * nz);

            UUID id = entity.getUUID();
            Double previous = lastNormDist.get(id);
            int cooldown = flashCooldowns.getOrDefault(id, 0) - 1;
            if (previous != null && (previous - 1.0) * (normDist - 1.0) < 0.0)
            {
                if (cooldown <= 0)
                {
                    onBoundaryCrossed(fairyRealm, projectOntoSurface(entity.position()));
                    cooldown = FLASH_COOLDOWN_TICKS;
                }
            }
            nextNormDist.put(id, normDist);
            if (cooldown > 0)
            {
                nextCooldowns.put(id, cooldown);
            }

            if (normDist > maxNormDist && normDist > 1.0E-4)
            {
                double scale = maxNormDist / normDist;
                double pushedX = x * scale;
                double pushedY = CENTER_Y + y * scale;
                double pushedZ = z * scale;
                entity.teleportTo(pushedX, pushedY, pushedZ);
                entity.setDeltaMovement(entity.getDeltaMovement().scale(-0.2));
            }
        }

        lastNormDist = nextNormDist;
        flashCooldowns = nextCooldowns;
    }
}
