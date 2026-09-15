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
    /**
     * Pits lit by the Crossing sung from inside the Fairy Realm itself (see {@link #openWaterPortal}),
     * keyed by their ring's centre. Each leads home rather than in - see {@link #goHome}. Forgotten
     * when the server stops, like every other open portal.
     */
    private static final Map<BlockPos, Set<BlockPos>> HOMEWARD = new HashMap<>();
    /**
     * Whoever has just been carried across lands at the bottom of the far pit - so the bottom does
     * not count for them until they have left its water once, or every crossing would bounce
     * straight back. See {@link #onLivingTick}.
     */
    private static final Set<UUID> ARRIVED = new HashSet<>();

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

        if (originLevel.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            // Sung from inside Yllumere, the Crossing opens the way home, not another way in. There
            // is nothing on this side to mirror it onto - doing so had it stamp a fresh rune over
            // its own heart - and registering it as an origin had anyone drowning in it sent
            // straight back into the same pool.
            Set<BlockPos> homeward = waterPitPositions(originCenter);
            for (BlockPos pos : homeward)
            {
                originLevel.setBlockAndUpdate(pos, ModBlocks.FAIRY_PORTAL_WATER.get().defaultBlockState());
            }
            HOMEWARD.put(originCenter.immutable(), homeward);
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
        if (fromLevel.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            return leadHome(player, fromLevel);
        }

        BlockPos destCenter = com.patrickma.magiccircles.worldgen.FairyPortalRuins.portalCenter();
        openSharedDestination(fairyRealm, destCenter);

        ActivePortal trip = new ActivePortal(player.getUUID(), fromLevel.dimension(), player.blockPosition(), Set.of(), player.blockPosition());
        trip.openerReachedDest = true;
        WAYSTONE_TRIPS.put(player.getUUID(), trip);

        player.setAirSupply(player.getMaxAirSupply());
        player.teleportTo(fairyRealm, destLanding.getX() + 0.5, destLanding.getY(), destLanding.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        ARRIVED.add(player.getUUID());
        return true;
    }

    /**
     * A Lost Waystone used inside the Fairy Realm - the emergency way out. Open portals only last as
     * long as the server that opened them, so anyone who logs off in Yllumere would otherwise come
     * back to a dark pool and no way home. If a portal of their own is still open behind them, this
     * takes them back through it exactly as swimming down would; otherwise it sends them to wherever
     * they would respawn, and failing that to the world's spawn.
     */
    private static boolean leadHome(ServerPlayer player, ServerLevel fromLevel)
    {
        MinecraftServer server = fromLevel.getServer();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("item.magiccircles.lost_waystone.home"), true);
        fromLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        player.setAirSupply(player.getMaxAirSupply());

        if (hasOwnEntry(player))
        {
            returnFromFairyRealm(player, fromLevel);
            return true;
        }

        sendToRespawn(player, server, false);
        return true;
    }

    /**
     * Out of the Fairy Realm to wherever this player would respawn - or, with {@code overworldOnly},
     * only if that is in the overworld - and failing that, to the world's spawn.
     */
    private static void sendToRespawn(ServerPlayer player, MinecraftServer server, boolean overworldOnly)
    {
        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnLevel != null && respawnPos != null && !respawnLevel.dimension().equals(ModDimensions.FAIRY_REALM)
                && (!overworldOnly || respawnLevel.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)))
        {
            java.util.Optional<net.minecraft.world.phys.Vec3> spot = net.minecraft.world.entity.player.Player
                    .findRespawnPositionAndUseSpawnBlock(respawnLevel, respawnPos, player.getRespawnAngle(),
                            player.isRespawnForced(), true);
            if (spot.isPresent())
            {
                player.teleportTo(respawnLevel, spot.get().x, spot.get().y, spot.get().z,
                        Set.of(), player.getRespawnAngle(), 0.0f);
                return;
            }
        }

        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int surface = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawn.getX(), spawn.getZ());
        player.teleportTo(overworld, spawn.getX() + 0.5, surface, spawn.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }

    /**
     * Cast out by the queen (see {@code QueensBanishment}): straight back to the overworld - to their
     * bed if it lies there, otherwise to the world's spawn. Any waystone trip they were on is over.
     */
    public static void banishToOverworld(ServerPlayer player)
    {
        MinecraftServer server = player.getServer();
        if (server == null)
        {
            return;
        }
        WAYSTONE_TRIPS.remove(player.getUUID());
        player.setAirSupply(player.getMaxAirSupply());
        sendToRespawn(player, server, true);
    }

    /** Whether this player still has a way back of their own - a waystone trip, or a portal they opened and came through. */
    private static boolean hasOwnEntry(ServerPlayer player)
    {
        if (WAYSTONE_TRIPS.containsKey(player.getUUID()))
        {
            return true;
        }
        for (ActivePortal portal : ACTIVE_PORTALS.values())
        {
            if (portal.openerUuid.equals(player.getUUID()) && portal.openerReachedDest)
            {
                return true;
            }
        }
        return false;
    }

    /** Fills a ring's pit with plain water, ready to be sung into a Crossing - how the ruined chamber's own waiting ring is laid (see {@code FairyPortalRuins}). */
    public static void fillWaterPit(ServerLevel level, BlockPos center)
    {
        for (BlockPos pos : waterPitPositions(center))
        {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        }
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
        // Never over a heart already sitting there - the ruined chamber keeps its own, full, for
        // whoever is stranded on this side (see FairyPortalRuins).
        if (!destLevel.getBlockState(destCenter).is(ModBlocks.HEART_CORE.get()))
        {
            destLevel.setBlockAndUpdate(destCenter, ModBlocks.MAGIC_CIRCLE.get().defaultBlockState()
                    .setValue(MagicCircleBlock.VARIANT, destLevel.random.nextInt(MagicCircleBlock.VARIANT_COUNT))
                    .setValue(MagicCircleBlock.COLOR, centerColor));
        }
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
            crossSoon(entity, serverLevel, () -> returnFromFairyRealm(entity, serverLevel));
            return;
        }
        if (serverLevel.dimension().equals(ModDimensions.FAIRY_REALM) && inHomewardPit(eyePos))
        {
            event.setCanceled(true);
            crossSoon(entity, serverLevel, () -> goHome(entity, serverLevel));
            return;
        }

        ActivePortal origin = findOriginAt(serverLevel.dimension(), eyePos);
        if (origin != null)
        {
            event.setCanceled(true);
            crossSoon(entity, serverLevel, () -> enterFairyRealm(entity, origin));
        }
    }

    /** Crossings noticed this tick, carried out at the start of the next - see {@link #crossSoon}. */
    private static final List<Crossing> pendingCrossings = new ArrayList<>();
    private static final Set<UUID> crossingEntities = new HashSet<>();

    /**
     * Queues a crossing for the start of the next server tick instead of making it on the spot.
     *
     * <p>The drowning check that notices a crossing runs in the middle of the entity's own tick,
     * and vanilla ends a player's tick by putting them back where the tick began - in whichever
     * level they are in by then. A crossing made from inside it left the player standing at their
     * old overworld coordinates in the Fairy Realm for that moment, far outside the island, and
     * the realm's shield ({@code FairyRealmShield}) duly shoved them back onto its surface - under
     * the island - before they ever reached the portal. A Lost Waystone never had the problem only
     * because using an item happens outside the player's tick. The air is refilled straight away,
     * so the drowning check doesn't keep firing while the crossing waits.
     */
    private static void crossSoon(LivingEntity entity, ServerLevel from, Runnable crossing)
    {
        entity.setAirSupply(entity.getMaxAirSupply());
        if (crossingEntities.add(entity.getUUID()))
        {
            pendingCrossings.add(new Crossing(entity, from, crossing));
        }
    }

    @SubscribeEvent
    public static void onServerTickStart(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.START || pendingCrossings.isEmpty())
        {
            return;
        }
        List<Crossing> batch = new ArrayList<>(pendingCrossings);
        pendingCrossings.clear();
        crossingEntities.clear();
        for (Crossing pending : batch)
        {
            // Skipped if it died, or went somewhere else by other means, in the meantime.
            if (pending.entity.isAlive() && !pending.entity.isRemoved() && pending.entity.level() == pending.from)
            {
                pending.crossing.run();
            }
        }
    }

    private record Crossing(LivingEntity entity, ServerLevel from, Runnable crossing)
    {
    }

    /**
     * Whether this block of portal fluid belongs to a portal that is open right now. Anything else is
     * left over from one that closed without being tidied - a server restart forgets every open
     * portal - and {@code block/FairyPortalWaterBlock} turns it back into the plain water it was.
     */
    public static boolean isLivePortalWater(Level level, BlockPos pos)
    {
        if (level.dimension().equals(ModDimensions.FAIRY_REALM) && (destWater != null && destWater.contains(pos) || inHomewardPit(pos)))
        {
            return true;
        }
        return findOriginAt(level.dimension(), pos) != null;
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
        ARRIVED.add(entity.getUUID());

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
            // Nowhere open on the other side: home is wherever they last slept.
            if (entity instanceof ServerPlayer player)
            {
                sendToRespawn(player, server, false);
            }
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
        ARRIVED.add(entity.getUUID());

        if (target == ownEntry)
        {
            closeEntry(server, target);
        }
    }

    private static boolean inHomewardPit(BlockPos pos)
    {
        return homewardPitAt(pos) != null;
    }

    @Nullable
    private static Set<BlockPos> homewardPitAt(BlockPos pos)
    {
        for (Set<BlockPos> water : HOMEWARD.values())
        {
            if (water.contains(pos))
            {
                return water;
            }
        }
        return null;
    }

    /**
     * Touching the bottom of a portal pit crosses at once - no waiting to drown (which still works
     * too, see {@link #onLivingDrown}). The bottom is the pit's lowest layer of portal water: feet
     * in it, with no more portal water beneath them. Anyone who has only just arrived through a pit
     * must leave its water once before its bottom counts for them - see {@link #ARRIVED}.
     */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel)
                || ACTIVE_PORTALS.isEmpty() && destWater == null && HOMEWARD.isEmpty() && ARRIVED.isEmpty())
        {
            return;
        }
        BlockPos feet = entity.blockPosition();
        boolean inRealm = serverLevel.dimension().equals(ModDimensions.FAIRY_REALM);
        Set<BlockPos> pit = null;
        Runnable crossing = null;
        if (inRealm && destWater != null && destWater.contains(feet))
        {
            pit = destWater;
            crossing = () -> returnFromFairyRealm(entity, serverLevel);
        }
        else if (inRealm && homewardPitAt(feet) != null)
        {
            pit = homewardPitAt(feet);
            crossing = () -> goHome(entity, serverLevel);
        }
        else
        {
            ActivePortal origin = findOriginAt(serverLevel.dimension(), feet);
            if (origin != null)
            {
                pit = origin.originWater;
                crossing = () -> enterFairyRealm(entity, origin);
            }
        }
        if (pit == null)
        {
            ARRIVED.remove(entity.getUUID());
            return;
        }
        if (ARRIVED.contains(entity.getUUID()) || pit.contains(feet.below()))
        {
            return;
        }
        crossSoon(entity, serverLevel, crossing);
    }

    /**
     * Through a pit lit from inside the Fairy Realm: a player goes back through their own way in if
     * one is still open behind them, and otherwise to wherever they last slept, or the world's spawn.
     * Anything else that drowns there goes out through an open portal if there is one.
     */
    private static void goHome(LivingEntity entity, ServerLevel fromLevel)
    {
        if (entity instanceof ServerPlayer player && !hasOwnEntry(player))
        {
            entity.setAirSupply(entity.getMaxAirSupply());
            sendToRespawn(player, fromLevel.getServer(), false);
            return;
        }
        returnFromFairyRealm(entity, fromLevel);
    }

    /** A homeward pit closes once its ring is broken or its water spoiled - never touching water the shared pool is still using. */
    private static void checkHomeward(MinecraftServer server)
    {
        ServerLevel fairyRealm = server.getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }
        Iterator<Map.Entry<BlockPos, Set<BlockPos>>> iterator = HOMEWARD.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<BlockPos, Set<BlockPos>> entry = iterator.next();
            if (!fairyRealm.isLoaded(entry.getKey()))
            {
                continue;
            }
            if (!PortalRitual.matchesBorder(fairyRealm, entry.getKey()) || !isPortalWaterIntact(server, ModDimensions.FAIRY_REALM, entry.getValue()))
            {
                iterator.remove();
                Set<BlockPos> water = new HashSet<>(entry.getValue());
                if (destWater != null)
                {
                    water.removeAll(destWater);
                }
                revertToWater(server, ModDimensions.FAIRY_REALM, water);
            }
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

    /** Whether this server run has checked the Fairy Realm's pool for fluid left over from before it started. */
    private static boolean staleDestinationCleared;

    /**
     * Every open portal is forgotten when the server stops, but the portal fluid it left in the
     * Fairy Realm's pool is saved with the world - so the pool came back looking like a portal and
     * doing nothing. Once per server run, before anything can open a new one, it goes back to water.
     */
    private static void clearStaleDestination(MinecraftServer server)
    {
        ServerLevel fairyRealm = server.getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null || destWater != null)
        {
            return;
        }
        for (BlockPos pos : waterPitPositions(com.patrickma.magiccircles.worldgen.FairyPortalRuins.portalCenter()))
        {
            if (fairyRealm.getBlockState(pos).is(ModBlocks.FAIRY_PORTAL_WATER.get()))
            {
                fairyRealm.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            }
        }
    }

    /**
     * In singleplayer the same game can start several worlds one after another, and none of this
     * belongs to the next one.
     */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event)
    {
        ACTIVE_PORTALS.clear();
        WAYSTONE_TRIPS.clear();
        HOMEWARD.clear();
        ARRIVED.clear();
        destWater = null;
        destLanding = null;
        staleDestinationCleared = false;
        pendingCrossings.clear();
        crossingEntities.clear();
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
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (!staleDestinationCleared)
        {
            staleDestinationCleared = true;
            clearStaleDestination(event.getServer());
        }
        if (!HOMEWARD.isEmpty() && event.getServer().getTickCount() % PORTAL_INTEGRITY_CHECK_INTERVAL == 0)
        {
            checkHomeward(event.getServer());
        }
        if (destWater == null)
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
