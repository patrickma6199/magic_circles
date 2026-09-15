package com.patrickma.magiccircles.worldgen;

import com.mojang.logging.LogUtils;
import com.patrickma.magiccircles.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Fairy Court: the Fairy Queen's throne room, hollowed out of the World Tree's own heartwood.
 *
 * <p>Where it goes was read straight out of the tree's structure ({@code god_tree.bin.gz}): the
 * heartwood is widest a little above where the hollow lower trunk ends, and around Y 151, centred
 * just east and south of the Wellspring ({@link #CENTER}), it is solid wood at least nine blocks out
 * for a dozen blocks up, and at least seven for twenty. So the court is a round hall fifteen blocks
 * across and eight high, ringed by four pillars, with the throne on a two-step dais against its
 * west wall - and above it a shaft rising into the trunk to twenty-two blocks over the floor, its
 * crown set with shroomlight. Five ledges are cut into the shaft's walls for the queen's people to
 * stand on and listen, so her court never crowds the floor. The lining shades up the walls from
 * the court's own planks, through stripped wood, to the tree's bare heartwood at the crown.
 *
 * <p>The way in is a great pointed arch through the east wall, framed in stripped wood with a
 * lantern hung at its point at either end, blossom climbing the bark round it, a little ledge before
 * it - and veins of the court's planks creeping out across the bark from it, as if the court were
 * slowly growing into the tree. East was chosen because it opens onto the court's flight line (see
 * {@link #routeTo}): from just outside the arch a flyer has open air all the way out past the
 * canopy along a line twenty degrees south of east, checked through the whole structure.
 *
 * <p>Carved once per version of the court (see {@link ThroneRoomData}) - on a fresh world right
 * after the tree is placed, and on an existing one the next time it starts. Each carving first puts
 * its whole area back to the tree's own blocks, so a court carved by an earlier version is rebuilt
 * cleanly rather than layered over; a world with the very first court (high in the trunk at Y 201)
 * has that room filled back in too. {@code entity/FairyQueenEntity} holds court here.
 */
public final class FairyThroneRoom
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The room's floor, at its centre. Y 151 - the ground's own height plus the tree's first 48 blocks. */
    public static final BlockPos CENTER = new BlockPos(WorldTree.CENTER_X + 3, WorldTree.groundY() + 48, WorldTree.CENTER_Z + 2);
    private static final double RADIUS = 7.5;
    /** The hall is full width this high; the shaft above it rises to {@link #HEIGHT}. */
    private static final int HALL_HEIGHT = 8;
    private static final double SHAFT_RADIUS = 5.5;
    private static final int HEIGHT = 22;
    /** The arch runs east from the room's edge to the bark... */
    private static final int TUNNEL_START = 7;
    private static final int BARK = 11;
    /** ...and a ledge runs on past it to here. */
    private static final int LEDGE_END = 13;
    /** How far past the bark anything leafy hanging in the way is cleared. */
    private static final int CLEAR_TO = 18;

    /** The throne's seat - on the dais against the west wall, facing the arch. */
    public static final BlockPos SEAT = CENTER.offset(-6, 3, 0);
    /** Where the queen stands in place while seated: hips on the seat, feet on the dais. */
    public static final Vec3 SEATED_POSITION = new Vec3(SEAT.getX() + 0.5, SEAT.getY() - 0.26, SEAT.getZ() + 0.5);
    /** Facing east, toward the arch. */
    public static final float SEAT_YAW = -90.0f;
    /** Just before the dais steps - where she flies to before sitting down. */
    public static final Vec3 APPROACH = new Vec3(CENTER.getX() - 1.5, CENTER.getY() + 2.3, CENTER.getZ() + 0.5);
    /** The line of the fallen faces the throne. */
    public static final float SOUL_YAW = 90.0f;

    // The court's flight line - see routeTo.
    private static final double FLIGHT_Y = CENTER.getY() + 2.0;
    private static final Vec3 INSIDE = new Vec3(CENTER.getX() + 4.5, CENTER.getY() + 1.6, CENTER.getZ() + 0.5);
    private static final Vec3 MOUTH = new Vec3(CENTER.getX() + 9.5, CENTER.getY() + 1.6, CENTER.getZ() + 0.5);
    private static final Vec3 LANDING = new Vec3(CENTER.getX() + 15.5, FLIGHT_Y, CENTER.getZ() + 0.5);
    /** Twenty degrees south of east from the room: open air from the landing out past the canopy's edge. */
    private static final double CORRIDOR_ANGLE = Math.toRadians(20.0);
    private static final double[] CORRIDOR_STEPS = {30.0, 55.0, 84.0};
    /** Round the tree at this distance from the trunk - clear of the canopy, which reaches about 76. */
    private static final double RING_RADIUS = 90.0;
    private static final double RING_STEP = Math.toRadians(20.0);

    /**
     * A ledge cut into the shaft wall for a fairy to stand on and listen: the way it opens off the
     * shaft, and its floor's height above the court's. Spread round the shaft and staggered up it,
     * each where the trunk is thickest in that direction.
     */
    private record Ledge(double angle, int height)
    {
    }

    private static final Ledge[] LEDGES = {
            new Ledge(Math.toRadians(60.0), 11), new Ledge(Math.toRadians(135.0), 14), new Ledge(Math.toRadians(205.0), 11),
            new Ledge(Math.toRadians(255.0), 15), new Ledge(Math.toRadians(315.0), 12)
    };
    private static final double LEDGE_INNER = 5.0;
    private static final double LEDGE_OUTER = 7.7;
    private static final double LEDGE_HALF_WIDTH = 1.3;
    private static final double LEDGE_STAND = 6.4;
    private static final int LEDGE_HEADROOM = 3;

    /** The three who stand on the floor along the runner, nearest the throne first. The rest go up to the ledges. */
    private static final int[][] FLOOR_PLACES = {{-1, -3}, {-1, 3}, {2, -3}};
    public static final int COURTIER_PLACES = FLOOR_PLACES.length + LEDGES.length;
    private static final Map<UUID, Integer> COURTIERS = new HashMap<>();

    /** The first court, high in the trunk, which a world that had it has filled back in. */
    private static final BlockPos OLD_CENTER = new BlockPos(WorldTree.CENTER_X + 2, WorldTree.groundY() + 98, WorldTree.CENTER_Z + 6);
    private static final int VERSION = 3;

    private FairyThroneRoom()
    {
    }

    /** Saved with the Fairy Realm: which version of the court, if any, has been carved into this world's tree. */
    public static final class ThroneRoomData extends SavedData
    {
        boolean carved;
        int version;

        public static ThroneRoomData load(CompoundTag tag)
        {
            ThroneRoomData data = new ThroneRoomData();
            data.carved = tag.getBoolean("Carved");
            data.version = tag.contains("Version") ? tag.getInt("Version") : data.carved ? 1 : 0;
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("Carved", this.carved);
            tag.putInt("Version", this.version);
            return tag;
        }
    }

    /** Whether the throne is there to be sat on - loaded, and not broken up. */
    public static boolean throneStands(ServerLevel level)
    {
        return level.isLoaded(SEAT) && level.getBlockState(SEAT).is(ModBlocks.LIVING_WOOD_STAIRS.get());
    }

    public static void carveIfNeeded(ServerLevel fairyRealm)
    {
        ThroneRoomData data = fairyRealm.getDataStorage().computeIfAbsent(ThroneRoomData::load, ThroneRoomData::new,
                "magiccircles_fairy_court_room");
        if (data.carved && data.version >= VERSION)
        {
            return;
        }
        int previous = data.carved ? data.version : 0;
        BlockPos newMin = CENTER.offset(-10, -10, -13);
        BlockPos newMax = CENTER.offset(CLEAR_TO + 2, HEIGHT + 2, 13);
        loadChunks(fairyRealm, newMin, newMax);
        if (previous == 1)
        {
            // The very first court goes back to being the tree's heartwood.
            BlockPos oldMin = OLD_CENTER.offset(-6, 0, -6);
            BlockPos oldMax = OLD_CENTER.offset(12, 8, 6);
            loadChunks(fairyRealm, oldMin, oldMax);
            WorldTree.restoreOriginal(fairyRealm, oldMin, oldMax);
            relight(fairyRealm, oldMin, oldMax);
        }

        carve(fairyRealm);
        relight(fairyRealm, newMin, newMax);
        data.carved = true;
        data.version = VERSION;
        data.setDirty();
        LOGGER.info("Carved the Fairy Court into the World Tree at {}{}.", CENTER,
                previous == 1 ? ", filling the old court at " + OLD_CENTER + " back in"
                        : previous > 1 ? ", rebuilding the court carved by version " + previous : "");
    }

    private static void loadChunks(ServerLevel level, BlockPos min, BlockPos max)
    {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
        {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
            {
                level.getChunk(x, z);
            }
        }
    }

    private static void relight(ServerLevel level, BlockPos min, BlockPos max)
    {
        WorldEditRelight.relight(level, min.getX() >> 4, max.getX() >> 4, min.getZ() >> 4, max.getZ() >> 4);
    }

    // ------------------------------------------------------------------
    // Finding the way
    // ------------------------------------------------------------------

    /**
     * The way from anywhere to {@code destination} in the court, as waypoints: round the outside of
     * the tree - clear of the canopy - to the far end of the court's flight line, in along it to the
     * ledge, through the arch, and across the room; and for a ledge up in the shaft, straight up the
     * middle of it first and then across, so nobody clips the hall's ceiling on the way. Whatever part
     * of that a flyer is already past, it skips. Flown by {@code entity/CourtFlight}.
     */
    public static List<Vec3> routeTo(Vec3 from, Vec3 destination)
    {
        List<Vec3> route = new ArrayList<>();
        if (inRoom(from))
        {
            arrive(route, destination);
            return route;
        }
        if (inArchway(from))
        {
            route.add(INSIDE);
            arrive(route, destination);
            return route;
        }
        if (from.distanceTo(LANDING) > 8.0)
        {
            double along = alongCorridor(from);
            double off = offCorridor(from);
            double outerEnd = CORRIDOR_STEPS[CORRIDOR_STEPS.length - 1];
            if (along > 10.0 && along < outerEnd + 12.0 && off < 6.0 && Math.abs(from.y - FLIGHT_Y) < 8.0)
            {
                // Already on the flight line: in along it from here.
                addCorridorInward(route, along);
            }
            else
            {
                double fromTree = Math.hypot(from.x - WorldTree.CENTER_X, from.z - WorldTree.CENTER_Z);
                double angle = Math.atan2(from.z - WorldTree.CENTER_Z, from.x - WorldTree.CENTER_X);
                if (fromTree < RING_RADIUS - 4.0)
                {
                    // Among the branches: straight out from the trunk first, at the height it is.
                    route.add(ringPoint(angle, from.y));
                }
                Vec3 outer = corridorPoint(outerEnd);
                double target = Math.atan2(outer.z - WorldTree.CENTER_Z, outer.x - WorldTree.CENTER_X);
                double sweep = Math.atan2(Math.sin(target - angle), Math.cos(target - angle));
                int steps = Math.max(1, (int) Math.ceil(Math.abs(sweep) / RING_STEP));
                for (int i = 1; i <= steps; i++)
                {
                    route.add(ringPoint(angle + sweep * i / steps, FLIGHT_Y));
                }
                addCorridorInward(route, Double.MAX_VALUE);
            }
        }
        route.add(LANDING);
        route.add(MOUTH);
        route.add(INSIDE);
        arrive(route, destination);
        return route;
    }

    /** The last of the way: up the middle of the shaft first if the place is above the hall. */
    private static void arrive(List<Vec3> route, Vec3 destination)
    {
        if (destination.y > CENTER.getY() + HALL_HEIGHT + 0.5)
        {
            route.add(new Vec3(CENTER.getX() + 0.5, destination.y + 0.2, CENTER.getZ() + 0.5));
        }
        route.add(destination);
    }

    public static boolean inRoom(Vec3 pos)
    {
        return Math.hypot(pos.x - (CENTER.getX() + 0.5), pos.z - (CENTER.getZ() + 0.5)) < RADIUS - 0.3
                && pos.y >= CENTER.getY() + 0.5 && pos.y <= CENTER.getY() + HEIGHT;
    }

    private static boolean inArchway(Vec3 pos)
    {
        return pos.x >= CENTER.getX() + TUNNEL_START && pos.x <= CENTER.getX() + LEDGE_END
                && Math.abs(pos.z - (CENTER.getZ() + 0.5)) <= 2.5
                && pos.y >= CENTER.getY() + 0.5 && pos.y <= CENTER.getY() + 8.0;
    }

    private static double alongCorridor(Vec3 pos)
    {
        return (pos.x - CENTER.getX() - 0.5) * Math.cos(CORRIDOR_ANGLE) + (pos.z - CENTER.getZ() - 0.5) * Math.sin(CORRIDOR_ANGLE);
    }

    private static double offCorridor(Vec3 pos)
    {
        return Math.abs(-(pos.x - CENTER.getX() - 0.5) * Math.sin(CORRIDOR_ANGLE) + (pos.z - CENTER.getZ() - 0.5) * Math.cos(CORRIDOR_ANGLE));
    }

    private static Vec3 corridorPoint(double distance)
    {
        return new Vec3(CENTER.getX() + 0.5 + Math.cos(CORRIDOR_ANGLE) * distance, FLIGHT_Y,
                CENTER.getZ() + 0.5 + Math.sin(CORRIDOR_ANGLE) * distance);
    }

    /** Every waypoint of the flight line nearer in than {@code from}, outermost first. */
    private static void addCorridorInward(List<Vec3> route, double from)
    {
        for (int i = CORRIDOR_STEPS.length - 1; i >= 0; i--)
        {
            if (CORRIDOR_STEPS[i] < from - 2.0)
            {
                route.add(corridorPoint(CORRIDOR_STEPS[i]));
            }
        }
    }

    private static Vec3 ringPoint(double angle, double y)
    {
        return new Vec3(WorldTree.CENTER_X + Math.cos(angle) * RING_RADIUS, Math.max(y, WorldTree.groundY() + 4.0),
                WorldTree.CENTER_Z + Math.sin(angle) * RING_RADIUS);
    }

    // ------------------------------------------------------------------
    // Where everyone stands
    // ------------------------------------------------------------------

    /** Where the {@code index}th soul in line stands: along the north wall, from the throne's end. */
    public static Vec3 soulPlace(int index)
    {
        return new Vec3(CENTER.getX() - 2 + index + 0.5, CENTER.getY() + 1, CENTER.getZ() - 5 + 0.5);
    }

    /** Where courtier {@code place} stands: the first three on the floor along the runner, the rest out on the ledges. */
    public static Vec3 courtierPlace(int place)
    {
        if (place < FLOOR_PLACES.length)
        {
            int[] spot = FLOOR_PLACES[place];
            return new Vec3(CENTER.getX() + spot[0] + 0.5, CENTER.getY() + 1, CENTER.getZ() + spot[1] + 0.5);
        }
        Ledge ledge = LEDGES[Math.min(place - FLOOR_PLACES.length, LEDGES.length - 1)];
        return new Vec3(CENTER.getX() + 0.5 + Math.cos(ledge.angle()) * LEDGE_STAND, CENTER.getY() + ledge.height(),
                CENTER.getZ() + 0.5 + Math.sin(ledge.angle()) * LEDGE_STAND);
    }

    /** The two floor rows face each other across the runner; on a ledge, a fairy faces out over the court. */
    public static float courtierYaw(int place)
    {
        if (place < FLOOR_PLACES.length)
        {
            return FLOOR_PLACES[place][1] < 0 ? 0.0f : 180.0f;
        }
        Ledge ledge = LEDGES[Math.min(place - FLOOR_PLACES.length, LEDGES.length - 1)];
        return (float) Math.toDegrees(ledge.angle()) + 90.0f;
    }

    /** A place for {@code fairy} to stand at court - the one it already has, or the first free one, or -1 if the court is full. */
    public static synchronized int claimCourtierPlace(ServerLevel level, UUID fairy)
    {
        // Anyone holding a place who is no longer about gives it up.
        COURTIERS.keySet().removeIf(id -> !id.equals(fairy) && !(level.getEntity(id) instanceof LivingEntity living && living.isAlive()));
        Integer held = COURTIERS.get(fairy);
        if (held != null)
        {
            return held;
        }
        for (int place = 0; place < COURTIER_PLACES; place++)
        {
            if (!COURTIERS.containsValue(place))
            {
                COURTIERS.put(fairy, place);
                return place;
            }
        }
        return -1;
    }

    public static synchronized void releaseCourtierPlace(UUID fairy)
    {
        COURTIERS.remove(fairy);
    }

    public static synchronized void releaseAllCourtiers()
    {
        COURTIERS.clear();
    }

    // ------------------------------------------------------------------
    // The shape of it
    // ------------------------------------------------------------------

    /** How far out the court is open at each height above its floor: the hall, the shaft, and the crown of the shaft rounding in. */
    private static double radiusAt(int height)
    {
        if (height <= HALL_HEIGHT)
        {
            return RADIUS;
        }
        return switch (height)
        {
            case 20 -> 4.6;
            case 21 -> 3.6;
            case 22 -> 2.2;
            default -> SHAFT_RADIUS;
        };
    }

    /** Whether the block {@code dx, dz} across from the centre and {@code h} up is inside a ledge's opening. */
    private static boolean inLedge(int dx, int h, int dz)
    {
        for (Ledge ledge : LEDGES)
        {
            if (h >= ledge.height() && h < ledge.height() + LEDGE_HEADROOM && onLedgeFootprint(ledge, dx, dz))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean onLedgeFootprint(Ledge ledge, int dx, int dz)
    {
        double out = dx * Math.cos(ledge.angle()) + dz * Math.sin(ledge.angle());
        double across = -dx * Math.sin(ledge.angle()) + dz * Math.cos(ledge.angle());
        return out >= LEDGE_INNER && out <= LEDGE_OUTER && Math.abs(across) <= LEDGE_HALF_WIDTH;
    }

    /** Whether this block is open air in the finished court - hall, shaft or ledge - not counting the archway. */
    private static boolean isRoomAir(int dx, int h, int dz)
    {
        if (h < 1 || h > HEIGHT)
        {
            return false;
        }
        return Math.hypot(dx, dz) <= radiusAt(h) || inLedge(dx, h, dz);
    }

    /** The tree's own wood - vanilla wood of any kind, never anything the court itself has put there. */
    private static boolean isTreeWood(BlockState state)
    {
        if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.PLANKS))
        {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    /** A steady random number in [0, 1) for a block - the same every time, so carving twice carves the same. */
    private static double noise(int x, int y, int z)
    {
        return ((Mth.getSeed(x, y, z) >>> 16) & 0xFFFF) / 65536.0;
    }

    // ------------------------------------------------------------------
    // The carving
    // ------------------------------------------------------------------

    private static void carve(ServerLevel level)
    {
        int cx = CENTER.getX();
        int cy = CENTER.getY();
        int cz = CENTER.getZ();

        // Back to the tree's own wood first, so an earlier court is rebuilt rather than built over.
        WorldTree.restoreOriginal(level, CENTER.offset(-10, -1, -13), CENTER.offset(BARK + 6, HEIGHT + 2, 13));
        hollowChamber(level, cx, cy, cz);
        lineWalls(level, cx, cy, cz);
        layFloor(level, cx, cy, cz);
        layLedges(level, cx, cy, cz);
        boreEntrance(level, cx, cy, cz);
        spreadVeins(level, cx, cy, cz);
        buildThrone(level, cx, cy, cz);
        raisePillars(level, cx, cy, cz);
        light(level, cx, cy, cz);
        plantFlowers(level, cx, cy, cz);
        layRunner(level, cx, cy, cz);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state)
    {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    /** Places only into open air - never over anything already there, the tree's or anyone's. */
    private static boolean placeIfAir(ServerLevel level, int x, int y, int z, BlockState state)
    {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir())
        {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /** Replaces only what is solid - facing the room with trim, without filling any of the room in. */
    private static void replaceIfSolid(ServerLevel level, int x, int y, int z, BlockState state)
    {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir())
        {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState blossom()
    {
        return Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static void hollowChamber(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        int reach = (int) Math.ceil(LEDGE_OUTER) + 1;
        for (int h = 1; h <= HEIGHT; h++)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (isRoomAir(dx, h, dz))
                    {
                        set(level, cx + dx, cy + h, cz + dz, air);
                    }
                }
            }
        }
    }

    /**
     * Every face of the court's walls and ceiling, lined by height: the court's own Living Wood
     * planks round the hall, giving way through a speckled band to stripped Living Wood up the shaft,
     * which thins in turn until the tree's bare heartwood shows through at the top - where the crown
     * of the shaft is set thick with shroomlight, scattering a little way down. Nothing but the tree's
     * own wood is ever lined over.
     */
    private static void lineWalls(ServerLevel level, int cx, int cy, int cz)
    {
        int reach = (int) Math.ceil(LEDGE_OUTER) + 2;
        for (int h = 1; h <= HEIGHT + 1; h++)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (isRoomAir(dx, h, dz) || !facesRoom(dx, h, dz))
                    {
                        continue;
                    }
                    BlockPos pos = new BlockPos(cx + dx, cy + h, cz + dz);
                    if (!isTreeWood(level.getBlockState(pos)))
                    {
                        continue;
                    }
                    BlockState lining = liningFor(h, noise(pos.getX(), pos.getY(), pos.getZ()));
                    if (lining != null)
                    {
                        level.setBlock(pos, lining, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // Shroomlight set into the walls: round the hall at head height and higher, and up the shaft.
        for (double degrees : new double[]{45.0, 90.0, 135.0, 225.0, 270.0, 315.0})
        {
            glowInWall(level, cx, cy, cz, Math.toRadians(degrees), 3);
            glowInWall(level, cx, cy, cz, Math.toRadians(degrees), 7);
        }
        for (double degrees : new double[]{20.0, 100.0, 170.0, 290.0})
        {
            glowInWall(level, cx, cy, cz, Math.toRadians(degrees), 13);
            glowInWall(level, cx, cy, cz, Math.toRadians(degrees), 17);
        }
    }

    private static boolean facesRoom(int dx, int h, int dz)
    {
        return isRoomAir(dx + 1, h, dz) || isRoomAir(dx - 1, h, dz) || isRoomAir(dx, h + 1, dz)
                || isRoomAir(dx, h - 1, dz) || isRoomAir(dx, h, dz + 1) || isRoomAir(dx, h, dz - 1);
    }

    /** What lines the wall this high up - null for the tree's bare wood, left as it is. */
    private static BlockState liningFor(int h, double chance)
    {
        BlockState planks = ModBlocks.LIVING_WOOD_PLANKS.get().defaultBlockState();
        BlockState stripped = ModBlocks.STRIPPED_LIVING_WOOD.get().defaultBlockState();
        if (h >= 20 && chance < 0.75 || h >= 18 && h < 20 && chance < 0.12)
        {
            return Blocks.SHROOMLIGHT.defaultBlockState();
        }
        if (h <= HALL_HEIGHT)
        {
            return planks;
        }
        if (h <= 12)
        {
            return chance < (h - HALL_HEIGHT) / 5.0 ? stripped : planks;
        }
        if (h <= 15)
        {
            return stripped;
        }
        if (h <= 19)
        {
            return chance < (h - 15) / 5.0 ? null : stripped;
        }
        return null;
    }

    /** The first wall block met going out from the middle at this height and heading - lit with shroomlight. */
    private static void glowInWall(ServerLevel level, int cx, int cy, int cz, double angle, int h)
    {
        for (double r = 0.0; r < 12.0; r += 0.5)
        {
            int x = cx + (int) Math.round(Math.cos(angle) * r);
            int z = cz + (int) Math.round(Math.sin(angle) * r);
            BlockPos pos = new BlockPos(x, cy + h, z);
            if (!level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, Blocks.SHROOMLIGHT.defaultBlockState(), Block.UPDATE_CLIENTS);
                return;
            }
        }
    }

    /** Living Wood planks, with a ring of stripped Living Wood set into them. */
    private static void layFloor(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState planks = ModBlocks.LIVING_WOOD_PLANKS.get().defaultBlockState();
        BlockState inlay = ModBlocks.STRIPPED_LIVING_WOOD.get().defaultBlockState();
        int reach = (int) Math.ceil(RADIUS);
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                double d = Math.hypot(dx, dz);
                if (d <= RADIUS)
                {
                    set(level, cx + dx, cy, cz + dz, d >= 4.6 && d < 5.4 ? inlay : planks);
                }
            }
        }
    }

    /** Each ledge's floor, a lip of stripped wood, with a blossom tucked in at the back of it. */
    private static void layLedges(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState lip = ModBlocks.STRIPPED_LIVING_WOOD.get().defaultBlockState();
        int reach = (int) Math.ceil(LEDGE_OUTER) + 1;
        for (Ledge ledge : LEDGES)
        {
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (onLedgeFootprint(ledge, dx, dz) && Math.hypot(dx, dz) > SHAFT_RADIUS)
                    {
                        set(level, cx + dx, cy + ledge.height() - 1, cz + dz, lip);
                    }
                }
            }
            int backX = cx + (int) Math.round(Math.cos(ledge.angle()) * (LEDGE_OUTER + 0.6));
            int backZ = cz + (int) Math.round(Math.sin(ledge.angle()) * (LEDGE_OUTER + 0.6));
            replaceIfSolid(level, backX, cy + ledge.height() + LEDGE_HEADROOM - 1, backZ, blossom());
        }
    }

    /** The opening of the arch, across it ({@code dz}) and up it ({@code h}): five wide, rising to a point seven high. */
    private static boolean inArch(int dz, int h)
    {
        int across = Math.abs(dz);
        return h >= 1 && (across <= 2 && h <= 5 || across <= 1 && h <= 6 || across == 0 && h <= 7);
    }

    /** The stripped-wood surround hugging the arch, its corners included. */
    private static boolean archFrame(int dz, int h)
    {
        if (h < 1 || inArch(dz, h))
        {
            return false;
        }
        return inArch(dz - 1, h) || inArch(dz + 1, h) || inArch(dz, h - 1) || inArch(dz - 1, h - 1) || inArch(dz + 1, h - 1);
    }

    /**
     * The way in: a great pointed arch through the east wall, five wide and seven high, floored with
     * stripped wood worn smooth. Its surround is stripped wood on the bark outside and on the court
     * wall within, with a lantern hung at its point at either end; outside, a ledge of planks on
     * brackets with a lantern-topped post at each corner, and blossom climbing the bark round it.
     * Nothing outside the bark is ever built over - the ledge and the blossom only fill open air.
     */
    private static void boreEntrance(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState worn = ModBlocks.STRIPPED_LIVING_WOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState trim = ModBlocks.STRIPPED_LIVING_WOOD.get().defaultBlockState();
        BlockState planks = ModBlocks.LIVING_WOOD_PLANKS.get().defaultBlockState();
        BlockState bracket = ModBlocks.LIVING_WOOD_STAIRS.get().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP);
        BlockState fence = ModBlocks.LIVING_WOOD_FENCE.get().defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

        for (int x = cx + TUNNEL_START; x <= cx + BARK; x++)
        {
            for (int dz = -3; dz <= 3; dz++)
            {
                for (int h = 1; h <= 8; h++)
                {
                    if (inArch(dz, h))
                    {
                        set(level, x, cy + h, cz + dz, air);
                    }
                }
                if (Math.abs(dz) <= 2)
                {
                    set(level, x, cy, cz + dz, worn);
                }
            }
        }
        for (int dz = -4; dz <= 4; dz++)
        {
            for (int h = 1; h <= 9; h++)
            {
                if (archFrame(dz, h))
                {
                    set(level, cx + BARK, cy + h, cz + dz, trim);
                    replaceIfSolid(level, cx + TUNNEL_START, cy + h, cz + dz, trim);
                    replaceIfSolid(level, cx + TUNNEL_START + 1, cy + h, cz + dz, trim);
                }
            }
        }
        set(level, cx + TUNNEL_START + 1, cy + 7, cz, hanging);
        set(level, cx + BARK, cy + 7, cz, hanging);

        for (int x = cx + BARK + 1; x <= cx + CLEAR_TO; x++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                for (int h = 1; h <= 7; h++)
                {
                    BlockPos pos = new BlockPos(x, cy + h, cz + dz);
                    if (level.getBlockState(pos).getBlock() instanceof LeavesBlock)
                    {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        for (int x = cx + BARK + 1; x <= cx + LEDGE_END; x++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                placeIfAir(level, x, cy, cz + dz, planks);
            }
        }
        for (int dz = -2; dz <= 2; dz++)
        {
            placeIfAir(level, cx + BARK + 1, cy - 1, cz + dz, bracket);
        }
        for (int dz : new int[]{-2, 2})
        {
            if (placeIfAir(level, cx + LEDGE_END, cy + 1, cz + dz, fence))
            {
                placeIfAir(level, cx + LEDGE_END, cy + 2, cz + dz, lantern);
            }
        }

        for (int dz = -5; dz <= 5; dz++)
        {
            for (int h = 2; h <= 10; h++)
            {
                boolean besideFrame = archFrame(dz - 1, h) || archFrame(dz + 1, h) || archFrame(dz, h - 1) || archFrame(dz, h + 1);
                if (besideFrame && !archFrame(dz, h) && !inArch(dz, h)
                        && !level.getBlockState(new BlockPos(cx + BARK, cy + h, cz + dz)).isAir())
                {
                    placeIfAir(level, cx + BARK + 1, cy + h, cz + dz, blossom());
                }
            }
        }
    }

    /**
     * Veins of the court's own Living Wood planks creeping out across the bark from the arch -
     * thick where they leave it, forking and thinning as they wander off - and in along the
     * archway's own walls, thickest where it opens into the court: as if the court were slowly
     * growing into the tree around it. Only ever over the tree's own wood.
     */
    private static void spreadVeins(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState planks = ModBlocks.LIVING_WOOD_PLANKS.get().defaultBlockState();
        RandomSource random = RandomSource.create(0xFA1EC0DEL);
        int veins = 9;
        for (int i = 0; i < veins; i++)
        {
            double angle = Math.PI * 2.0 * i / veins + random.nextDouble() * 0.4;
            double dz = Math.cos(angle) * 3.5;
            double h = 4.0 + Math.sin(angle) * 4.5;
            crawl(level, planks, random, cx, cy, cz, dz, h, angle, 8 + random.nextInt(8), 0);
        }

        for (int x = cx + TUNNEL_START; x <= cx + BARK; x++)
        {
            double chance = 0.55 - 0.1 * (x - (cx + TUNNEL_START));
            for (int dz = -4; dz <= 4; dz++)
            {
                for (int h = 1; h <= 8; h++)
                {
                    boolean wall = !inArch(dz, h) && (inArch(dz - 1, h) || inArch(dz + 1, h) || inArch(dz, h - 1));
                    BlockPos pos = new BlockPos(x, cy + h, cz + dz);
                    if (wall && random.nextDouble() < chance && isTreeWood(level.getBlockState(pos)))
                    {
                        level.setBlock(pos, planks, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** One vein, wandering outward across the bark from where it starts, now and then forking. */
    private static void crawl(ServerLevel level, BlockState planks, RandomSource random, int cx, int cy, int cz,
                              double dz, double h, double angle, int length, int depth)
    {
        for (int step = 0; step < length; step++)
        {
            angle += (random.nextDouble() - 0.5) * 0.9;
            dz += Math.cos(angle);
            h += Math.sin(angle);
            int bz = (int) Math.round(dz);
            int bh = (int) Math.round(h);
            infect(level, planks, cx, cy, cz, bz, bh);
            if (step < 3 && depth == 0)
            {
                // Thick where it leaves the arch.
                infect(level, planks, cx, cy, cz, bz + (random.nextBoolean() ? 1 : -1), bh);
            }
            if (depth < 2 && step > 1 && random.nextDouble() < 0.18)
            {
                crawl(level, planks, random, cx, cy, cz, dz, h, angle + (random.nextBoolean() ? 0.8 : -0.8),
                        (length - step) / 2 + 1, depth + 1);
            }
        }
    }

    /** Turns the outermost face of the trunk at this spot on the bark into the court's planks, if it is the tree's own wood. */
    private static void infect(ServerLevel level, BlockState planks, int cx, int cy, int cz, int dz, int h)
    {
        for (int x = cx + BARK + 6; x >= cx + BARK - 2; x--)
        {
            BlockPos pos = new BlockPos(x, cy + h, cz + dz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LeavesBlock)
            {
                continue;
            }
            if (isTreeWood(state))
            {
                level.setBlock(pos, planks, Block.UPDATE_CLIENTS);
            }
            return;
        }
    }

    /**
     * Against the west wall, facing the arch: a dais of two steps with stairs up each, and on it the
     * throne - a Living Wood seat between stripped-wood arms, a tall bark back with stripped wood
     * either side, and above it the tree itself breaking into blossom around a glowing crest.
     */
    private static void buildThrone(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState planks = ModBlocks.LIVING_WOOD_PLANKS.get().defaultBlockState();
        BlockState step = ModBlocks.LIVING_WOOD_STAIRS.get().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState stripped = ModBlocks.STRIPPED_LIVING_WOOD_LOG.get().defaultBlockState();
        BlockState bark = ModBlocks.LIVING_WOOD_LOG.get().defaultBlockState();

        // The dais, in two steps.
        for (int x = cx - 7; x <= cx - 4; x++)
        {
            for (int z = cz - 3; z <= cz + 3; z++)
            {
                set(level, x, cy + 1, z, planks);
            }
        }
        for (int x = cx - 7; x <= cx - 5; x++)
        {
            for (int z = cz - 2; z <= cz + 2; z++)
            {
                set(level, x, cy + 2, z, planks);
            }
        }
        // Stairs up each step.
        for (int z = cz - 2; z <= cz + 2; z++)
        {
            set(level, cx - 3, cy + 1, z, step);
        }
        for (int z = cz - 1; z <= cz + 1; z++)
        {
            set(level, cx - 4, cy + 2, z, step);
        }
        // The seat: a stair whose tall half is its backrest, so whoever sits faces east.
        set(level, SEAT.getX(), SEAT.getY(), SEAT.getZ(), step);
        set(level, cx - 6, cy + 3, cz - 1, stripped);
        set(level, cx - 6, cy + 3, cz + 1, stripped);
        // The back, rising against the wall.
        for (int h = 3; h <= 8; h++)
        {
            set(level, cx - 7, cy + h, cz, bark);
        }
        for (int h = 3; h <= 7; h++)
        {
            set(level, cx - 7, cy + h, cz - 1, stripped);
            set(level, cx - 7, cy + h, cz + 1, stripped);
        }
        // The crest, and the tree flowering around it.
        set(level, cx - 7, cy + 9, cz, Blocks.SHROOMLIGHT.defaultBlockState());
        for (int h = 8; h <= 9; h++)
        {
            set(level, cx - 7, cy + h, cz - 1, blossom());
            set(level, cx - 7, cy + h, cz + 1, blossom());
        }
        set(level, cx - 7, cy + 7, cz - 2, blossom());
        set(level, cx - 7, cy + 7, cz + 2, blossom());
        set(level, cx - 8, cy + 8, cz - 2, blossom());
        set(level, cx - 8, cy + 8, cz + 2, blossom());
        // Flowers at the dais corners.
        set(level, cx - 4, cy + 2, cz - 2, Blocks.POTTED_BLUE_ORCHID.defaultBlockState());
        set(level, cx - 4, cy + 2, cz + 2, Blocks.POTTED_ALLIUM.defaultBlockState());
    }

    /** Four pillars of stripped wood holding up the hall's ceiling round the foot of the shaft, each flowering where it meets it. */
    private static void raisePillars(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState pillar = ModBlocks.STRIPPED_LIVING_WOOD_LOG.get().defaultBlockState();
        for (int sx : new int[]{-4, 4})
        {
            for (int sz : new int[]{-4, 4})
            {
                int x = cx + sx;
                int z = cz + sz;
                for (int h = 1; h <= HALL_HEIGHT; h++)
                {
                    set(level, x, cy + h, z, pillar);
                }
                for (Direction side : Direction.Plane.HORIZONTAL)
                {
                    placeIfAir(level, x + side.getStepX(), cy + HALL_HEIGHT, z + side.getStepZ(), blossom());
                }
            }
        }
    }

    /** Lanterns hung round the hall from its ceiling, where the shaft opens above. */
    private static void light(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int i = 0; i < 6; i++)
        {
            double angle = Math.PI * 2.0 * i / 6.0 + Math.PI / 6.0;
            int x = cx + (int) Math.round(Math.cos(angle) * 6.5);
            int z = cz + (int) Math.round(Math.sin(angle) * 6.5);
            if (!level.getBlockState(new BlockPos(x, cy + HALL_HEIGHT + 1, z)).isAir())
            {
                placeIfAir(level, x, cy + HALL_HEIGHT, z, lantern);
            }
        }
    }

    /** Along the foot of the wall: potted flowers, and flowering azalea on little beds of moss. */
    private static void plantFlowers(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState[] pots = {
                Blocks.POTTED_POPPY.defaultBlockState(), Blocks.POTTED_AZURE_BLUET.defaultBlockState(),
                Blocks.POTTED_OXEYE_DAISY.defaultBlockState(), Blocks.POTTED_CORNFLOWER.defaultBlockState(),
                Blocks.POTTED_LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.POTTED_PINK_TULIP.defaultBlockState()
        };
        int placed = 0;
        for (int i = 0; i < 20; i++)
        {
            double angle = Math.PI * 2.0 * i / 20.0;
            // Not in front of the way in (east), and not on the dais (west).
            double cos = Math.cos(angle);
            if (cos > 0.8 || cos < -0.5)
            {
                continue;
            }
            int x = cx + (int) Math.round(cos * (RADIUS - 0.6));
            int z = cz + (int) Math.round(Math.sin(angle) * (RADIUS - 0.6));
            if (!level.getBlockState(new BlockPos(x, cy + 1, z)).isAir())
            {
                continue;
            }
            if (i % 2 == 0)
            {
                set(level, x, cy, z, Blocks.MOSS_BLOCK.defaultBlockState());
                set(level, x, cy + 1, z, Blocks.FLOWERING_AZALEA.defaultBlockState());
            }
            else
            {
                set(level, x, cy + 1, z, pots[placed++ % pots.length]);
            }
        }
    }

    /** A pink runner, three wide, from the ledge through the arch to the foot of the dais. */
    private static void layRunner(ServerLevel level, int cx, int cy, int cz)
    {
        BlockState carpet = Blocks.PINK_CARPET.defaultBlockState();
        for (int x = cx - 2; x <= cx + BARK; x++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                placeIfAir(level, x, cy + 1, cz + dz, carpet);
            }
        }
    }
}
