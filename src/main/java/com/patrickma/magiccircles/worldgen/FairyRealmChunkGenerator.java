package com.patrickma.magiccircles.worldgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.patrickma.magiccircles.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * The island's terrain: a flat clearing at the center (where {@link WorldTree} and its well
 * stand) melding gradually outward into real Perlin-noise mountains, everywhere except a wide
 * corridor facing +Z (the tree's own entrance direction, matching where {@link FairyPortalRuins}
 * sits) which stays open and flat all the way out, a meandering river tracing the outer edge
 * through the hilly two-thirds of the island, and an underside that tapers to a rough point below
 * the center instead of cutting off flat.
 *
 * <p><b>The mountain/underside noise uses a real random seed, chosen once and recorded, not a
 * fixed constant.</b> {@link #ensureTerrainReady} - called by {@link WorldTree}/{@link
 * FairyPortalRuins} before either force-loads any chunk of their own, which is what guarantees
 * this runs before any real terrain generation happens - either loads a seed already recorded
 * for this world ({@link TerrainSeedSavedData}) or, the first time only, tries random seeds
 * ({@link #searchForMountainSeed}) until one actually produces a column tall enough to read as a
 * real mountain ({@value #MOUNTAIN_HEIGHT_THRESHOLD} blocks above the flat base), then records
 * that seed so every later boot of this same world reuses the exact same terrain rather than
 * rerolling it. This is a deliberate change from an earlier, fully fixed-constant version of
 * this generator (no seed at all, so every install matched byte-for-byte) - once the island's
 * actual shape is finalized, the plan is still to bake one specific generated result into a
 * shipped map for every install (see the class's own closing paragraph), and a recorded seed is
 * just as reproducible for that purpose as a hand-picked constant, while actually looking like
 * real terrain in the meantime.
 *
 * <p>{@link #rawColumnAt} is the one place the actual shape math lives - flat/mountain/entrance/
 * river/underside, all of it - and {@link #columnShapeAt} (what every other method in this class
 * calls through) adds exactly one more thing on top: for any column that isn't itself a river,
 * checking its 8 neighbors and raising its own height to match if any neighbor turns out to be a
 * river with a higher water surface, so the water is always contained by ground at or above its
 * own level rather than occasionally poking out over lower neighboring terrain (a real artifact
 * an earlier version had, from computing each column's height completely independently).
 *
 * <p><b>Everything procedural here is meant to be temporary</b>, in the sense that it only needs
 * to exist until this island's shape is actually finalized - at that point the plan is to
 * generate it once, save the result, and ship the baked world data directly (the same "capture
 * it once, place it as fixed data forever" approach {@link WorldTree}'s own structure resource
 * already uses, just at the scale of an entire dimension instead of one building) rather than
 * running this generator on every install forever. Keeping every procedural piece of the Fairy
 * Realm's generation in this one file (plus the biome json referenced below) - as opposed to
 * spreading terrain logic across several classes - is what makes that eventual swap a matter of
 * deleting this file and its biome/worldgen data rather than hunting for scattered pieces: the
 * one-time structure placements ({@link WorldTree}, {@link FairyPortalRuins}) are already
 * separate for exactly this reason, and would very likely stay even after this file goes, since
 * their own output could just as easily be re-baked into the same shipped map.
 *
 * <p><b>{@link #ensureTerrainReady} must run before this dimension generates its very first
 * chunk, not just before {@link WorldTree}/{@link FairyPortalRuins} force-load their own
 * footprint.</b> An earlier version only called it from those two classes' own {@code
 * ServerStartedEvent} handlers - which is too late: {@code MinecraftServer.prepareLevels()}
 * forces the world's actual spawn chunks to generate *during server startup*, before {@code
 * ServerStartedEvent} ever fires, so any chunk anywhere near spawn generated (and permanently
 * baked flat, since a generated chunk is never regenerated) with {@link #mountainNoise} still
 * null - {@link #mountainHeightAt(int, int)}'s null fallback returns 0, silently flattening
 * everything outside {@link #FLAT_RADIUS} to the same {@link #FLAT_TOP_Y} the actual flat center
 * uses, which is exactly the "the whole island is flat" symptom this was fixed for. {@link
 * #onLevelLoad} closes that gap by hooking {@code LevelEvent.Load} instead, which Forge fires as
 * each {@code ServerLevel} is constructed - reliably before {@code prepareLevels()} ever asks
 * that level for a chunk.
 */
@Mod.EventBusSubscriber(modid = com.patrickma.magiccircles.MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FairyRealmChunkGenerator extends ChunkGenerator
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<FairyRealmChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource))
                    .apply(instance, FairyRealmChunkGenerator::new));

    private static final int ISLAND_RADIUS = 250;

    /** The island's own radius, for anything outside this class that needs to know where the land actually ends (e.g. {@code FairyRealmWeather}'s "storms don't strike the island itself" bubble). */
    public static int islandRadius()
    {
        return ISLAND_RADIUS;
    }
    // Topmost grass block's Y at the flat center - unchanged from this generator's original
    // fixed-layer-stack version, so WorldTree/FairyPortalRuins' own already-tuned Y offsets
    // never had to move.
    private static final int FLAT_TOP_Y = 102;
    private static final int TOPSOIL_DIRT_LAYERS = 3;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    /**
     * Every body of water this generator places (the stream, the river bed's own flooding) is
     * real Wellspring Water, not vanilla water - the whole island sits on the Wellspring, so any
     * water reaching the surface should read as the same mana-charged water the well itself holds.
     * A method, not a {@code static final} field - a field initializer runs at class-load time,
     * which for this class happens during mod construction/event-bus scanning, well before {@code
     * ModBlocks}' own {@code DeferredRegister} has actually fired its registration event; calling
     * {@code .get()} that early threw "Registry Object not present" and crashed mod loading
     * outright the first time this was tried as a field.
     */
    private static BlockState water()
    {
        return ModBlocks.WELLSPRING_WATER.get().defaultBlockState();
    }
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();

    // Flat radius around the very center (see WorldTree) that always sits at exactly FLAT_TOP_Y
    // regardless of direction - {@link WorldTree#maxRadius()} is the tree's own real bounding box
    // reach, but that's an *axis-aligned* half-width (a square's, since the structure is a
    // rectangular NBT capture), not a true radius: the square's own corners actually reach
    // maxRadius() * sqrt(2), noticeably further out along the diagonals than straight along X or
    // Z. An earlier version of this constant used a flat 85 reasoned only against the axis-aligned
    // reach (~78) - comfortably past *that*, but well short of the true ~110-block corner
    // distance, which is exactly why the tree's own square platform edge was visibly poking out
    // past the flat disc into bumpy mountain terrain in the corners specifically (confirmed: this
    // got more noticeable, not less, once other terrain changes left less incidental mountain
    // camouflage right around the tree - a real bug this fixes properly rather than only masking
    // it with a bigger fixed number).
    private static final double FLAT_RADIUS_MARGIN = 10.0;
    private static int cachedFlatRadius = -1;

    /** The World Tree's own mandatory-flat disc radius - see {@code FairyLandmarkExclusionFilter}, which keeps trees from spawning within 5 blocks of this. Sized dynamically off the tree's own real structure bounds (see this field's own doc comment) rather than a fixed guess, so it always fully contains the tree's square footprint - corners included - regardless of the source structure's own actual size. */
    public static int flatRadius()
    {
        if (cachedFlatRadius < 0)
        {
            double cornerReach = WorldTree.maxRadius() * Math.sqrt(2.0);
            cachedFlatRadius = (int) Math.ceil(cornerReach + FLAT_RADIUS_MARGIN);
        }
        return cachedFlatRadius;
    }

    // How far beyond FLAT_RADIUS the flat-to-mountain gradient takes to fully ramp up to 1 - wider
    // than an earlier version's 50, so the flat center melts into the hills gradually over a much
    // longer stretch instead of climbing to full mountain height in a comparatively short, more
    // visibly abrupt band right past the flat disc's edge.
    private static final double BLEND_WIDTH = 70.0;
    // How far before ISLAND_RADIUS the mountain contribution starts tapering back down to 0,
    // so mountains always meet the flat ground level exactly at the island's true edge instead
    // of cutting off mid-slope.
    private static final double EDGE_TAPER_WIDTH = 55.0;

    // The entrance corridor: a wedge centered on +Z (see WorldTree's own doc comments on why +Z
    // is "the entrance direction") that stays flat and hill-free all the way out to the island's
    // true edge, wide enough to comfortably walk between the tree and FairyPortalRuins without
    // ever climbing a hill. ENTRANCE_ANGLE_TAPER_DEG is how many degrees past that wedge boundary
    // the mountain contribution takes to ramp from 0 back up to 1 - a smooth meld into the wedge
    // rather than a hard seam right at its edge.
    private static final double ENTRANCE_HALF_ANGLE_DEG = 35.0;
    private static final double ENTRANCE_ANGLE_TAPER_DEG = 20.0;

    private static final double MOUNTAIN_AMPLITUDE = 115.0;
    private static final double MOUNTAIN_NOISE_SCALE = 1.0 / 170.0;
    // Normalized noise (0-1) raised to this power before scaling by MOUNTAIN_AMPLITUDE - above 1,
    // this biases most of the terrain toward the lower end (rolling hills) with real peaks only
    // where the noise is already strongly positive, rather than bumps distributed evenly.
    private static final double MOUNTAIN_SHAPE_POWER = 1.7;

    // The river system: traces the RIVER_CENTER *contour line* of a dedicated noise field
    // (riverNoise) - the set of points where the noise value is close to RIVER_CENTER - rather
    // than thresholding "noise above some value," which was this system's own first version and
    // produced scattered blobs/pits (any local peak above the threshold forms its own separate
    // island of "river," with no reason for any two to connect) instead of an actual river. A
    // contour line of a smooth coherent noise field is a real, long, connected, naturally
    // meandering curve across the whole map - exactly the shape of a river.
    //
    // Two problems remained even with a contour line, both fixed here:
    //
    // 1. Width was defined in raw noise-value units (a flat +/-0.09 band), but the noise's own
    // local gradient (how fast its value changes per block) varies from place to place - the same
    // noise-unit band is a wide river where the gradient is shallow and a near-nonexistent sliver
    // where it's steep. #riverDistanceFromCenterBlocks fixes this by dividing the noise-value gap
    // by a numerically-estimated local gradient magnitude, turning "how far from center in noise
    // units" into an actual approximate *physical distance in blocks* - RIVER_HALF_WIDTH_BLOCKS
    // then means what it says, consistently, everywhere.
    //
    // 2. The carve depth used to be relative to mountainTop (mountainTop - some fraction of
    // RIVER_MAX_DEPTH), which can fail to reach WATER_LEVEL_Y at all wherever the mountain terrain
    // crossing the river happens to be tall enough - exactly the "pits disconnected by whole
    // cliffs" symptom this was built to fix. #riverCarvedTop now lerps toward RIVER_BED_TOP (an
    // *absolute* elevation, not a depth-below-terrain) as the band's own falloff t approaches 1 -
    // at the true centerline (t=1) the result is RIVER_BED_TOP exactly, regardless of how tall
    // mountainTop was, which is what actually guarantees the channel breaks all the way through
    // any mountain or hill it crosses rather than leaving a dry, disconnected gap there.
    private static final double RIVER_NOISE_SCALE = 1.0 / 50.0;
    private static final double RIVER_CENTER = 0.0;
    // Half-width of the river channel, in actual blocks (see point 1 above) - most of the river's
    // own meander is comfortably wider than this since only the closest approach to a crossing
    // mountain peak ever pinches down toward the guaranteed-connected centerline.
    private static final double RIVER_HALF_WIDTH_BLOCKS = 8.0;
    // How far apart the two extra noise samples (see #riverGradient) are, in blocks - small enough
    // to approximate the true local derivative well at RIVER_NOISE_SCALE's own wavelength.
    private static final double RIVER_GRADIENT_STEP = 1.0;
    // Fixed water surface for every river column, regardless of how tall the mountain terrain
    // around it happens to be - the same way a real sea level doesn't care how tall nearby hills
    // are. Now level with the flat ground itself (was FLAT_TOP_Y - 7, which left the Wellspring
    // sitting in a trench seven blocks down): a source block's surface sits just under the top of
    // the grass beside it, so the water meets the bank the way a real shoreline does. The river
    // widens to its banks as a result, since more of the carved channel now falls under the
    // waterline - it cannot spread past RIVER_HALF_WIDTH_BLOCKS regardless.
    private static final int WATER_LEVEL_Y = FLAT_TOP_Y;
    // The absolute elevation the *centerline* always carves down to (see point 2 above) - a real,
    // swimmable few blocks of depth below the water surface, not just barely-flooded.
    private static final int RIVER_BED_TOP = WATER_LEVEL_Y - 6;

    // The island's underside tapers to a rough point below the center rather than cutting off
    // flat like a cookie-cutter slab - deepest (lowest Y) at the very center, shallowest near the
    // true edge, with a bit of noise so the taper itself reads as a natural, uneven point rather
    // than a mathematically perfect cone.
    private static final int UNDERSIDE_CENTER_BOTTOM_Y = 1;
    private static final int UNDERSIDE_EDGE_BOTTOM_Y = 75;
    private static final double UNDERSIDE_SHARPNESS = 2.2;
    private static final double UNDERSIDE_NOISE_AMPLITUDE = 8.0;
    private static final double UNDERSIDE_NOISE_SCALE = 1.0 / 90.0;

    // "Fairly tall mountains" for the purposes of #searchForMountainSeed - blocks above
    // FLAT_TOP_Y a column needs to reach somewhere on the island before a candidate seed is
    // accepted.
    private static final int MOUNTAIN_HEIGHT_THRESHOLD = 85;
    private static final int SEED_SEARCH_MAX_ATTEMPTS = 200;
    private static final int SEED_SEARCH_GRID_STEP = 20;

    private static volatile PerlinSimplexNoise mountainNoise;
    private static volatile PerlinSimplexNoise undersideNoise;
    private static volatile PerlinSimplexNoise streamNoise;

    public FairyRealmChunkGenerator(BiomeSource biomeSource)
    {
        super(biomeSource);
    }

    /** The walkable surface height (one above the topmost grass layer) at the flat center - {@link WorldTree}/{@link FairyPortalRuins} build relative to this rather than duplicating the constant it comes from. */
    public static int groundY()
    {
        return FLAT_TOP_Y + 1;
    }

    /**
     * Resolves (and, the first time only, chooses and records) the random seed driving this
     * island's terrain noise. Safe to call more than once - only the first call for a given JVM
     * run actually does anything; every call after that returns immediately. Must run before any
     * real chunk generation for this dimension - see the class doc comment.
     */
    public static synchronized void ensureTerrainReady(ServerLevel fairyRealm)
    {
        if (mountainNoise != null)
        {
            return;
        }
        TerrainSeedSavedData saved = fairyRealm.getDataStorage().computeIfAbsent(TerrainSeedSavedData::load, TerrainSeedSavedData::new, "magiccircles_terrain_seed");
        long seed;
        if (saved.hasSeed)
        {
            seed = saved.seed;
            LOGGER.info("Fairy Realm terrain: reusing recorded seed {}", seed);
        }
        else
        {
            seed = searchForMountainSeed();
            saved.seed = seed;
            saved.hasSeed = true;
            saved.setDirty();
            LOGGER.info("Fairy Realm terrain: recorded new seed {}", seed);
        }
        mountainNoise = new PerlinSimplexNoise(RandomSource.create(seed), List.of(1, 2, 3, 4));
        undersideNoise = new PerlinSimplexNoise(RandomSource.create(seed + 1), List.of(1, 2));
        streamNoise = new PerlinSimplexNoise(RandomSource.create(seed + 2), List.of(1, 2));
    }

    /** See the class doc comment - this is what actually guarantees {@link #ensureTerrainReady} runs before this dimension's very first chunk. */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel serverLevel
                && serverLevel.dimension().equals(com.patrickma.magiccircles.registry.ModDimensions.FAIRY_REALM))
        {
            ensureTerrainReady(serverLevel);
        }
    }

    /** Tries random seeds until one produces at least one column tall enough to read as a real mountain, or gives up and uses the last attempt after {@value #SEED_SEARCH_MAX_ATTEMPTS} tries. */
    private static long searchForMountainSeed()
    {
        RandomSource searchRandom = RandomSource.create();
        for (int attempt = 1; attempt <= SEED_SEARCH_MAX_ATTEMPTS; attempt++)
        {
            long candidate = searchRandom.nextLong();
            PerlinSimplexNoise candidateMountain = new PerlinSimplexNoise(RandomSource.create(candidate), List.of(1, 2, 3, 4));
            // seed+2 - the exact same offset #ensureTerrainReady uses for streamNoise (the river
            // field), so this checks the actual noise the chosen seed will really generate with.
            PerlinSimplexNoise candidateRiver = new PerlinSimplexNoise(RandomSource.create(candidate + 2), List.of(1, 2));
            boolean acceptable = hasTallMountain(candidateMountain) && hasRiver(candidateMountain, candidateRiver);
            if (acceptable || attempt == SEED_SEARCH_MAX_ATTEMPTS)
            {
                LOGGER.info("Fairy Realm terrain: seed {} accepted after {} attempt(s) (mountain and river both present: {})", candidate, attempt, acceptable);
                return candidate;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** A coarse grid scan (not full-resolution - this only needs to find *a* tall spot, not map the whole island) checking whether {@code noise} produces a column reaching {@value #MOUNTAIN_HEIGHT_THRESHOLD} blocks above the flat base anywhere on the island. */
    private static boolean hasTallMountain(PerlinSimplexNoise noise)
    {
        for (int x = -ISLAND_RADIUS; x <= ISLAND_RADIUS; x += SEED_SEARCH_GRID_STEP)
        {
            for (int z = -ISLAND_RADIUS; z <= ISLAND_RADIUS; z += SEED_SEARCH_GRID_STEP)
            {
                double dist = Math.sqrt((double) x * x + (double) z * z);
                if (dist > ISLAND_RADIUS)
                {
                    continue;
                }
                double envelope = mountainEnvelope(x, z, dist);
                if (envelope <= 0.0)
                {
                    continue;
                }
                if (mountainHeightAt(noise, x, z) * envelope >= MOUNTAIN_HEIGHT_THRESHOLD)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The same kind of coarse grid scan as {@link #hasTallMountain}, checking whether {@code
     * riverNoise} actually carves at least one sampled column below {@link #WATER_LEVEL_Y}
     * anywhere on the island - i.e. whether this seed produces a real, visible river/lake at all,
     * not just dry noise blobs that never reach the water table. {@code mountainNoise} is needed
     * alongside it since the carve depth is relative to {@link #mountainHeightAt}, exactly like
     * real generation computes it.
     */
    private static boolean hasRiver(PerlinSimplexNoise mountainNoiseCandidate, PerlinSimplexNoise riverNoiseCandidate)
    {
        for (int x = -ISLAND_RADIUS; x <= ISLAND_RADIUS; x += SEED_SEARCH_GRID_STEP)
        {
            for (int z = -ISLAND_RADIUS; z <= ISLAND_RADIUS; z += SEED_SEARCH_GRID_STEP)
            {
                double dist = Math.sqrt((double) x * x + (double) z * z);
                if (dist > ISLAND_RADIUS || dist <= flatRadius())
                {
                    continue;
                }
                double angleFromEntranceDeg = Math.toDegrees(Math.atan2(x, z));
                if (Math.abs(angleFromEntranceDeg) <= ENTRANCE_HALF_ANGLE_DEG)
                {
                    continue;
                }
                double envelope = mountainEnvelope(x, z, dist);
                int mountainTop = FLAT_TOP_Y + (int) Math.round(mountainHeightAt(mountainNoiseCandidate, x, z) * envelope);
                if (riverCarvedTop(riverNoiseCandidate, x, z, mountainTop) < WATER_LEVEL_Y)
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec()
    {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup registries, RandomState randomState, long seed)
    {
        // No structures at all - normal placement assumes continuous terrain, not one isolated disc.
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, Stream.empty());
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk)
    {
        // The layer stack in fillFromNoise already includes its own top ("grass") layer.
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step)
    {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level)
    {
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos)
    {
    }

    private static double smoothstep(double edge0, double edge1, double x)
    {
        double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Normalizes the noise's own roughly-[-1,1] output to 0-1, then shapes it (see {@link #MOUNTAIN_SHAPE_POWER}) and scales by {@link #MOUNTAIN_AMPLITUDE} - always >= 0, so this only ever adds height above the flat base, never carves below it (the river handles carving separately). */
    private static double mountainHeightAt(PerlinSimplexNoise noise, int x, int z)
    {
        double raw = noise.getValue(x * MOUNTAIN_NOISE_SCALE, z * MOUNTAIN_NOISE_SCALE, false);
        double normalized = Mth.clamp((raw + 1.0) * 0.5, 0.0, 1.0);
        return Math.pow(normalized, MOUNTAIN_SHAPE_POWER) * MOUNTAIN_AMPLITUDE;
    }

    private static double mountainHeightAt(int x, int z)
    {
        PerlinSimplexNoise noise = mountainNoise;
        // Shouldn't happen given #ensureTerrainReady's calling convention - falls back to flat
        // rather than crashing if some other path ever triggers generation first.
        return noise == null ? 0.0 : mountainHeightAt(noise, x, z);
    }

    /** How much of {@link #mountainHeightAt} actually applies at this column - the product of three independent 0-1 tapers (center blend, edge falloff, entrance-angle blend), each detailed on the constant it's built from. */
    private static double mountainEnvelope(int x, int z, double dist)
    {
        double centerBlend = smoothstep(flatRadius(), flatRadius() + BLEND_WIDTH, dist);
        double edgeFalloff = 1.0 - smoothstep(ISLAND_RADIUS - EDGE_TAPER_WIDTH, ISLAND_RADIUS, dist);
        double angleFromEntranceDeg = Math.toDegrees(Math.atan2(x, z));
        double absAngleFromEntranceDeg = Math.abs(angleFromEntranceDeg);
        double angleBlend = smoothstep(ENTRANCE_HALF_ANGLE_DEG, ENTRANCE_HALF_ANGLE_DEG + ENTRANCE_ANGLE_TAPER_DEG, absAngleFromEntranceDeg);
        return centerBlend * edgeFalloff * angleBlend;
    }

    /** Public wrapper around {@link #undersideBottomAt(int, int, double)} - {@link WellspringOcean} traces this same taper directly so its own "vast ocean" cavity always stays within the island's real available rock rather than digging an unrelated shape. */
    public static int undersideBottomAt(int x, int z)
    {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        return undersideBottomAt(x, z, dist);
    }

    /** How deep underground the island's own underside sits at (x,z) - a rough point below the center, tapering to a shallow edge, per the class doc comment. Always at least 1 (never below the world's own floor). */
    private static int undersideBottomAt(int x, int z, double dist)
    {
        double t = Mth.clamp(dist / ISLAND_RADIUS, 0.0, 1.0);
        double taper = Math.pow(1.0 - t, UNDERSIDE_SHARPNESS);
        double raw = UNDERSIDE_EDGE_BOTTOM_Y - (UNDERSIDE_EDGE_BOTTOM_Y - UNDERSIDE_CENTER_BOTTOM_Y) * taper;
        PerlinSimplexNoise noise = undersideNoise;
        double bump = noise == null ? 0.0 : noise.getValue(x * UNDERSIDE_NOISE_SCALE, z * UNDERSIDE_NOISE_SCALE, false) * UNDERSIDE_NOISE_AMPLITUDE;
        return Math.max(1, (int) Math.round(raw + bump));
    }

    /**
     * The full shape math for one column, entirely on its own - flat center, entrance wedge,
     * mountain envelope/height, river, and underside taper, all independent of any neighboring
     * column. {@link #columnShapeAt} is what adds neighbor-awareness (river containment) on top
     * of this.
     */
    private static RawColumn rawColumnAt(int x, int z)
    {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        if (dist > ISLAND_RADIUS)
        {
            return RawColumn.VOID;
        }

        int bottomY = undersideBottomAt(x, z, dist);

        if (dist <= flatRadius())
        {
            return RawColumn.solid(FLAT_TOP_Y, bottomY);
        }

        double angleFromEntranceDeg = Math.toDegrees(Math.atan2(x, z));
        double absAngleFromEntranceDeg = Math.abs(angleFromEntranceDeg);

        if (absAngleFromEntranceDeg <= ENTRANCE_HALF_ANGLE_DEG)
        {
            // Inside the entrance wedge: flat all the way to the island's true edge, no hills,
            // no river - a clear corridor between the tree and the portal ruins.
            return RawColumn.solid(FLAT_TOP_Y, bottomY);
        }

        double envelope = mountainEnvelope(x, z, dist);
        int mountainTop = FLAT_TOP_Y + (int) Math.round(mountainHeightAt(x, z) * envelope);

        PerlinSimplexNoise river = streamNoise;
        if (river == null)
        {
            return RawColumn.solid(mountainTop, bottomY);
        }

        int carvedTop = riverCarvedTop(river, x, z, mountainTop);
        if (carvedTop >= mountainTop)
        {
            // Outside RIVER_HALF_WIDTH_BLOCKS entirely - ordinary dry terrain.
            return RawColumn.solid(mountainTop, bottomY);
        }
        if (carvedTop >= WATER_LEVEL_Y)
        {
            // Dipped, but not deep enough to reach the water table - a real, visible valley/dip
            // in the terrain, just a dry one.
            return RawColumn.solid(carvedTop, bottomY);
        }
        return RawColumn.river(carvedTop, WATER_LEVEL_Y, bottomY);
    }

    /**
     * Roughly how far (x,z) is, in actual blocks, from the river noise's own {@link
     * #RIVER_CENTER} contour line - not just "how far in noise-value units," which would make the
     * channel's real physical width depend on how steep the noise field's local gradient happens
     * to be at that point (see the class-level comment above {@link #RIVER_NOISE_SCALE}). Standard
     * technique for turning a scalar field's level set into an approximate distance field: near
     * the contour, {@code value ~= gradientMagnitude * distance}, so {@code distance ~=
     * value / gradientMagnitude}. The gradient itself is estimated with two extra samples spaced
     * {@link #RIVER_GRADIENT_STEP} block apart.
     */
    private static double riverDistanceFromCenterBlocks(PerlinSimplexNoise river, int x, int z)
    {
        double n0 = river.getValue(x * RIVER_NOISE_SCALE, z * RIVER_NOISE_SCALE, false);
        double nx = river.getValue((x + RIVER_GRADIENT_STEP) * RIVER_NOISE_SCALE, z * RIVER_NOISE_SCALE, false);
        double nz = river.getValue(x * RIVER_NOISE_SCALE, (z + RIVER_GRADIENT_STEP) * RIVER_NOISE_SCALE, false);
        double gradX = (nx - n0) / RIVER_GRADIENT_STEP;
        double gradZ = (nz - n0) / RIVER_GRADIENT_STEP;
        double gradMag = Math.max(1.0E-4, Math.sqrt(gradX * gradX + gradZ * gradZ));
        return Math.abs(n0 - RIVER_CENTER) / gradMag;
    }

    /**
     * The river's own carve at this column, on top of {@code mountainTop} - see the class-level
     * comment above {@link #RIVER_NOISE_SCALE} for the overall idea. Returns {@code mountainTop}
     * unchanged past {@link #RIVER_HALF_WIDTH_BLOCKS} from the contour line entirely; at the
     * contour line itself ({@code t == 1}), always returns exactly {@link #RIVER_BED_TOP} -
     * regardless of how tall {@code mountainTop} was - which is what guarantees the channel
     * actually breaks all the way through any mountain/hill it crosses instead of leaving a dry
     * gap there.
     */
    private static int riverCarvedTop(PerlinSimplexNoise river, int x, int z, int mountainTop)
    {
        double distFromCenter = riverDistanceFromCenterBlocks(river, x, z);
        if (distFromCenter >= RIVER_HALF_WIDTH_BLOCKS)
        {
            return mountainTop;
        }
        double t = 1.0 - distFromCenter / RIVER_HALF_WIDTH_BLOCKS;
        t = t * t * (3.0 - 2.0 * t);
        return mountainTop - (int) Math.round(t * (mountainTop - RIVER_BED_TOP));
    }

    /**
     * {@link #rawColumnAt} plus one thing it deliberately doesn't do on its own: for a
     * non-river column, checking all 8 neighbors and raising this column's own height to match
     * any neighboring river's water surface, if that surface is higher. Without this, two
     * columns computed completely independently (self and a river neighbor a couple of blocks
     * away) can land on either side of a coin-flip in the underlying noise and leave the river's
     * water poking out over land that's actually lower than it - "the blocks immediately
     * surrounding the water must also be at the same level as the water or higher" is exactly
     * what this guarantees, checked fresh for every column rather than assumed from how smooth
     * the noise "should" be.
     */
    private static ColumnShape columnShapeAt(int x, int z)
    {
        RawColumn self = rawColumnAt(x, z);
        if (self.isVoid)
        {
            return ColumnShape.VOID;
        }
        if (self.isRiver())
        {
            return ColumnShape.river(self.top, self.waterTop, self.bottomY);
        }

        int requiredTop = self.top;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (dx == 0 && dz == 0)
                {
                    continue;
                }
                RawColumn neighbor = rawColumnAt(x + dx, z + dz);
                if (neighbor.isRiver())
                {
                    requiredTop = Math.max(requiredTop, neighbor.waterTop);
                }
            }
        }
        return ColumnShape.solid(requiredTop, self.bottomY);
    }

    /** Grass at {@code top}, {@value #TOPSOIL_DIRT_LAYERS} dirt layers just below it, solid stone below that - the same soil profile regardless of how tall {@code top} actually is. */
    private static BlockState blockStateForLayer(int y, int top)
    {
        if (y == top)
        {
            return GRASS;
        }
        if (y > top - 1 - TOPSOIL_DIRT_LAYERS)
        {
            return DIRT;
        }
        return STONE;
    }

    // The lakebed's own material palette - a random scatter of sand/gravel/clay/dirt rather than
    // one uniform block, weighted toward sand and gravel (the two that actually read as "under a
    // stream" at a glance) with clay and dirt as a minority.
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    private static final BlockState[] LAKEBED_PALETTE = {
            SAND, SAND, SAND, GRAVEL, GRAVEL, GRAVEL, CLAY, DIRT
    };

    /** The streambed - {@value #LAKEBED_DEPTH} layers of a random sand/gravel/clay/dirt scatter (see {@link #LAKEBED_PALETTE}), stone deeper still, rather than the grass-topped soil profile every other column gets. Deterministic per-column (hashed from its own position), not a stored {@code Random}, so it's stable however many times this column happens to be queried. */
    private static final int LAKEBED_DEPTH = 3;

    private static BlockState blockStateForLakebed(int x, int y, int z, int bed)
    {
        if (y <= bed - LAKEBED_DEPTH)
        {
            return STONE;
        }
        // A real position-seeded RandomSource (the same idea vanilla uses for per-block
        // deterministic randomness, e.g. decorator noise) rather than a hand-rolled linear
        // hash-mod - the old `x*A + z*B + y*C, mod palette length` combination aliased into
        // visibly repeating diagonal stripes across the bed instead of reading as a genuine random
        // scatter, which is exactly the "flat bed of repeating lines" complaint this replaces.
        RandomSource positionRandom = RandomSource.create(Mth.getSeed(x, y, z));
        return LAKEBED_PALETTE[positionRandom.nextInt(LAKEBED_PALETTE.length)];
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk)
    {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        int chunkOriginX = chunk.getPos().getMinBlockX();
        int chunkOriginZ = chunk.getPos().getMinBlockZ();

        for (int localX = 0; localX < 16; localX++)
        {
            for (int localZ = 0; localZ < 16; localZ++)
            {
                int worldX = chunkOriginX + localX;
                int worldZ = chunkOriginZ + localZ;
                ColumnShape shape = columnShapeAt(worldX, worldZ);
                if (shape.isVoid())
                {
                    continue;
                }

                int top = shape.solidTop;
                for (int y = Math.max(1, shape.bottomY); y <= top; y++)
                {
                    BlockState state = shape.isRiver() ? blockStateForLakebed(worldX, y, worldZ, top) : blockStateForLayer(y, top);
                    mutablePos.set(localX, y, localZ);
                    chunk.setBlockState(mutablePos, state, false);
                    oceanFloor.update(localX, y, localZ, state);
                    worldSurface.update(localX, y, localZ, state);
                }

                if (shape.isRiver())
                {
                    for (int y = shape.solidTop + 1; y <= shape.waterTop; y++)
                    {
                        mutablePos.set(localX, y, localZ);
                        chunk.setBlockState(mutablePos, water(), false);
                        oceanFloor.update(localX, y, localZ, water());
                    }
                }
            }
        }

        // The World Tree and the ruined portal chamber are NOT placed here - both are far too
        // large (or too deep/precise) to paste from inside a per-chunk worldgen hook, which only
        // has a small region of nearby chunks actually accessible. See WorldTree#placeIfNeeded
        // and FairyPortalRuins#placeIfNeeded - both are one-time world edits that run once the
        // server (and this level) actually exist, not during chunk generation itself.

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState)
    {
        ColumnShape shape = columnShapeAt(x, z);
        if (shape.isVoid())
        {
            return level.getMinBuildHeight();
        }
        return shape.solidTop + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState)
    {
        ColumnShape shape = columnShapeAt(x, z);
        BlockState[] column = new BlockState[level.getHeight()];
        if (!shape.isVoid())
        {
            for (int y = Math.max(1, shape.bottomY); y <= shape.solidTop && y - level.getMinBuildHeight() < column.length; y++)
            {
                column[y - level.getMinBuildHeight()] = shape.isRiver()
                        ? blockStateForLakebed(x, y, z, shape.solidTop)
                        : blockStateForLayer(y, shape.solidTop);
            }
            if (shape.isRiver())
            {
                for (int y = shape.solidTop + 1; y <= shape.waterTop && y - level.getMinBuildHeight() < column.length; y++)
                {
                    column[y - level.getMinBuildHeight()] = water();
                }
            }
        }
        for (int i = 0; i < column.length; i++)
        {
            if (column[i] == null)
            {
                column[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public int getGenDepth()
    {
        return 256;
    }

    @Override
    public int getSeaLevel()
    {
        return -63;
    }

    @Override
    public int getMinY()
    {
        return 0;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level)
    {
        return groundY();
    }

    /** {@link #rawColumnAt}'s own, neighbor-independent result: either void, solid ground up to {@code top}, or a river channel (solid up to {@code top}, then water up to {@code waterTop}) - either way with {@code bottomY} as its lowest solid block. */
    private static final class RawColumn
    {
        static final RawColumn VOID = new RawColumn(true, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        final boolean isVoid;
        final int top;
        final int waterTop;
        final int bottomY;

        private RawColumn(boolean isVoid, int top, int waterTop, int bottomY)
        {
            this.isVoid = isVoid;
            this.top = top;
            this.waterTop = waterTop;
            this.bottomY = bottomY;
        }

        static RawColumn solid(int top, int bottomY)
        {
            return new RawColumn(false, top, Integer.MIN_VALUE, bottomY);
        }

        static RawColumn river(int bed, int waterTop, int bottomY)
        {
            return new RawColumn(false, bed, waterTop, bottomY);
        }

        boolean isRiver()
        {
            return waterTop != Integer.MIN_VALUE;
        }
    }

    /** {@link #columnShapeAt}'s final result, after the neighbor-containment pass {@link RawColumn} doesn't do on its own. Same shape as {@link RawColumn} otherwise. */
    private static final class ColumnShape
    {
        static final ColumnShape VOID = new ColumnShape(true, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        final int solidTop;
        final int waterTop;
        final int bottomY;
        private final boolean isVoid;

        private ColumnShape(boolean isVoid, int solidTop, int waterTop, int bottomY)
        {
            this.isVoid = isVoid;
            this.solidTop = solidTop;
            this.waterTop = waterTop;
            this.bottomY = bottomY;
        }

        static ColumnShape solid(int top, int bottomY)
        {
            return new ColumnShape(false, top, Integer.MIN_VALUE, bottomY);
        }

        static ColumnShape river(int bed, int waterTop, int bottomY)
        {
            return new ColumnShape(false, bed, waterTop, bottomY);
        }

        boolean isVoid()
        {
            return isVoid;
        }

        boolean isRiver()
        {
            return waterTop != Integer.MIN_VALUE;
        }
    }

    /** Just the one recorded value this needs to persist: the random seed chosen for this world's terrain noise, if one has been chosen yet. */
    public static final class TerrainSeedSavedData extends SavedData
    {
        private boolean hasSeed;
        private long seed;

        public TerrainSeedSavedData()
        {
        }

        public static TerrainSeedSavedData load(CompoundTag tag)
        {
            TerrainSeedSavedData data = new TerrainSeedSavedData();
            data.hasSeed = tag.getBoolean("HasSeed");
            data.seed = tag.getLong("Seed");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("HasSeed", hasSeed);
            tag.putLong("Seed", seed);
            return tag;
        }
    }
}
