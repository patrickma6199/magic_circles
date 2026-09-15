package com.patrickma.magiccircles.worldgen;

import com.mojang.logging.LogUtils;
import com.patrickma.magiccircles.FairyPortalManager;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Where "Zuzo's Crossing" actually lands in the Fairy Realm - a ruined stone chamber built into
 * the base of a natural-looking hill, reached by a real staircase up through a cave-mouth
 * opening, rather than a portal simply appearing in open air (an earlier version's {@code
 * FairyPortalManager#allocateDestSlot} placed the very first portal wherever the golden-angle
 * spread's slot 0 landed, which happened to fall inside the World Tree's own canopy - "in the
 * middle of the bushes," not anywhere that reads as a real, pre-existing arrival point).
 *
 * <p>Like {@link WorldTree}, this is a one-time world edit run once from {@link
 * #onServerStarted}, gated by its own {@link RuinsSavedData} flag - not a per-chunk worldgen
 * hook, since it needs the same unrestricted, already-loaded-level access {@code WorldTree}
 * does. Placed along the tree's own entrance axis (+Z from {@link WorldTree#CENTER_X}/{@link
 * WorldTree#CENTER_Z}, {@value #DISTANCE_BEYOND_TREE} blocks past {@link WorldTree#maxRadius()}
 * so it never overlaps the tree's own canopy) - {@code FairyPortalManager} points its very first
 * portal destination ({@link #portalCenter()}) here.
 *
 * <p>An earlier version buried the chamber deep underground behind a rubble-filled shaft that had
 * to be dug through - reported as reading like a tomb, with a real bug besides (a stray, oddly
 * isolated single layer of cobblestone that didn't match anything else in the chamber, right
 * where the shaft was supposed to open up). Both are gone now: the whole "dig your way out"
 * mechanic is replaced by a real, immediately walkable staircase, and the chamber itself sits
 * shallow enough to be built into the base of an actual hill (see {@link #buildHill}) rather than
 * buried under flat ground.
 *
 * <p>Order matters here, more than in {@link WorldTree}: the hill has to exist before anything
 * gets carved through it (the staircase, the skylight shaft), and the actual pre-hill ground
 * height is queried live (via {@code ServerLevel#getHeight}) rather than assumed, so this places
 * correctly regardless of what {@link FairyRealmChunkGenerator} actually generated at this exact
 * spot.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyPortalRuins
{
    private static final long SEED = 20260907L;

    // How far past the tree's own outer reach (see WorldTree#maxRadius) the chamber's center
    // sits, along the same +Z entrance axis the tree's own door faces - comfortably clears the
    // canopy/branches with room to walk between the two.
    private static final int DISTANCE_BEYOND_TREE = 40;

    private static final int CHAMBER_HALF_WIDTH = 5; // 11 wide (X)
    private static final int CHAMBER_HALF_DEPTH = 5; // 11 deep (Z)
    private static final int CHAMBER_INTERIOR_HEIGHT = 7;
    // How far below the *original* (pre-hill) ground level the chamber floor sits - shallow on
    // purpose, so the room reads as built into a hill's base rather than buried like a tomb; the
    // hill mounded on top (see #buildHill) is what actually buries the ceiling, not depth alone.
    private static final int DEPTH_BELOW_SURFACE = 9;

    // The hill the chamber sits under - a simple parabolic dome mounded directly on top of
    // whatever FairyRealmChunkGenerator already put there (queried live, not assumed), centered
    // on the chamber itself.
    private static final int HILL_RADIUS = 22;
    // Doubled from the original 10 - every "layer" of the dome's parabolic falloff now builds two
    // blocks tall instead of one, so the same silhouette just reads as a proper hill rather than a
    // low mound.
    private static final int HILL_HEIGHT = 20;

    // The pedestal the portal itself stands on - a small raised dais at the chamber's dead
    // center, one block taller than the surrounding floor.
    private static final int PEDESTAL_RADIUS = 2; // 5x5
    private static final int PEDESTAL_HEIGHT = 1;

    // The tree's own entrance, as an offset from WorldTree's own center rather than an absolute
    // world position - given directly (confirmed by standing on both doorway blocks at world
    // (2,103,74.353) and (3,103,74.353) while the tree sits at its current CENTER_X/CENTER_Z of
    // (0,0)) rather than derived from the structure data, since there's no per-block "this is the
    // door" information captured anywhere. Stored relative to the tree's own center specifically
    // so the path in #pavePathToTree stays correct if the tree's own placement ever moves -
    // as long as the entrance stays in the same position *relative to the tree itself*.
    private static final int TREE_ENTRANCE_X_OFFSET = 2; // the doorway spans offsets 2 and 3; 2 is its near edge
    private static final int TREE_ENTRANCE_Z_OFFSET = 74;
    private static final int PATH_STEP_INTERVAL = 4;

    // Wall/ceiling material weights - mostly structural stone brick and mossy stone brick, with
    // cobblestone/mossy cobblestone and a little stone and cracked stone brick as the "this has
    // decayed" minority, rather than one uniform block.
    private static final BlockState[] WALL_PALETTE = {
            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
    };
    private static final BlockState[] CAVE_IN_PALETTE = {
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.MOSSY_COBBLESTONE.defaultBlockState()
    };
    private static final BlockState[] FLOOR_PALETTE = {
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.MOSSY_COBBLESTONE.defaultBlockState()
    };
    private static final BlockState[] PATH_PALETTE = {
            Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
    };
    private static final double WALL_GAP_CHANCE = 0.06;

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState PEDESTAL_TOP = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();

    private FairyPortalRuins()
    {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm != null)
        {
            // Must run before the getChunk calls below - see FairyRealmChunkGenerator#ensureTerrainReady.
            FairyRealmChunkGenerator.ensureTerrainReady(fairyRealm);
            // Explicit, not left to Forge's own (unspecified) event dispatch order - see
            // WorldTree#placeIfNeeded's own doc comment for why this matters here.
            WorldTree.placeIfNeeded(fairyRealm);
            placeIfNeeded(fairyRealm);
            placeCrossingIfNeeded(fairyRealm);
        }
    }

    public static int centerX()
    {
        return WorldTree.CENTER_X;
    }

    public static synchronized int centerZ()
    {
        return WorldTree.CENTER_Z + WorldTree.maxRadius() + DISTANCE_BEYOND_TREE;
    }

    public static int floorY()
    {
        return WorldTree.groundY() - DEPTH_BELOW_SURFACE;
    }

    /** Where {@code FairyPortalManager} centers the very first portal's ring - dead center of the chamber, on top of the pedestal (see {@link #PEDESTAL_HEIGHT}). */
    public static BlockPos portalCenter()
    {
        return new BlockPos(centerX(), floorY() + PEDESTAL_HEIGHT + 1, centerZ());
    }

    /** The hill's own flat apron radius (the hill mound plus its flat grass ring, *not* the further outer blend band that melts into natural terrain) - see {@code FairyLandmarkExclusionFilter}, which keeps trees from spawning within 5 blocks of this. */
    public static int flatApronRadius()
    {
        return HILL_RADIUS + FLAT_APRON_WIDTH;
    }

    // Deliberately NOT synchronized - see WorldTree#placeIfNeeded's own doc comment for the exact
    // same reasoning: this method's own chunk-force-loading calls can reentrantly need #centerZ
    // (via FairyLandmarkExclusionFilter) on a different thread, and holding this class's own
    // monitor for this whole body would deadlock against that the same way WorldTree's own
    // version did (confirmed via a real hung-server watchdog crash there).
    private static void placeIfNeeded(ServerLevel fairyRealm)
    {
        RuinsSavedData saved = fairyRealm.getDataStorage().computeIfAbsent(RuinsSavedData::load, RuinsSavedData::new, "magiccircles_portal_ruins");
        if (saved.placed)
        {
            return;
        }

        int cx = centerX();
        int cz = centerZ();
        int floorY = floorY();

        int treeEntranceX = WorldTree.CENTER_X + TREE_ENTRANCE_X_OFFSET;
        int treeEntranceZ = WorldTree.CENTER_Z + TREE_ENTRANCE_Z_OFFSET;

        int loadRadius = HILL_RADIUS + FLAT_APRON_WIDTH + OUTER_BLEND_WIDTH;
        int minChunkX = (Math.min(cx - loadRadius, Math.min(treeEntranceX, cx)) - 2) >> 4;
        int maxChunkX = (Math.max(cx + loadRadius, Math.max(treeEntranceX, cx)) + 2) >> 4;
        int minChunkZ = (Math.min(treeEntranceZ, cz - loadRadius) - 2) >> 4;
        int maxChunkZ = ((cz + loadRadius) + 2) >> 4;
        for (int cx2 = minChunkX; cx2 <= maxChunkX; cx2++)
        {
            for (int cz2 = minChunkZ; cz2 <= maxChunkZ; cz2++)
            {
                fairyRealm.getChunk(cx2, cz2);
            }
        }

        Random random = new Random(SEED);

        buildHill(fairyRealm, cx, cz);
        carveChamber(fairyRealm, random, cx, cz, floorY);
        buildPedestal(fairyRealm, cx, cz, floorY);
        carveSkylight(fairyRealm, random, cx, cz, floorY);
        placeLanterns(fairyRealm, cx, cz, floorY);
        int caveMouthZ = carveStaircase(fairyRealm, random, cx, cz, floorY);
        paveEntranceApron(fairyRealm, random, cx, caveMouthZ);
        pavePathToTree(fairyRealm, random, cx, caveMouthZ, treeEntranceX, treeEntranceZ);
        WorldEditRelight.relight(fairyRealm, minChunkX, maxChunkX, minChunkZ, maxChunkZ);

        saved.placed = true;
        saved.setDirty();
    }

    /**
     * A simple parabolic dome mounded directly on top of whatever terrain is already there
     * (queried live via {@code getHeight}, not assumed) - a natural-looking rise for the chamber
     * to sit under, rather than a chamber buried under otherwise-flat ground with nothing marking
     * it from outside.
     */
    // A flat grass ring just past the hill's own edge - matches the hill's own base height
    // (rather than whatever the general terrain happens to do there) so the mound sits on a
    // clean apron instead of running straight into whatever the surrounding hills/mountains are
    // doing, the same "give it a flat buffer before the natural terrain takes over" treatment
    // FairyRealmChunkGenerator's own FLAT_RADIUS already gives the World Tree.
    private static final int FLAT_APRON_WIDTH = 3;
    // Beyond the apron, a further band that gradually interpolates from the apron's own flat
    // height back to whatever the natural terrain already has there - without this, the apron's
    // hard edge met the mountain terrain in a single blocky step (however tall the difference
    // happened to be at that exact spot), which read as an obviously artificial seam rather than
    // a hill actually sitting in its own landscape.
    private static final int OUTER_BLEND_WIDTH = 14;

    private static void buildHill(ServerLevel level, int cx, int cz)
    {
        int apronRadius = HILL_RADIUS + FLAT_APRON_WIDTH;
        int outerRadius = apronRadius + OUTER_BLEND_WIDTH;

        // The biome's own Living Wood Trees can spawn absolutely anywhere, including right where
        // this hill is about to go up - cleared first, column by column, so every height query
        // below (all of them MOTION_BLOCKING_NO_LEAVES, which only ignores *leaves*, not logs)
        // reads the real ground rather than a tree trunk's own height. Without this, a tree
        // caught in the apron/blend radius got its trunk buried mid-column by the fill below and
        // its now-orphaned leaf canopy left floating - exactly what read as "dirt on top of a
        // tree." Removing the tree outright, rather than trying to preserve it, is also just the
        // more sensible call here: a stray tree poking out of a hand-built hill's flat apron
        // wouldn't have looked right even without the bug.
        for (int x = cx - outerRadius; x <= cx + outerRadius; x++)
        {
            for (int z = cz - outerRadius; z <= cz + outerRadius; z++)
            {
                if (Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz)) <= outerRadius)
                {
                    clearTreeBlocks(level, x, z);
                }
            }
        }

        int centerBaseTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;

        for (int x = cx - outerRadius; x <= cx + outerRadius; x++)
        {
            for (int z = cz - outerRadius; z <= cz + outerRadius; z++)
            {
                double dist = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                if (dist > outerRadius)
                {
                    continue;
                }

                if (dist <= HILL_RADIUS)
                {
                    int baseTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    double t = 1.0 - dist / HILL_RADIUS;
                    int extra = (int) Math.round(HILL_HEIGHT * t * t);
                    if (extra <= 0)
                    {
                        continue;
                    }
                    int newTop = baseTop + extra;
                    for (int y = baseTop + 1; y <= newTop; y++)
                    {
                        BlockState state = y == newTop ? GRASS : (y > newTop - 3 ? DIRT : STONE);
                        level.setBlock(new BlockPos(x, y, z), state, 2);
                    }
                }
                else if (dist <= apronRadius)
                {
                    // The flat apron: force this column to the hill's own base height,
                    // regardless of what the general terrain currently has there - filling up
                    // or clearing down as needed.
                    setColumnTop(level, x, z, centerBaseTop);
                }
                else
                {
                    // The outer blend: read whatever the natural terrain already put here
                    // (untouched by anything above, since this column hasn't been visited yet)
                    // and smoothly interpolate this column's own top between that and the
                    // apron's flat height, rather than leaving a hard seam right at the apron's
                    // own edge.
                    int naturalTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    double t = (dist - apronRadius) / OUTER_BLEND_WIDTH;
                    t = t * t * (3.0 - 2.0 * t);
                    int blendedTop = (int) Math.round(centerBaseTop + (naturalTop - centerBaseTop) * t);
                    setColumnTop(level, x, z, blendedTop);
                }
            }
        }
    }

    /**
     * Strips any Living Wood Tree log/leaves - and bamboo - out of this one column, top to
     * bottom, before {@link #buildHill} ever reads a height here. Bamboo turned out to be the
     * same bug as the tree one all over again: {@code MOTION_BLOCKING_NO_LEAVES} counts a bamboo
     * stalk as solid ground (bamboo has real collision, unlike a leaf), so a stalk caught in the
     * apron/blend footprint inflated the read height exactly the way a tree trunk did, leaving a
     * floating grass cap sitting on top of an otherwise perfectly normal bamboo plant once the
     * hill's own fill ran. Whatever else can grow tall enough to trip this same heightmap quirk
     * belongs in this same list.
     */
    private static void clearTreeBlocks(ServerLevel level, int x, int z)
    {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
        {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.is(ModBlocks.LIVING_WOOD_LOG.get()) || state.is(ModBlocks.LIVING_WOOD_LEAVES.get())
                    || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING))
            {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** Forces this one column's own top to {@code targetTop} - filling up with soil or clearing down to air as needed - used by both the hill's flat apron and its outer blend band. */
    private static void setColumnTop(ServerLevel level, int x, int z, int targetTop)
    {
        int currentTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (currentTop < targetTop)
        {
            for (int y = currentTop + 1; y <= targetTop; y++)
            {
                BlockState state = y == targetTop ? GRASS : (y > targetTop - 3 ? DIRT : STONE);
                level.setBlock(new BlockPos(x, y, z), state, 2);
            }
        }
        else if (currentTop > targetTop)
        {
            for (int y = targetTop + 1; y <= currentTop; y++)
            {
                level.setBlock(new BlockPos(x, y, z), Blocks.CAVE_AIR.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(x, targetTop, z), GRASS, 2);
            fillGapBelow(level, x, targetTop, z);
        }
    }

    /**
     * A defensive check for the grass cap {@link #setColumnTop} just placed at {@code capY}: the
     * outer blend band's {@code blendedTop} is a smooth interpolation between two sampled heights
     * (the hill's own center and this column's natural terrain), which - on real, non-monotonic
     * mountain terrain - doesn't always land on a Y where solid ground actually already exists.
     * When that happens, the cap this method just placed would otherwise be floating over
     * whatever gap is really there. This finds the first genuinely solid block below the cap
     * (searching a generous depth) and fills the gap in between with dirt/stone, so the cap is
     * always actually resting on something rather than assumed to be.
     */
    private static void fillGapBelow(ServerLevel level, int x, int capY, int z)
    {
        int y = capY - 1;
        int searchLimit = capY - 40;
        while (y > searchLimit && level.getBlockState(new BlockPos(x, y, z)).isAir())
        {
            y--;
        }
        if (y == capY - 1 || y <= searchLimit)
        {
            // Either already resting on something solid, or the gap is implausibly deep (more
            // likely a genuine chasm/void than a height-blend artifact) - leave it alone rather
            // than plugging what might be real terrain.
            return;
        }
        for (int fillY = y + 1; fillY < capY; fillY++)
        {
            BlockState state = fillY > capY - 4 ? DIRT : STONE;
            level.setBlock(new BlockPos(x, fillY, z), state, 2);
        }
    }

    /** The room itself: a floor, four walls, and a ceiling, each drawn from {@link #WALL_PALETTE}/{@link #FLOOR_PALETTE}, with the interior left open. */
    private static void carveChamber(ServerLevel level, Random random, int cx, int cz, int floorY)
    {
        int minX = cx - CHAMBER_HALF_WIDTH, maxX = cx + CHAMBER_HALF_WIDTH;
        int minZ = cz - CHAMBER_HALF_DEPTH, maxZ = cz + CHAMBER_HALF_DEPTH;
        int ceilingY = floorY + CHAMBER_INTERIOR_HEIGHT + 1;

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                boolean edgeX = x == minX || x == maxX;
                boolean edgeZ = z == minZ || z == maxZ;
                boolean isWallColumn = edgeX || edgeZ;

                level.setBlock(new BlockPos(x, floorY, z), randomFrom(random, FLOOR_PALETTE), 2);

                if (isWallColumn)
                {
                    for (int y = floorY + 1; y <= ceilingY; y++)
                    {
                        BlockState state = random.nextDouble() < WALL_GAP_CHANCE
                                ? Blocks.CAVE_AIR.defaultBlockState()
                                : randomFrom(random, WALL_PALETTE);
                        level.setBlock(new BlockPos(x, y, z), state, 2);
                    }
                }
                else
                {
                    for (int y = floorY + 1; y <= ceilingY - 1; y++)
                    {
                        level.setBlock(new BlockPos(x, y, z), Blocks.CAVE_AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(new BlockPos(x, ceilingY, z), randomFrom(random, WALL_PALETTE), 2);
                }
            }
        }
    }

    /**
     * Zuzo's Crossing, waiting on the pedestal for whoever is stranded here: blue and gold runes round a
     * Heart Core already full of mana, over a pit of water - everything but the redstone at the four
     * corners. Lay the redstone, touch a Fairy Horn to the heart, and the Crossing - sung from inside
     * the realm - opens the way home (see {@code FairyPortalManager#openWaterPortal}). Laid once per
     * world, on new worlds and existing ones alike ({@link RuinsSavedData#crossingPlaced}); redstone
     * someone has already put at a corner is left where it is.
     */
    private static void placeCrossingIfNeeded(ServerLevel level)
    {
        RuinsSavedData saved = level.getDataStorage().computeIfAbsent(RuinsSavedData::load, RuinsSavedData::new, "magiccircles_portal_ruins");
        if (!saved.placed || saved.crossingPlaced)
        {
            return;
        }
        BlockPos center = portalCenter();
        level.getChunk(center.getX() >> 4, center.getZ() >> 4);
        for (int dx = -2; dx <= 2; dx++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                BlockPos pos = center.offset(dx, 0, dz);
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (dx == 0 && dz == 0)
                {
                    continue;
                }
                if (inner)
                {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                else if (corner)
                {
                    if (!level.getBlockState(pos).is(Blocks.REDSTONE_WIRE))
                    {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                else
                {
                    // PortalRitual's own pattern: gold at the middle of the near and far edges, blue
                    // either side of it; blue at the middle of the two side edges, gold either side.
                    RuneColor color = Math.abs(dz) == 2 ? (dx == 0 ? RuneColor.GOLD : RuneColor.BLUE)
                            : (dz == 0 ? RuneColor.BLUE : RuneColor.GOLD);
                    level.setBlock(pos, ModBlocks.MAGIC_CIRCLE.get().defaultBlockState()
                            .setValue(MagicCircleBlock.VARIANT, level.random.nextInt(MagicCircleBlock.VARIANT_COUNT))
                            .setValue(MagicCircleBlock.COLOR, color), 2);
                }
            }
        }

        level.setBlockAndUpdate(center, ModBlocks.HEART_CORE.get().defaultBlockState());
        if (level.getBlockEntity(center) instanceof HeartCoreBlockEntity heart)
        {
            heart.setMana(HeartstoneItem.MAX_MANA);
            heart.setCenterColor(RuneColor.BLUE);
        }
        if (level.getBlockState(center.above()).isAir())
        {
            level.setBlockAndUpdate(center.above(), ModBlocks.HEART_CORE_TOP.get().defaultBlockState());
        }
        FairyPortalManager.fillWaterPit(level, center);

        saved.crossingPlaced = true;
        saved.setDirty();
        LogUtils.getLogger().info("Laid the waiting Crossing in the portal ruins at {} - a full heart, and no redstone.", center);
    }

    /** A small raised dais at the chamber's dead center - {@link #portalCenter()} sits exactly one block above its top, so the portal itself reads as standing on a pedestal rather than flush with the surrounding floor. */
    private static void buildPedestal(ServerLevel level, int cx, int cz, int floorY)
    {
        for (int dx = -PEDESTAL_RADIUS; dx <= PEDESTAL_RADIUS; dx++)
        {
            for (int dz = -PEDESTAL_RADIUS; dz <= PEDESTAL_RADIUS; dz++)
            {
                level.setBlock(new BlockPos(cx + dx, floorY + PEDESTAL_HEIGHT, cz + dz), PEDESTAL_TOP, 2);
            }
        }
    }

    // The full excavated footprint's own half-size - 5 wide, 5 deep - not the open tube's half
    // size. An earlier version conflated the two and only ever left a 1x1 hole open (the "3x3"
    // it was going for actually described this outer footprint, not the interior). See
    // #SKYLIGHT_TUBE_HALF_SIZE for the actual open interior.
    private static final int SKYLIGHT_HALF_SIZE = 2;
    // The genuinely open interior - a real 3x3 tube, not the 1x1 an earlier version left. Only
    // the one-block-thick ring at SKYLIGHT_HALF_SIZE itself (the outermost ring of the 5x5) is
    // actual WALL_PALETTE wall; everything inside that ring, out to this half-size, is open air.
    private static final int SKYLIGHT_TUBE_HALF_SIZE = 1;

    /**
     * A real 3x3 open tube (not 1x1) from the chamber ceiling (centered above the pedestal)
     * straight up through the hill, lined by a one-block-thick wall of {@link #WALL_PALETTE} -
     * a 5x5 footprint total - genuinely open to the sky at the top: no glass, and no leftover
     * dirt/grass cap from {@link #buildHill} left sealing it off (an earlier version stopped one
     * block short of the hill's own local surface, silently leaving that natural cap in place and
     * blocking all light). The wall ring now runs all the way up to - and including - that surface
     * level, so the shaft reads as a real carved-out passage flush with the hillside rather than
     * fading into ordinary terrain a block early.
     *
     * <p>The exception is the very top row: instead of {@link #WALL_PALETTE}, the wall ring there
     * is built from {@link #PEDESTAL_TOP} (the same chiseled stone brick the portal's own dais
     * uses - "the edge of the portal basin") with a Sea Lantern at each of the 4 true corners, the
     * same rim language {@code WorldTree#carveWell} already uses for the Wellspring - a small
     * stone lip framing the opening right at ground level. The 3x3 interior of that row is left
     * open air, so the shaft is still genuinely open straight through.
     *
     * <p>Each of the 25 columns queries its own local hill height rather than assuming they all
     * match, since the hill's own slope can differ slightly across a 5-block span.
     */
    private static void carveSkylight(ServerLevel level, Random random, int cx, int cz, int floorY)
    {
        int ceilingY = floorY + CHAMBER_INTERIOR_HEIGHT;
        BlockState cornerLight = Blocks.SEA_LANTERN.defaultBlockState();
        for (int dx = -SKYLIGHT_HALF_SIZE; dx <= SKYLIGHT_HALF_SIZE; dx++)
        {
            for (int dz = -SKYLIGHT_HALF_SIZE; dz <= SKYLIGHT_HALF_SIZE; dz++)
            {
                int x = cx + dx;
                int z = cz + dz;
                boolean isTube = Math.abs(dx) <= SKYLIGHT_TUBE_HALF_SIZE && Math.abs(dz) <= SKYLIGHT_TUBE_HALF_SIZE;
                boolean isWall = !isTube;
                boolean isCorner = Math.abs(dx) == SKYLIGHT_HALF_SIZE && Math.abs(dz) == SKYLIGHT_HALF_SIZE;
                int hillTopHere = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;

                for (int y = ceilingY; y < hillTopHere; y++)
                {
                    BlockState state = isWall ? randomFrom(random, WALL_PALETTE) : Blocks.CAVE_AIR.defaultBlockState();
                    level.setBlock(new BlockPos(x, y, z), state, 2);
                }

                BlockState topState = !isWall ? Blocks.CAVE_AIR.defaultBlockState()
                        : isCorner ? cornerLight
                        : PEDESTAL_TOP;
                level.setBlock(new BlockPos(x, hillTopHere, z), topState, 2);
            }
        }
    }

    /** 4 hanging lanterns, one near each interior corner, each with solid ceiling directly above so they're genuinely (not just visually) supported. */
    private static void placeLanterns(ServerLevel level, int cx, int cz, int floorY)
    {
        int ceilingY = floorY + CHAMBER_INTERIOR_HEIGHT + 1;
        int lanternY = ceilingY - 1;
        int inset = CHAMBER_HALF_WIDTH - 1;
        BlockState hangingLantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);

        int[][] corners = {
                {cx - inset, cz - inset}, {cx + inset, cz - inset},
                {cx - inset, cz + inset}, {cx + inset, cz + inset},
        };
        for (int[] corner : corners)
        {
            level.setBlock(new BlockPos(corner[0], ceilingY, corner[1]), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(corner[0], lanternY, corner[1]), hangingLantern, 2);
        }
    }

    /**
     * A real, immediately walkable staircase from the chamber's near wall (facing back toward
     * the tree, in the -Z direction) up through the hill to a proper cave-mouth opening -
     * replacing an earlier version's rubble-filled shaft entirely, not just patching it. One
     * step of real stairs per block of rise, 3 wide, with headroom carved above each step;
     * everything around the carved passage is left as the hill's own solid mass from {@link
     * #buildHill}, which is what actually forms the tunnel's walls and ceiling - no separate
     * wall-building needed here. Returns the Z coordinate of the cave mouth's own opening, for
     * {@link #paveEntranceApron}/{@link #pavePathToTree} to build outward from.
     */
    private static int carveStaircase(ServerLevel level, Random random, int cx, int cz, int floorY)
    {
        int nearWallZ = cz - CHAMBER_HALF_DEPTH;
        int groundY = WorldTree.groundY();
        int rise = Math.max(1, (groundY - 1) - floorY);

        int z = nearWallZ;
        int y = floorY;

        for (int step = 0; step < rise + 1; step++)
        {
            if (step == 0) {
                for (int dx = -1; dx <= 1; dx++)
                {
                    int x = cx + dx;
                    for (int dy = 0; dy <= 3; dy++) {
                        level.setBlock(new BlockPos(x, y + dy, z), randomFrom(random, CAVE_IN_PALETTE), 2);
                    }
                }
            };
            z--;
            y++;
            for (int dx = -1; dx <= 1; dx++)
            {
                int x = cx + dx;
                if(dx == -1) {
                    level.setBlock(new BlockPos(x-1, y, z), randomFrom(random, WALL_PALETTE), 2);
                    level.setBlock(new BlockPos(x-1, y + 1, z), randomFrom(random, WALL_PALETTE), 2);
                    level.setBlock(new BlockPos(x-1, y + 2, z), randomFrom(random, WALL_PALETTE), 2);
                    level.setBlock(new BlockPos(x-1, y + 3, z), randomFrom(random, WALL_PALETTE), 2);
                } else if (dx == 1) {
                    level.setBlock(new BlockPos(x+1, y, z), randomFrom(random, WALL_PALETTE), 2);
                    level.setBlock(new BlockPos(x+1, y + 1, z), randomFrom(random, WALL_PALETTE), 2);
                    level.setBlock(new BlockPos(x+1, y + 2, z), randomFrom(random, WALL_PALETTE), 2);
                    level.setBlock(new BlockPos(x+1, y + 3, z), randomFrom(random, WALL_PALETTE), 2);
                }
                BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH)
                        .setValue(StairBlock.HALF, Half.BOTTOM);
                level.setBlock(new BlockPos(x, y, z), stair, 2);
                level.setBlock(new BlockPos(x, y - 1, z), randomFrom(random, FLOOR_PALETTE), 2);
                level.setBlock(new BlockPos(x, y + 1, z), Blocks.CAVE_AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y + 2, z), Blocks.CAVE_AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y + 3, z), Blocks.CAVE_AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y + 4, z), randomFrom(random, WALL_PALETTE), 2);
            }
        }

        // The cave mouth itself: a proper archway one more block out, breaching fully into open
        // exterior air rather than leaving anything capping it off.
        int mouthZ = z - 1;
        for (int dx = -1; dx <= 1; dx++)
        {
            int x = cx + dx;
            for (int dy = 0; dy <= 2; dy++)
            {
                level.setBlock(new BlockPos(x, y + dy, mouthZ), Blocks.CAVE_AIR.defaultBlockState(), 2);
            }
        }

        // The actual floor right after the last stair step - an earlier version carved the mouth's
        // open air (above) but never placed anything at all one block below it, leaving whatever
        // the hill's own raw terrain (from buildHill, which has no idea a mouth gets carved through
        // it later) happened to be there - usually nothing solid, since the hillside is already
        // sloping away at this point, so walking out here just dropped through open space with no
        // way to actually exit. WALL_PALETTE (not FLOOR_PALETTE) per an explicit request for more
        // of it right here, in front of the last set of stairs, on the floor.
        for (int dx = -1; dx <= 1; dx++)
        {
            level.setBlock(new BlockPos(cx + dx, y, mouthZ), randomFrom(random, WALL_PALETTE), 2);
        }
        return mouthZ;
    }

    /** A small paved apron right outside the cave mouth - where the staircase's own paving meets the hillside, before the longer, sparser path to the tree takes over. */
    private static void paveEntranceApron(ServerLevel level, Random random, int cx, int caveMouthZ)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = 0; dz <= 2; dz++)
            {
                int x = cx + dx;
                int z = caveMouthZ - dz;
                clearTreeBlocks(level, x, z);
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                level.setBlock(new BlockPos(x, surfaceY, z), randomFrom(random, PATH_PALETTE), 2);
            }
        }
    }

    /**
     * An intermittent paved trail (small paving patches every {@value #PATH_STEP_INTERVAL}
     * blocks, not a solid road) along the straight line from the cave mouth to the tree's own
     * entrance - see {@link #TREE_ENTRANCE_X_OFFSET}/{@link #TREE_ENTRANCE_Z_OFFSET} for why
     * that endpoint is stored relative to the tree's own center rather than as a fixed world
     * position. Each surface height is looked up live rather than assumed, since the ground
     * between the ruins and the tree isn't perfectly flat once real terrain is involved.
     */
    private static void pavePathToTree(ServerLevel level, Random random, int startX, int startZ, int treeEntranceX, int treeEntranceZ)
    {
        double dx = treeEntranceX - startX;
        double dz = treeEntranceZ - startZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0)
        {
            return;
        }
        int steps = (int) Math.round(distance);
        for (int i = 0; i <= steps; i += PATH_STEP_INTERVAL)
        {
            double t = i / distance;
            int x = startX + (int) Math.round(dx * t);
            int z = startZ + (int) Math.round(dz * t);
            clearTreeBlocks(level, x, z);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            level.setBlock(new BlockPos(x, surfaceY, z), randomFrom(random, PATH_PALETTE), 2);
            if (random.nextBoolean())
            {
                int x2 = x + (random.nextBoolean() ? 1 : -1);
                clearTreeBlocks(level, x2, z);
                int surfaceY2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x2, z) - 1;
                level.setBlock(new BlockPos(x2, surfaceY2, z), randomFrom(random, PATH_PALETTE), 2);
            }
        }
    }

    private static BlockState randomFrom(Random random, BlockState[] palette)
    {
        return palette[random.nextInt(palette.length)];
    }

    /** Just the one boolean this needs to persist: has the ruin already been placed on this Fairy Realm level. */
    public static final class RuinsSavedData extends SavedData
    {
        private boolean placed;
        /** Whether the waiting Crossing has been laid on the pedestal - see {@link #placeCrossingIfNeeded}. */
        private boolean crossingPlaced;

        public RuinsSavedData()
        {
        }

        public static RuinsSavedData load(CompoundTag tag)
        {
            RuinsSavedData data = new RuinsSavedData();
            data.placed = tag.getBoolean("Placed");
            data.crossingPlaced = tag.getBoolean("CrossingPlaced");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("Placed", placed);
            tag.putBoolean("CrossingPlaced", crossingPlaced);
            return tag;
        }
    }
}
