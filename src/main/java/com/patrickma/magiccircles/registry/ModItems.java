package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.item.AthameItem;
import com.patrickma.magiccircles.item.ChalkItem;
import com.patrickma.magiccircles.item.ColoredChalkItem;
import com.patrickma.magiccircles.item.FairyHornItem;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.item.LostWaystoneItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every item the mod adds. */
public class ModItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MagicCircles.MOD_ID);

    /** Crafting reagent. It doesn't do anything by itself yet - it's the raw magical material other recipes build on. */
    public static final RegistryObject<Item> ARCANE_DUST = ITEMS.register("arcane_dust",
            () -> new Item(new Item.Properties()));

    /** Right-click a block's top face with this to draw a magic circle on it. Wears down over 200 draws. */
    public static final RegistryObject<Item> CHALK = ITEMS.register("chalk",
            () -> new ChalkItem(ModBlocks.MAGIC_CIRCLE.get(), new Item.Properties().durability(200)));

    /** Same as {@link #CHALK}, but the rune it draws is marked Gold - a different wisp color and, once a ring is uniformly this color, a different spell. */
    public static final RegistryObject<Item> GOLD_CHALK = ITEMS.register("gold_chalk",
            () -> new ColoredChalkItem(ModBlocks.MAGIC_CIRCLE.get(), new Item.Properties().durability(200), RuneColor.GOLD));

    /** Same idea as {@link #GOLD_CHALK}, marked Purple instead. How to craft this is still undecided - creative-only for now. */
    public static final RegistryObject<Item> PURPLE_CHALK = ITEMS.register("purple_chalk",
            () -> new ColoredChalkItem(ModBlocks.MAGIC_CIRCLE.get(), new Item.Properties().durability(200), RuneColor.PURPLE));

    /** Same idea as {@link #GOLD_CHALK}, marked Red instead. How to craft this is still undecided - creative-only for now. */
    public static final RegistryObject<Item> RED_CHALK = ITEMS.register("red_chalk",
            () -> new ColoredChalkItem(ModBlocks.MAGIC_CIRCLE.get(), new Item.Properties().durability(200), RuneColor.RED));

    /**
     * Same idea as {@link #GOLD_CHALK}, marked Green instead. Crafted from plain {@link #CHALK}
     * and {@link #THELIA_DUST} (data/magiccircles/recipes/green_chalk_from_thelia_dust.json) -
     * the one colored chalk with a real survival recipe so far.
     */
    public static final RegistryObject<Item> GREEN_CHALK = ITEMS.register("green_chalk",
            () -> new ColoredChalkItem(ModBlocks.MAGIC_CIRCLE.get(), new Item.Properties().durability(200), RuneColor.GREEN));

    /** Right-click the ground at the center of a completed magic circle ring to summon its heart core. */
    public static final RegistryObject<Item> HEARTSTONE = ITEMS.register("heartstone",
            () -> new HeartstoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** The item form of {@link ModBlocks#FAIRY_FOSSIL_ORE}, mostly for creative-mode use - survival mining never drops it. */
    public static final RegistryObject<Item> FAIRY_FOSSIL_ORE = ITEMS.register("fairy_fossil_ore",
            () -> new BlockItem(ModBlocks.FAIRY_FOSSIL_ORE.get(), new Item.Properties()));

    /** Right-click a working Heart Core with this to cast a spell - see {@link com.patrickma.magiccircles.block.HeartCoreBlock}. */
    public static final RegistryObject<Item> FAIRY_HORN = ITEMS.register("fairy_horn",
            () -> new FairyHornItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** One-way trip straight to the Fairy Realm's shared portal - see {@link LostWaystoneItem}. Creative-only for now; no craft or drop yet gives you one. */
    public static final RegistryObject<Item> LOST_WAYSTONE = ITEMS.register("lost_waystone",
            () -> new LostWaystoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Item forms of the Living Wood Tree's own blocks (see {@link ModBlocks#LIVING_WOOD_LOG}/{@link ModBlocks#LIVING_WOOD_LEAVES}) - mostly for creative-mode use, since the tree itself only ever grows from worldgen. */
    public static final RegistryObject<Item> LIVING_WOOD_LOG = ITEMS.register("living_wood_log",
            () -> new BlockItem(ModBlocks.LIVING_WOOD_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> LIVING_WOOD_LEAVES = ITEMS.register("living_wood_leaves",
            () -> new BlockItem(ModBlocks.LIVING_WOOD_LEAVES.get(), new Item.Properties()));

    /** Pale pink/gold, matching {@link com.patrickma.magiccircles.entity.PixieEntity}'s own recolor. */
    public static final RegistryObject<Item> PIXIE_SPAWN_EGG = ITEMS.register("pixie_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.PIXIE, 0xF7C8E0, 0xE8B84B, new Item.Properties()));

    /** Coat brown/antler tan, matching {@link com.patrickma.magiccircles.entity.DreamElkEntity}'s own texture. */
    public static final RegistryObject<Item> DREAM_ELK_SPAWN_EGG = ITEMS.register("dream_elk_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.DREAM_ELK, 0x6B4A2E, 0xD6C5A8, new Item.Properties()));

    /**
     * Dropped by {@link com.patrickma.magiccircles.entity.ManaWyrmEntity} on death - a plain
     * minor food, same as vanilla raw fish, plus 1 minute of {@link ModEffects#BLESSED_BY_WELLSPRING}
     * - see that effect's own doc comment for what it unlocks.
     */
    public static final RegistryObject<Item> RAW_MANA_WYRM = ITEMS.register("raw_mana_wyrm",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationMod(0.1f)
                    .effect(() -> new MobEffectInstance(ModEffects.BLESSED_BY_WELLSPRING.get(), 20 * 60, 0), 1.0f)
                    .build())));

    /**
     * Cooked from {@link #RAW_MANA_WYRM} - 5 full hunger icons (10 nutrition) plus a guaranteed
     * short Regeneration buff, and 2 minutes of {@link ModEffects#BLESSED_BY_WELLSPRING} (double
     * the raw version's own 1 minute).
     */
    public static final RegistryObject<Item> COOKED_MANA_WYRM = ITEMS.register("cooked_mana_wyrm",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(10).saturationMod(0.6f)
                    .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 200, 1), 1.0f)
                    .effect(() -> new MobEffectInstance(ModEffects.BLESSED_BY_WELLSPRING.get(), 20 * 120, 0), 1.0f)
                    .build())));

    /**
     * The mod's own lore book - a real, unique item (not a written book, book and quill, or any
     * other vanilla book variant). Looks closed while held/in-inventory/on the ground (a plain
     * flat icon - see {@code textures/item/book_of_the_faye.png}); you have to place it (ordinary
     * {@code BlockItem} behavior) and right-click the *placed* book to actually read it - see
     * {@link com.patrickma.magiccircles.block.BookOfTheFayeBlock}'s own doc comment.
     */
    public static final RegistryObject<Item> BOOK_OF_THE_FAYE = ITEMS.register("book_of_the_faye",
            () -> new BlockItem(ModBlocks.BOOK_OF_THE_FAYE_BLOCK.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /**
     * A green powder smelted from a Living Wood Log (see data/magiccircles/recipes/thelia_dust_smelting.json)
     * - crafted with plain Chalk, it makes Green Chalk (data/magiccircles/recipes/green_chalk_from_thelia_dust.json).
     * Purely a redstone-dust reskin, mechanically inert otherwise - it doesn't place a wire or do
     * anything redstone-like, it's just the item icon this mod's own equivalent "fine magical
     * powder" borrows.
     */
    public static final RegistryObject<Item> THELIA_DUST = ITEMS.register("thelia_dust",
            () -> new Item(new Item.Properties()));

    /**
     * Forbidden magic's own color - see {@link com.patrickma.magiccircles.block.RuneColor#BLACK}
     * and {@code limbo/RiteOfPassage}. Crafted from plain {@link #CHALK} and coal/charcoal (see
     * data/magiccircles/recipes/black_chalk.json).
     */
    public static final RegistryObject<Item> BLACK_CHALK = ITEMS.register("black_chalk",
            () -> new ColoredChalkItem(ModBlocks.MAGIC_CIRCLE.get(), new Item.Properties().durability(200), RuneColor.BLACK));

    /**
     * A double-edged dagger - right-click the center rune of a complete Black-chalk ring with
     * this to perform {@code limbo/RiteOfPassage}. Crafted from a stick and an iron ingot (see
     * data/magiccircles/recipes/athame.json). Does nothing as a weapon; this is a ritual tool,
     * not gear.
     */
    public static final RegistryObject<Item> ATHAME = ITEMS.register("athame",
            () -> new AthameItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
}
