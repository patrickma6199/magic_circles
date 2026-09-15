package com.patrickma.magiccircles.worldgen;

import com.patrickma.magiccircles.entity.ManaWyrmEntity;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A real, vast ocean of Wellspring Water, hollowed out of the stone the island's underside tapers
 * down into - reached by diving down through {@link WorldTree}'s own well shaft. Runs explicitly
 * after {@link WorldTree#placeIfNeeded} (called directly from there, same reasoning {@code
 * FairyPortalRuins} already uses for its own ordering - Forge doesn't guarantee anything about the
 * order two independent {@code ServerStartedEvent} handlers run in relative to each other, and
 * this needs both the well and {@link FairyRealmChunkGenerator}'s own terrain to already exist).
 *
 * <p>An earlier version carved a fixed-radius sphere (32 blocks) regardless of where the island's
 * own rock actually was - correctly described as "a small hole," not an ocean. This version
 * instead traces {@link FairyRealmChunkGenerator#undersideBottomAt}'s own taper directly for its
 * floor, at every column out to {@link #MAX_HORIZONTAL_RADIUS} - the exact same curve that
 * generator uses to decide where the island's underside actually is, plus a safety buffer, so the
 * cavity is guaranteed to fit inside the real available rock rather than risk breaching either the
 * walkable surface above or the true bottom below. Because that taper is deepest at the center and
 * shallowest toward the edge, the result reads as a proper basin - vast and open near the middle,
 * tapering to a shallow fringe - rather than a uniform box. Two independent 2D noise fields
 * perturb the horizontal edge and the floor height per column so the outline and floor texture
 * both read as organic rather than a mathematically perfect shape.
 *
 * <p>If {@link WorldTree}'s river/stream ever happens to dip low enough to break through into this
 * cavity somewhere along its course, that's left alone entirely - nothing here checks for or
 * "patches" an overlap; the ocean is carved once, unconditionally, and stays that way.
 *
 * <p>Sea Lanterns are scattered through the surrounding stone as small clustered veins - the same
 * "found in pockets as you dig" read a real ore gives, at a comparable rough density, placed
 * directly via a random-walk blob rather than through the real {@code ConfiguredFeature}/{@code
 * PlacedFeature} system (which only ever runs during normal per-chunk decoration, not from a
 * one-time world edit like this one).
 */
public final class WellspringOcean
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // How far out from the well's own column the ocean is allowed to spread - comfortably inside
    // FairyRealmChunkGenerator's own mandatory-flat disc (FLAT_RADIUS, 85) so this never risks
    // undermining anything past it (where real mountain terrain, with its own very different
    // underside depth, takes over).
    private static final int MAX_HORIZONTAL_RADIUS = 75;
    // Solid rock kept between the cavity's own flat ceiling and the walkable surface above it -
    // the ceiling itself sits at a fixed depth below ground (FairyRealmChunkGenerator's flat
    // center has no terrain variation to account for within this radius), not a taper.
    private static final int TOP_BUFFER = 14;
    // Solid rock kept between the cavity's own floor and the island's real tapered underside -
    // see the class doc comment; this is what actually keeps the ocean inside real available rock.
    private static final int BOTTOM_BUFFER = 10;
    private static final int MIN_CAVITY_HEIGHT = 6;

    private static final int SHAFT_RADIUS = 2;

    private static final double EDGE_NOISE_AMPLITUDE = 14.0;
    private static final double EDGE_NOISE_SCALE = 1.0 / 50.0;
    private static final double FLOOR_NOISE_AMPLITUDE = 5.0;
    private static final double FLOOR_NOISE_SCALE = 1.0 / 24.0;
    // How wide a band, just inside the noise-perturbed edge, the floor spends rising to meet the
    // ceiling - an earlier version cut the cavity off with a hard cylindrical wall right at the
    // edge (floor and ceiling both just stopping dead), which read as an obviously artificial cut
    // rather than a cave naturally pinching out. Tapering the floor up over this band instead
    // means the water gets shallower and shallower before the rock finally closes in, the way a
    // real cave/basin edge actually looks.
    private static final double EDGE_TAPER_WIDTH = 18.0;
    // The ceiling's own noise - previously perfectly flat everywhere, which is what made "the top
    // of the ocean" read as an obviously artificial flat cut too. Deliberately negated ("reversed"
    // Perlin/simplex, per what was asked for) so it only ever dips further down into the rock
    // (stalactite-like), never rises - rising risks breaching TOP_BUFFER's own safety margin to
    // the walkable surface above, which this avoids entirely by construction.
    private static final double CEILING_NOISE_AMPLITUDE = 6.0;
    private static final double CEILING_NOISE_SCALE = 1.0 / 22.0;
    // Directly above/around the well's own shaft, the ceiling stays perfectly flat (no noise) -
    // the same "flatten near the landmark" treatment FairyRealmChunkGenerator gives the World
    // Tree's own flat disc - so the shaft itself always breaks cleanly into open water instead of
    // risking a jagged, noise-dipped ceiling right where the player actually swims down through.
    private static final double CEILING_FLATTEN_RADIUS = 10.0;
    private static final double CEILING_FLATTEN_BLEND_WIDTH = 16.0;

    // Glitter Weed - see block/GlitterWeedBlock's own doc comment. Grown up from a dirt seabed
    // (replacing whatever stone/sand the taper's own floor would otherwise expose) at this rough
    // density, each patch a random height, capped with a Glitter Weed Sac once it stops growing.
    // Really tall per an explicit request (30-40, was 2-6) - GLITTER_WEED_MAX_HEIGHT only rarely
    // binds in practice, since GLITTER_WEED_CEILING_CLEARANCE_MIN/MAX (also per that same request -
    // a random clearance per patch, not one fixed value) usually caps a patch first: every patch's
    // own top stays at least that far below "the top of the ocean" (this column's own noise-varied
    // ceiling - see #cavityColumnAt), regardless of how deep the water beneath it happens to be.
    //
    // GLITTER_WEED_ABSOLUTE_MIN is what actually lets the forest reach the ocean's own shallower
    // edge (per a follow-up request - "grows closer to the edge too"): a patch whose available
    // headroom can't fit the full 30-40 range no longer gets skipped outright, it just grows as
    // tall as it actually can (down to this floor) instead - full-height giants near the deep
    // center, tapering to short shoots right at the shoreline, rather than a hard cutoff between
    // "forest" and "nothing."
    private static final double GLITTER_WEED_CHANCE = 0.05;
    private static final int GLITTER_WEED_ABSOLUTE_MIN = 4;
    private static final int GLITTER_WEED_MIN_HEIGHT = 30;
    private static final int GLITTER_WEED_MAX_HEIGHT = 40;
    private static final int GLITTER_WEED_CEILING_CLEARANCE_MIN = 27;
    private static final int GLITTER_WEED_CEILING_CLEARANCE_MAX = 38;

    // Sea Lantern veins - a random-walk blob seeded at roughly this density through the cavity's
    // full bounding volume (most seed attempts land in open water or well outside any solid rock
    // and are simply skipped, the same way a real ore's placement attempts miss just as often).
    private static final int VEIN_VOLUME_PER_ATTEMPT = 4500;
    private static final int VEIN_MIN_SIZE = 3;
    private static final int VEIN_MAX_SIZE = 7;
    private static final int VEIN_VERTICAL_SPAN = 110;

    private static final int MANA_WYRM_COUNT = 60;
    private static final int MANA_WYRM_PLACEMENT_ATTEMPTS = 800;

    // A handful, not a population like the wyrms - Dream Elk are meant to be a rare, memorable
    // sight even underwater, mostly living on dry land via natural surface spawning instead.
    private static final int DREAM_ELK_COUNT = 6;
    private static final int DREAM_ELK_PLACEMENT_ATTEMPTS = 200;

    private static final long SEED = 20260907L * 3L;

    private WellspringOcean()
    {
    }

    /** Called explicitly from {@link WorldTree#placeIfNeeded} - see this class's own doc comment on why. */
    /**
     * Whether a spot is down in the Wellspring's sea - under the island and within the cavity's
     * widest reach. Loose on purpose, since the cavity's exact edge is noise nothing else
     * recomputes: anything this deep and this close under the well is the sea, or the rock round it.
     */
    public static boolean inSea(BlockPos pos)
    {
        BlockPos well = WorldTree.wellCenter();
        return Math.hypot(pos.getX() - well.getX(), pos.getZ() - well.getZ()) <= seaReach() && pos.getY() <= seaCeilingY();
    }

    /** How far out from under the well the sea can reach, its noisy edge included. */
    public static double seaReach()
    {
        return MAX_HORIZONTAL_RADIUS + EDGE_NOISE_AMPLITUDE;
    }

    /** The highest the sea's ceiling ever rises. */
    public static int seaCeilingY()
    {
        return FairyRealmChunkGenerator.groundY() - 1 - TOP_BUFFER + (int) Math.ceil(CEILING_NOISE_AMPLITUDE);
    }

    public static synchronized void carveIfNeeded(ServerLevel fairyRealm)
    {
        OceanSavedData saved = fairyRealm.getDataStorage().computeIfAbsent(OceanSavedData::load, OceanSavedData::new, "magiccircles_wellspring_ocean");
        if (saved.placed)
        {
            return;
        }

        LOGGER.info("Carving the Wellspring's own vast ocean beneath the World Tree - this happens once, ever.");

        BlockPos wellCenter = WorldTree.wellCenter();
        int centerX = wellCenter.getX();
        int centerZ = wellCenter.getZ();

        int loadRadius = MAX_HORIZONTAL_RADIUS + 2;
        Set<Long> touchedChunks = new HashSet<>();
        for (int cx = (centerX - loadRadius) >> 4; cx <= (centerX + loadRadius) >> 4; cx++)
        {
            for (int cz = (centerZ - loadRadius) >> 4; cz <= (centerZ + loadRadius) >> 4; cz++)
            {
                long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                if (touchedChunks.add(key))
                {
                    fairyRealm.getChunk(cx, cz);
                }
            }
        }

        PerlinSimplexNoise edgeNoise = new PerlinSimplexNoise(RandomSource.create(SEED), List.of(1, 2));
        PerlinSimplexNoise floorNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 1), List.of(1, 2));
        PerlinSimplexNoise ceilingNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 3), List.of(1, 2));
        Random random = new Random(SEED + 2);

        BlockState wellspringWater = ModBlocks.WELLSPRING_WATER.get().defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState glitterWeed = ModBlocks.GLITTER_WEED.get().defaultBlockState();
        BlockState glitterWeedPlant = ModBlocks.GLITTER_WEED_PLANT.get().defaultBlockState();
        BlockState glitterWeedSac = ModBlocks.GLITTER_WEED_SAC.get().defaultBlockState();
        int baseCeilingY = FairyRealmChunkGenerator.groundY() - 1 - TOP_BUFFER;

        carveShaft(fairyRealm, wellCenter, baseCeilingY, wellspringWater);

        int columnsCarved = 0;
        int weedPatches = 0;
        for (int x = centerX - MAX_HORIZONTAL_RADIUS; x <= centerX + MAX_HORIZONTAL_RADIUS; x++)
        {
            for (int z = centerZ - MAX_HORIZONTAL_RADIUS; z <= centerZ + MAX_HORIZONTAL_RADIUS; z++)
            {
                CavityColumn column = cavityColumnAt(x, z, centerX, centerZ, baseCeilingY, edgeNoise, floorNoise, ceilingNoise);
                if (column == null)
                {
                    continue;
                }
                for (int y = column.floorY; y <= column.ceilingY; y++)
                {
                    fairyRealm.setBlock(new BlockPos(x, y, z), wellspringWater, 2);
                }
                fairyRealm.setBlock(new BlockPos(x, column.floorY - 1, z), dirt, 2);
                columnsCarved++;

                int headroom = column.ceilingY - column.floorY - 2;
                int clearance = GLITTER_WEED_CEILING_CLEARANCE_MIN
                        + random.nextInt(GLITTER_WEED_CEILING_CLEARANCE_MAX - GLITTER_WEED_CEILING_CLEARANCE_MIN + 1);
                int ceilingCap = (column.ceilingY - clearance) - column.floorY;
                int allowedHeight = Math.min(headroom, ceilingCap);
                if (allowedHeight >= GLITTER_WEED_ABSOLUTE_MIN && random.nextDouble() < GLITTER_WEED_CHANCE)
                {
                    int maxHeight = Math.min(GLITTER_WEED_MAX_HEIGHT, allowedHeight);
                    int minHeight = Math.min(GLITTER_WEED_MIN_HEIGHT, maxHeight);
                    int height = minHeight + random.nextInt(maxHeight - minHeight + 1);
                    for (int i = 0; i < height; i++)
                    {
                        BlockState stalk = i == height - 1 ? glitterWeed : glitterWeedPlant;
                        fairyRealm.setBlock(new BlockPos(x, column.floorY + i, z), stalk, 2);
                    }
                    fairyRealm.setBlock(new BlockPos(x, column.floorY + height, z), glitterWeedSac, 2);
                    weedPatches++;
                }
            }
        }

        long totalVolume = (long) columnsCarved * (baseCeilingY - (FairyRealmChunkGenerator.undersideBottomAt(centerX, centerZ) + BOTTOM_BUFFER));
        int veinAttempts = (int) Math.max(40, totalVolume / VEIN_VOLUME_PER_ATTEMPT);
        int veinsPlaced = scatterSeaLanternVeins(fairyRealm, random, centerX, centerZ, baseCeilingY, veinAttempts);

        int wyrmsSpawned = spawnManaWyrms(fairyRealm, random, centerX, centerZ, baseCeilingY, edgeNoise, floorNoise, ceilingNoise);
        int elksSpawned = spawnDreamElks(fairyRealm, random, centerX, centerZ, baseCeilingY, edgeNoise, floorNoise, ceilingNoise);

        saved.placed = true;
        saved.setDirty();
        LOGGER.info("Wellspring ocean carving complete ({} columns, {} sea lantern veins, {} glitter weed patches, {} mana wyrms, {} dream elks).", columnsCarved, veinsPlaced, weedPatches, wyrmsSpawned, elksSpawned);
    }

    /** The connecting shaft: straight down from the well's own water surface to the ocean's own (flat) ceiling at the very center. */
    private static void carveShaft(ServerLevel level, BlockPos wellCenter, int ceilingY, BlockState wellspringWater)
    {
        for (int y = ceilingY; y <= wellCenter.getY(); y++)
        {
            for (int dx = -SHAFT_RADIUS; dx <= SHAFT_RADIUS; dx++)
            {
                for (int dz = -SHAFT_RADIUS; dz <= SHAFT_RADIUS; dz++)
                {
                    if (dx * dx + dz * dz > SHAFT_RADIUS * SHAFT_RADIUS)
                    {
                        continue;
                    }
                    level.setBlock(new BlockPos(wellCenter.getX() + dx, y, wellCenter.getZ() + dz), wellspringWater, 2);
                }
            }
        }
    }

    private static double smoothstep(double edge0, double edge1, double x)
    {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * This column's own cavity extent, or {@code null} if there's no cavity here at all (past the
     * (noise-perturbed) horizontal edge, or too shallow to bother with). {@link
     * FairyRealmChunkGenerator#undersideBottomAt} is queried directly for the floor's own base
     * depth - the real source of "fits within the available rock," not a shape invented
     * independently of it - though the actual floor returned here is raised above that near the
     * edge (see {@link #EDGE_TAPER_WIDTH}) and the ceiling dips below {@code baseCeilingY} away
     * from the well shaft (see {@link #CEILING_NOISE_AMPLITUDE}/{@link #CEILING_FLATTEN_RADIUS}).
     */
    private static CavityColumn cavityColumnAt(int x, int z, int centerX, int centerZ, int baseCeilingY, PerlinSimplexNoise edgeNoise, PerlinSimplexNoise floorNoise, PerlinSimplexNoise ceilingNoise)
    {
        double dx = x - centerX;
        double dz = z - centerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double edgeBump = edgeNoise.getValue(x * EDGE_NOISE_SCALE, z * EDGE_NOISE_SCALE, false) * EDGE_NOISE_AMPLITUDE;
        double edge = MAX_HORIZONTAL_RADIUS + edgeBump;
        if (dist > edge)
        {
            return null;
        }

        double ceilingBlend = smoothstep(CEILING_FLATTEN_RADIUS, CEILING_FLATTEN_RADIUS + CEILING_FLATTEN_BLEND_WIDTH, dist);
        double ceilingDip = -Math.abs(ceilingNoise.getValue(x * CEILING_NOISE_SCALE, z * CEILING_NOISE_SCALE, false));
        int ceilingY = baseCeilingY + (int) Math.round(ceilingDip * CEILING_NOISE_AMPLITUDE * ceilingBlend);

        double floorBump = floorNoise.getValue(x * FLOOR_NOISE_SCALE, z * FLOOR_NOISE_SCALE, false) * FLOOR_NOISE_AMPLITUDE;
        int baseFloorY = FairyRealmChunkGenerator.undersideBottomAt(x, z) + BOTTOM_BUFFER + (int) Math.round(floorBump);

        double taperT = smoothstep(edge - EDGE_TAPER_WIDTH, edge, dist);
        int floorY = baseFloorY + (int) Math.round(taperT * (ceilingY - baseFloorY));

        if (ceilingY - floorY < MIN_CAVITY_HEIGHT)
        {
            return null;
        }
        return new CavityColumn(floorY, ceilingY);
    }

    private static int scatterSeaLanternVeins(ServerLevel level, Random random, int centerX, int centerZ, int ceilingY, int attempts)
    {
        BlockState seaLantern = Blocks.SEA_LANTERN.defaultBlockState();
        int span = MAX_HORIZONTAL_RADIUS * 2 + 1;
        int placed = 0;
        for (int i = 0; i < attempts; i++)
        {
            int x = centerX - MAX_HORIZONTAL_RADIUS + random.nextInt(span);
            int z = centerZ - MAX_HORIZONTAL_RADIUS + random.nextInt(span);
            int y = ceilingY - random.nextInt(VEIN_VERTICAL_SPAN);
            BlockPos seed = new BlockPos(x, y, z);
            if (!level.getBlockState(seed).is(Blocks.STONE))
            {
                continue;
            }
            placeVeinBlob(level, random, seed, seaLantern);
            placed++;
        }
        return placed;
    }

    /** A small random-walk blob, only ever converting actual stone it wanders into - open water or air is simply skipped, so a vein seeded right at the cavity's own wall never leaks blocks into the open water. */
    private static void placeVeinBlob(ServerLevel level, Random random, BlockPos seed, BlockState oreState)
    {
        int size = VEIN_MIN_SIZE + random.nextInt(VEIN_MAX_SIZE - VEIN_MIN_SIZE + 1);
        BlockPos.MutableBlockPos cursor = seed.mutable();
        for (int i = 0; i < size; i++)
        {
            if (level.getBlockState(cursor).is(Blocks.STONE))
            {
                level.setBlock(cursor, oreState, 2);
            }
            cursor.move(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        }
    }

    private static int spawnManaWyrms(ServerLevel level, Random random, int centerX, int centerZ, int ceilingY, PerlinSimplexNoise edgeNoise, PerlinSimplexNoise floorNoise, PerlinSimplexNoise ceilingNoise)
    {
        int spawned = 0;
        for (int attempt = 0; attempt < MANA_WYRM_PLACEMENT_ATTEMPTS && spawned < MANA_WYRM_COUNT; attempt++)
        {
            int x = centerX + random.nextInt(2 * MAX_HORIZONTAL_RADIUS + 1) - MAX_HORIZONTAL_RADIUS;
            int z = centerZ + random.nextInt(2 * MAX_HORIZONTAL_RADIUS + 1) - MAX_HORIZONTAL_RADIUS;
            CavityColumn column = cavityColumnAt(x, z, centerX, centerZ, ceilingY, edgeNoise, floorNoise, ceilingNoise);
            if (column == null)
            {
                continue;
            }
            int y = column.floorY + random.nextInt(Math.max(1, column.ceilingY - column.floorY));
            ManaWyrmEntity wyrm = new ManaWyrmEntity(ModEntities.MANA_WYRM.get(), level);
            wyrm.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0f, 0.0f);
            wyrm.finalizeSpawn(level, level.getCurrentDifficultyAt(wyrm.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(wyrm);
            spawned++;
        }
        return spawned;
    }

    /**
     * A small guaranteed batch of Dream Elk (see {@code entity/DreamElkEntity}) placed directly
     * in the open water, near the seabed - "this includes starting in wellspring water," per an
     * explicit request, is otherwise unreachable through ordinary heightmap-based natural
     * spawning (see {@code MagicCircles#commonSetup}'s own doc comment on why a closed
     * underground cavity like this one is invisible to any heightmap column). They're expected to
     * jump their own way out through the well shaft or up to the cavity's own ceiling given their
     * huge leap - not babysat out of the water here.
     */
    private static int spawnDreamElks(ServerLevel level, Random random, int centerX, int centerZ, int ceilingY, PerlinSimplexNoise edgeNoise, PerlinSimplexNoise floorNoise, PerlinSimplexNoise ceilingNoise)
    {
        int spawned = 0;
        for (int attempt = 0; attempt < DREAM_ELK_PLACEMENT_ATTEMPTS && spawned < DREAM_ELK_COUNT; attempt++)
        {
            int x = centerX + random.nextInt(2 * MAX_HORIZONTAL_RADIUS + 1) - MAX_HORIZONTAL_RADIUS;
            int z = centerZ + random.nextInt(2 * MAX_HORIZONTAL_RADIUS + 1) - MAX_HORIZONTAL_RADIUS;
            CavityColumn column = cavityColumnAt(x, z, centerX, centerZ, ceilingY, edgeNoise, floorNoise, ceilingNoise);
            if (column == null)
            {
                continue;
            }
            int y = column.floorY + 1;
            com.patrickma.magiccircles.entity.DreamElkEntity elk =
                    new com.patrickma.magiccircles.entity.DreamElkEntity(com.patrickma.magiccircles.registry.ModEntities.DREAM_ELK.get(), level);
            elk.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0f, 0.0f);
            elk.finalizeSpawn(level, level.getCurrentDifficultyAt(elk.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(elk);
            spawned++;
        }
        return spawned;
    }

    private static final class CavityColumn
    {
        final int floorY;
        final int ceilingY;

        CavityColumn(int floorY, int ceilingY)
        {
            this.floorY = floorY;
            this.ceilingY = ceilingY;
        }
    }

    public static final class OceanSavedData extends SavedData
    {
        public boolean placed = false;

        public static OceanSavedData load(CompoundTag tag)
        {
            OceanSavedData data = new OceanSavedData();
            data.placed = tag.getBoolean("placed");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("placed", placed);
            return tag;
        }
    }
}
