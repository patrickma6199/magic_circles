package com.patrickma.magiccircles;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.ritual.PortalRitual;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDrownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything about "Zuzo's Crossing," the portal spell (see
 * {@code HeartCoreBlockEntity#openWaterPortal}) and the Lost Waystone (see
 * {@code LostWaystoneItem}): the water-pit precondition, converting that water into
 * {@code FairyPortalWaterBlock}, and the actual crossing mechanic - stand in the portal water
 * until your air runs out, and you're carried to the other side instead of actually drowning
 * (see {@link #onLivingDrown}).
 *
 * <p><b>One shared Fairy Realm portal.</b> Every overworld portal - however many are open at
 * once - connects to the exact same destination: the pre-built ruined chamber (see
 * {@code FairyPortalRuins}). Opening a second (or third...) overworld portal doesn't spread out
 * to a new spot on the island; it just adds another origin that shares the one fairy-side water
 * pit, which stays lit as portal fluid for as long as at least one origin (a real portal, or a
 * pending Lost Waystone trip) is still open. Whoever opened a given origin always returns to it
 * specifically ({@link #returnFromFairyRealm} tries the crosser's own entry first); anyone else -
 * another player, a mob that wandered in and drowned there - lands at a uniformly random one of
 * the currently-open overworld portals instead, per {@link #pickRandomOrigin}.
 *
 * <p>Tracking active portals/trips is a plain in-memory map, not saved-game data - like the old
 * system, it doesn't survive a server restart. That's a real limitation worth fixing (with
 * Forge's {@code SavedData}) before this is more than a first pass; noting it here rather than
 * pretending otherwise.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyPortalManager
{
    // The 8 cells immediately around a portal ring's center - see PortalRitual's '0' cells.
    private static final int[][] SURROUNDING_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1},
    };
    // The top water layer sits one block below the ring's own Y (the solid ground the runes
    // and the Heart Core actually sit on, not the rune/heart grid position itself) - it's the
    // only layer that has to be a real source block. The 3 layers below that just need to be
    // *some* kind of water (a source block naturally flows straight down into an empty shaft
    // underneath it, so digging one hole per column and placing a single source block at the
    // top is all building this actually takes - the rest fills in on its own).
    private static final int SOURCE_LAYER_DEPTH = 1;
    private static final int FLOWING_LAYERS_BELOW_SOURCE = 3;

    /** Real overworld portals, keyed by their origin ring's center. */
    private static final Map<BlockPos, ActivePortal> ACTIVE_PORTALS = new HashMap<>();
    /** Pending one-way Lost Waystone trips, keyed by the traveler's UUID - see {@link #useWaystone}. */
    private static final Map<UUID, ActivePortal> WAYSTONE_TRIPS = new HashMap<>();

    /** The single shared Fairy Realm portal's water cells, or {@code null} while nothing is open. */
    @Nullable
    private static Set<BlockPos> destWater;
    @Nullable
    private static BlockPos destLanding;

    private FairyPortalManager()
    {
    }

    /**
     * True if all 8 surrounding columns have a real water source block at
     * {@value #SOURCE_LAYER_DEPTH} below the ring, and *some* water (source or flowing) for the
     * {@value #FLOWING_LAYERS_BELOW_SOURCE} blocks under that.
     */
    public static boolean hasWaterPit(Level level, BlockPos center)
    {
        for (int[] offset : SURROUNDING_OFFSETS)
        {
            if (!isWaterSource(level.getBlockState(center.offset(offset[0], -SOURCE_LAYER_DEPTH, offset[1]))))
            {
                return false;
            }
            for (int i = 1; i <= FLOWING_LAYERS_BELOW_SOURCE; i++)
            {
                BlockPos pos = center.offset(offset[0], -SOURCE_LAYER_DEPTH - i, offset[1]);
                if (!level.getBlockState(pos).getFluidState().is(FluidTags.WATER))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isWaterSource(BlockState state)
    {
        return state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
    }

    /** Every block {@link #hasWaterPit} checks, top (the source layer) to bottom - what actually becomes portal water. */
    private static Set<BlockPos> waterPitPositions(BlockPos center)
    {
        Set<BlockPos> positions = new HashSet<>();
        for (int[] offset : SURROUNDING_OFFSETS)
        {
            for (int i = 0; i <= FLOWING_LAYERS_BELOW_SOURCE; i++)
            {
                positions.add(center.offset(offset[0], -SOURCE_LAYER_DEPTH - i, offset[1]));
            }
        }
        return positions;
    }

    /**
     * The single deepest cell of one specific pit column ({@link #SURROUNDING_OFFSETS}'s first
     * entry) - always the same corner rather than an arbitrary one, so both sides of a portal
     * land in the same relative spot. Whoever crosses arrives standing right here, at the very
     * bottom of the 4-deep pit, so getting out means actually swimming up through the portal
     * water rather than popping out already at the surface.
     */
    private static BlockPos pitFloor(BlockPos center)
    {
        int[] offset = SURROUNDING_OFFSETS[0];
        return center.offset(offset[0], -SOURCE_LAYER_DEPTH - FLOWING_LAYERS_BELOW_SOURCE, offset[1]);
    }

    /**
     * Converts the (already-validated) water pit around {@code originCenter} into portal water,
     * makes sure the one shared Fairy Realm portal is open, and registers this origin against it.
     * {@code centerColor} is whatever color the ring's center rune was before it became a Heart
     * Core - the shared destination's own center rune gets refreshed to match on every new
     * origin, since there's no single "correct" color once more than one origin can feed it.
     */
    public static void openWaterPortal(ServerLevel originLevel, BlockPos originCenter, RuneColor centerColor, ServerPlayer opener)
    {
        MinecraftServer server = originLevel.getServer();
        ServerLevel fairyRealm = server.getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }

        Set<BlockPos> originWater = waterPitPositions(originCenter);
        for (BlockPos pos : originWater)
        {
            originLevel.setBlockAndUpdate(pos, ModBlocks.FAIRY_PORTAL_WATER.get().defaultBlockState());
        }

        BlockPos destCenter = com.patrickma.magiccircles.worldgen.FairyPortalRuins.portalCenter();
        boolean firstEverOpen = destWater == null;
        if (firstEverOpen)
        {
            mirrorBorder(originLevel, originCenter, fairyRealm, destCenter, centerColor);
        }
        openSharedDestination(fairyRealm, destCenter);

        ActivePortal portal = new ActivePortal(opener.getUUID(), originLevel.dimension(), originCenter, originWater, pitFloor(originCenter));
        portal.openerReachedDest = false;
        ACTIVE_PORTALS.put(originCenter.immutable(), portal);
    }

    /**
     * A Lost Waystone's one-way trip: no origin water pit (the player just vanishes from wherever
     * they used it), straight to the shared Fairy Realm portal. Registered the same way a real
     * portal's origin is, keyed by the traveler instead of a block position, so the existing
     * return-routing in {@link #returnFromFairyRealm} sends them back to the exact spot they left
     * from once they cross back through the portal - the only way back, since the waystone itself
     * is consumed on arrival.
     */
    public static boolean useWaystone(ServerPlayer player)
    {
        ServerLevel fromLevel = player.serverLevel();
        MinecraftServer server = fromLevel.getServer();
        ServerLevel fairyRealm = server.getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return false;
        }

        BlockPos destCenter = com.patrickma.magiccircles.worldgen.FairyPortalRuins.portalCenter();
        openSharedDestination(fairyRealm, destCenter);

        ActivePortal trip = new ActivePortal(player.getUUID(), fromLevel.dimension(), player.blockPosition(), Set.of(), player.blockPosition());
        trip.openerReachedDest = true;
        WAYSTONE_TRIPS.put(player.getUUID(), trip);

        player.setAirSupply(player.getMaxAirSupply());
        player.teleportTo(fairyRealm, destLanding.getX() + 0.5, destLanding.getY(), destLanding.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        return true;
    }

    /** Makes sure the shared destination's water pit exists and is lit as portal fluid - idempotent, safe to call every time a new origin opens. */
    private static void openSharedDestination(ServerLevel fairyRealm, BlockPos destCenter)
    {
        fairyRealm.getChunk(destCenter.getX() >> 4, destCenter.getZ() >> 4);
        Set<BlockPos> water = waterPitPositions(destCenter);
        for (BlockPos pos : water)
        {
            fairyRealm.setBlockAndUpdate(pos, ModBlocks.FAIRY_PORTAL_WATER.get().defaultBlockState());
        }
        destWater = water;
        destLanding = pitFloor(destCenter);
    }

    /** Copies the origin's 5x5 border (redstone + rune colors) onto the destination, plus a fresh center rune - not the 8 inner cells, which get their own water pit instead. */
    private static void mirrorBorder(ServerLevel originLevel, BlockPos originCenter, ServerLevel destLevel, BlockPos destCenter, RuneColor centerColor)
    {
        for (int dx = -2; dx <= 2; dx++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                boolean isInnerCellOrCenter = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (isInnerCellOrCenter)
                {
                    continue;
                }
                BlockState state = originLevel.getBlockState(originCenter.offset(dx, 0, dz));
                destLevel.setBlockAndUpdate(destCenter.offset(dx, 0, dz), state);
            }
        }
        destLevel.setBlockAndUpdate(destCenter, ModBlocks.MAGIC_CIRCLE.get().defaultBlockState()
                .setValue(MagicCircleBlock.VARIANT, destLevel.random.nextInt(MagicCircleBlock.VARIANT_COUNT))
                .setValue(MagicCircleBlock.COLOR, centerColor));
    }

    /**
     * The actual crossing trigger. Since the portal water is a real fluid now (see
     * {@code ModFluids}/{@code ModFluidTypes}), vanilla's own breathing code already drains air
     * and shows the bubble HUD with no help from this mod at all - the only thing left to
     * handle is what happens once air actually runs out. {@link LivingDrownEvent} (confirmed by
     * decompiling {@code ForgeHooks#onLivingBreathe}, the generic, non-water-specific successor
     * to vanilla's own now-dead water-breathing code) fires every tick a living entity's air is
     * at or below zero, and is cancelable - canceling it here skips the vanilla drown damage
     * (and its bubble-burst particles) entirely, in favor of resetting the entity's air and
     * sending it to the other side instead. This isn't limited to players - any
     * {@link LivingEntity} (animals, monsters, ...) that drowns in a portal pit gets carried
     * across the same way, though only players get the opener-priority/random-landing treatment
     * on the way back (see {@link #returnFromFairyRealm}).
     */
    @SubscribeEvent
    public static void onLivingDrown(LivingDrownEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        BlockPos eyePos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());

        if (serverLevel.dimension().equals(ModDimensions.FAIRY_REALM) && destWater != null && destWater.contains(eyePos))
        {
            event.setCanceled(true);
            returnFromFairyRealm(entity, serverLevel);
            return;
        }

        ActivePortal origin = findOriginAt(serverLevel.dimension(), eyePos);
        if (origin != null)
        {
            event.setCanceled(true);
            enterFairyRealm(entity, origin);
        }
    }

    @Nullable
    private static ActivePortal findOriginAt(ResourceKey<Level> dimension, BlockPos pos)
    {
        for (ActivePortal portal : ACTIVE_PORTALS.values())
        {
            if (portal.originDim.equals(dimension) && portal.originWater.contains(pos))
            {
                return portal;
            }
        }
        return null;
    }

    /** Overworld portal -> shared Fairy Realm destination. Works for any entity, not just the opener. */
    private static void enterFairyRealm(LivingEntity entity, ActivePortal origin)
    {
        if (!(entity.level() instanceof ServerLevel fromLevel) || destLanding == null)
        {
            return;
        }
        ServerLevel fairyRealm = fromLevel.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }

        entity.setAirSupply(entity.getMaxAirSupply());
        entity.teleportTo(fairyRealm, destLanding.getX() + 0.5, destLanding.getY(), destLanding.getZ() + 0.5,
                Set.of(), entity.getYRot(), entity.getXRot());

        if (entity instanceof ServerPlayer player && player.getUUID().equals(origin.openerUuid))
        {
            origin.openerReachedDest = true;
        }
    }

    /**
     * Shared Fairy Realm destination -> overworld. The opener of a still-open origin (real
     * portal or pending waystone trip) always lands back at their own; anyone else - another
     * player, a mob - is sent to a uniformly random currently-open real portal, per the "the
     * person that opened the portal will always return to their own but anyone else will
     * randomly land back at one of the two overworld portals" rule. Mobs/non-opener players have
     * nowhere defined to go if no real portal is open (only waystone trips, which have no
     * physical landing spot for a stranger) - they're simply left in place rather than crashing.
     */
    private static void returnFromFairyRealm(LivingEntity entity, ServerLevel fromLevel)
    {
        MinecraftServer server = fromLevel.getServer();

        ActivePortal ownEntry = null;
        if (entity instanceof ServerPlayer player)
        {
            ActivePortal waystone = WAYSTONE_TRIPS.get(player.getUUID());
            if (waystone != null)
            {
                ownEntry = waystone;
            }
            else
            {
                for (ActivePortal portal : ACTIVE_PORTALS.values())
                {
                    if (portal.openerUuid.equals(player.getUUID()) && portal.openerReachedDest)
                    {
                        ownEntry = portal;
                        break;
                    }
                }
            }
        }

        ActivePortal target = ownEntry != null ? ownEntry : pickRandomOrigin();
        if (target == null)
        {
            return;
        }

        ServerLevel toLevel = server.getLevel(target.originDim);
        if (toLevel == null)
        {
            return;
        }

        entity.setAirSupply(entity.getMaxAirSupply());
        // landing.getY() itself, not +0.3 or similar - for a real portal this is the pit's own
        // bottom water block (getting out means swimming up through it); for a waystone trip it's
        // just the exact spot the traveler used the item from.
        BlockPos landing = target.originLanding;
        entity.teleportTo(toLevel, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
                Set.of(), entity.getYRot(), entity.getXRot());

        if (target == ownEntry)
        {
            closeEntry(server, target);
        }
    }

    /** A uniformly random currently-open *real* portal - never a waystone trip, which has no physical spot for a stranger to land at. */
    @Nullable
    private static ActivePortal pickRandomOrigin()
    {
        if (ACTIVE_PORTALS.isEmpty())
        {
            return null;
        }
        List<ActivePortal> options = new ArrayList<>(ACTIVE_PORTALS.values());
        return options.get((int) (Math.random() * options.size()));
    }

    /**
     * True round trip aside, breaking the origin's 5x5 border - actually destroying one of its
     * blocks, not just standing near it - also closes whatever portal is rooted there, checked
     * periodically from {@code HeartCoreBlockEntity#tickRingIntegrity} the same way a broken
     * 12-ring cancels a color-spell. A no-op if this position isn't (or is no longer) an active
     * portal's origin, so it's cheap to call unconditionally from every Heart Core's own tick.
     */
    public static void closeIfBorderBroken(ServerLevel level, BlockPos originCenter)
    {
        ActivePortal portal = ACTIVE_PORTALS.get(originCenter);
        if (portal == null || PortalRitual.matchesBorder(level, originCenter))
        {
            return;
        }
        closeEntry(level.getServer(), portal);
    }

    private static int integrityCheckCounter = 0;
    private static final int PORTAL_INTEGRITY_CHECK_INTERVAL = 20;

    /**
     * Keeps every origin in sync with the shared destination: if the destination's own water
     * stops being real portal fluid, every origin closes (there's nothing left to connect to);
     * if one specific origin's water is broken instead, only that origin closes - the shared
     * destination stays open as long as anything else still needs it.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || destWater == null)
        {
            return;
        }
        integrityCheckCounter++;
        if (integrityCheckCounter % PORTAL_INTEGRITY_CHECK_INTERVAL != 0)
        {
            return;
        }

        MinecraftServer server = event.getServer();

        if (!isPortalWaterIntact(server, ModDimensions.FAIRY_REALM, destWater))
        {
            for (ActivePortal portal : ACTIVE_PORTALS.values())
            {
                revertToWater(server, portal.originDim, portal.originWater);
            }
            ACTIVE_PORTALS.clear();
            WAYSTONE_TRIPS.clear();
            revertToWater(server, ModDimensions.FAIRY_REALM, destWater);
            destWater = null;
            destLanding = null;
            return;
        }

        Iterator<ActivePortal> iterator = ACTIVE_PORTALS.values().iterator();
        while (iterator.hasNext())
        {
            ActivePortal portal = iterator.next();
            if (!isPortalWaterIntact(server, portal.originDim, portal.originWater))
            {
                iterator.remove();
                revertToWater(server, portal.originDim, portal.originWater);
            }
        }
        closeSharedDestinationIfUnused(server);
    }

    /** True only if every one of these cells is still the special portal fluid - an unloaded dimension is treated as intact rather than spuriously closing a portal whose far side just hasn't been visited in a while. */
    private static boolean isPortalWaterIntact(MinecraftServer server, ResourceKey<Level> dimension, Set<BlockPos> water)
    {
        ServerLevel level = server.getLevel(dimension);
        if (level == null)
        {
            return true;
        }
        for (BlockPos pos : water)
        {
            if (!level.getBlockState(pos).is(ModBlocks.FAIRY_PORTAL_WATER.get()))
            {
                return false;
            }
        }
        return true;
    }

    /** Closes one specific origin (real portal or waystone trip) - reverts its own water (if any) and, if it was the last thing keeping the shared destination open, reverts that too. */
    private static void closeEntry(MinecraftServer server, ActivePortal entry)
    {
        boolean removed = ACTIVE_PORTALS.values().remove(entry);
        if (!removed)
        {
            WAYSTONE_TRIPS.values().remove(entry);
        }
        if (!entry.originWater.isEmpty())
        {
            revertToWater(server, entry.originDim, entry.originWater);
        }
        closeSharedDestinationIfUnused(server);
    }

    private static void closeSharedDestinationIfUnused(MinecraftServer server)
    {
        if (destWater != null && ACTIVE_PORTALS.isEmpty() && WAYSTONE_TRIPS.isEmpty())
        {
            revertToWater(server, ModDimensions.FAIRY_REALM, destWater);
            destWater = null;
            destLanding = null;
        }
    }

    private static void revertToWater(MinecraftServer server, ResourceKey<Level> dimension, Set<BlockPos> water)
    {
        ServerLevel level = server.getLevel(dimension);
        if (level == null)
        {
            return;
        }
        for (BlockPos pos : water)
        {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        }
    }

    private static final class ActivePortal
    {
        final UUID openerUuid;
        final ResourceKey<Level> originDim;
        final Set<BlockPos> originWater;
        final BlockPos originLanding;
        boolean openerReachedDest;

        ActivePortal(UUID openerUuid, ResourceKey<Level> originDim, BlockPos originCenter, Set<BlockPos> originWater, BlockPos originLanding)
        {
            this.openerUuid = openerUuid;
            this.originDim = originDim;
            this.originWater = originWater;
            this.originLanding = originLanding;
        }
    }
}
