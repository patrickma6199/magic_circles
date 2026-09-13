package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.BookOfTheFayeBlock;
import com.patrickma.magiccircles.block.FairyPortalWaterBlock;
import com.patrickma.magiccircles.block.GlitterWeedBlock;
import com.patrickma.magiccircles.block.GlitterWeedPlantBlock;
import com.patrickma.magiccircles.block.HeartCoreBlock;
import com.patrickma.magiccircles.block.HeartCoreTopBlock;
import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.WellspringWaterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every block the mod adds. Register new blocks here, then give them an item in {@link ModItems}. */
public class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MagicCircles.MOD_ID);

    public static final RegistryObject<Block> MAGIC_CIRCLE = BLOCKS.register("magic_circle",
            () -> new MagicCircleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .instabreak()
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .noOcclusion()
                    .lightLevel(state -> 2)));

    /** Never placed directly (no BlockItem) - only appears where {@link com.patrickma.magiccircles.item.HeartstoneItem} puts one. */
    public static final RegistryObject<Block> HEART_CORE = BLOCKS.register("heart_core",
            () -> new HeartCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5f)
                    .noOcclusion()
                    .lightLevel(state -> 14)));

    /** A companion hitbox for {@link #HEART_CORE} - see {@link HeartCoreTopBlock}. */
    public static final RegistryObject<Block> HEART_CORE_TOP = BLOCKS.register("heart_core_top",
            () -> new HeartCoreTopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(1.5f)
                    .noOcclusion()));

    /** The placed form of {@link com.patrickma.magiccircles.registry.ModItems#BOOK_OF_THE_FAYE} - see {@link BookOfTheFayeBlock}'s own doc comment. */
    public static final RegistryObject<Block> BOOK_OF_THE_FAYE_BLOCK = BLOCKS.register("book_of_the_faye_block",
            () -> new BookOfTheFayeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .strength(1.0f)
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    /** As common as coal, same Y range - see the worldgen files under data/magiccircles/worldgen and data/magiccircles/forge. */
    public static final RegistryObject<Block> FAIRY_FOSSIL_ORE = BLOCKS.register("fairy_fossil_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PINK)
                    .requiresCorrectToolForDrops()
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.STONE)));

    /**
     * What the portal spell's water pit turns into - see {@code FairyPortalManager}. A real
     * fluid block ({@link FairyPortalWaterBlock} extends vanilla's own {@code LiquidBlock}),
     * backed by {@link ModFluids#PORTAL_WATER} rather than vanilla water - see that class and
     * {@link ModFluidTypes} for why. `copy(Blocks.WATER)` as the properties base is what gives
     * it water's exact collision/push/light-dampening behavior at the block level; the swim,
     * breathing, and bubble-HUD behavior come from the fluid/FluidType instead. Not obtainable
     * as an item.
     */
    public static final RegistryObject<FairyPortalWaterBlock> FAIRY_PORTAL_WATER = BLOCKS.register("fairy_portal_water",
            () -> new FairyPortalWaterBlock(() -> ModFluids.PORTAL_WATER.get(), BlockBehaviour.Properties.copy(Blocks.WATER)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .lightLevel(state -> 6)));

    /**
     * The Wellspring's water - see {@link WellspringWaterBlock} for why this is a real fluid
     * block rather than plain water, and {@code worldgen/WorldTree.java}'s well for where it's
     * placed. Not obtainable as an item.
     */
    public static final RegistryObject<WellspringWaterBlock> WELLSPRING_WATER = BLOCKS.register("wellspring_water",
            () -> new WellspringWaterBlock(() -> ModFluids.WELLSPRING_WATER.get(), BlockBehaviour.Properties.copy(Blocks.WATER)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .lightLevel(state -> 10)));

    /**
     * The Fairy Realm's own native tree - see {@code data/magiccircles/worldgen/configured_feature/living_wood_tree.json}
     * for the actual tree shape, which reuses vanilla's own Dark Oak giant-tree trunk/foliage
     * placers (the same Java classes the biggest trees in the base game use) rather than any new
     * placer code, just pointed at these blocks instead of vanilla's. Plain {@link
     * RotatedPillarBlock}, same as any vanilla log.
     */
    public static final RegistryObject<Block> LIVING_WOOD_LOG = BLOCKS.register("living_wood_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LOG)
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)));

    /** This tree's leaves - a soft glow ({@code lightLevel}) is the one deliberate difference from a plain {@link Blocks#OAK_LEAVES} copy, so a Living Wood canopy actually reads as magical after dark. */
    public static final RegistryObject<Block> LIVING_WOOD_LEAVES = BLOCKS.register("living_wood_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .lightLevel(state -> 4)));

    /** The growing tip of Glitter Weed - a reskinned Kelp planted along {@code worldgen/WellspringOcean}'s own seabed. See {@link GlitterWeedBlock}'s own doc comment. Not obtainable as an item (never placed by hand, only by worldgen). */
    public static final RegistryObject<Block> GLITTER_WEED = BLOCKS.register("glitter_weed",
            () -> new GlitterWeedBlock(BlockBehaviour.Properties.copy(Blocks.KELP)));

    /** The body segments below {@link #GLITTER_WEED}'s own tip - same relationship as vanilla's own {@code Blocks.KELP}/{@code Blocks.KELP_PLANT}. Not obtainable as an item. */
    public static final RegistryObject<Block> GLITTER_WEED_PLANT = BLOCKS.register("glitter_weed_plant",
            () -> new GlitterWeedPlantBlock(BlockBehaviour.Properties.copy(Blocks.KELP_PLANT)));

    /**
     * A reskinned Shroomlight, placed on top of each fully-grown Glitter Weed patch (see {@code
     * worldgen/WellspringOcean}) - both the light source and, once it caps a patch, the reason
     * that patch stops growing (a growing Kelp-alike tip can't extend into a solid block). Plain
     * {@link Block}, no special behavior beyond vanilla Shroomlight's own copied properties -
     * unlike the weed itself, this never needs to know about water at all. Not obtainable as an
     * item (worldgen-only).
     */
    public static final RegistryObject<Block> GLITTER_WEED_SAC = BLOCKS.register("glitter_weed_sac",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SHROOMLIGHT)
                    .mapColor(MapColor.COLOR_RED)));
}
