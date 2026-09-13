package com.patrickma.magiccircles.client.book;

import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Everything the Book of the Faye says, as chapters of text and illustrations. {@code
 * BookOfTheFayeScreen} flows these onto however many pages they need, so nothing here is written
 * against a page size and adding a paragraph never shifts a diagram onto the wrong page.
 *
 * <p>The prose is the Faye's own telling; the figures beside it are the practical half - real item
 * icons, real recipes, and the ring diagrams drawn as the footprint you would actually chalk.
 */
public final class FayeBookContent
{
    /** One paragraph, or one illustration. A page is a run of these. */
    public record Block(Component text, FayeFigure figure)
    {
        public static Block text(String literal)
        {
            return new Block(Component.literal(literal), null);
        }

        public static Block figure(FayeFigure figure)
        {
            return new Block(null, figure);
        }
    }

    public record Chapter(String title, List<Block> blocks)
    {
    }

    private FayeBookContent()
    {
    }

    private static ItemStack stack(net.minecraft.world.level.ItemLike item)
    {
        return new ItemStack(item);
    }

    private static List<ItemStack> row(net.minecraft.world.level.ItemLike... items)
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (net.minecraft.world.level.ItemLike item : items)
        {
            stacks.add(stack(item));
        }
        return stacks;
    }

    /** Nine cells for a crafting bench; {@code null} leaves a slot empty. */
    private static List<ItemStack> grid(net.minecraft.world.level.ItemLike... items)
    {
        List<ItemStack> cells = new ArrayList<>();
        for (net.minecraft.world.level.ItemLike item : items)
        {
            cells.add(item == null ? ItemStack.EMPTY : stack(item));
        }
        while (cells.size() < 9)
        {
            cells.add(ItemStack.EMPTY);
        }
        return cells;
    }

    private static List<RuneColor> solid(RuneColor color)
    {
        return Collections.nCopies(12, color);
    }

    /** Six of one and six of the other - the split the ritual actually counts, drawn as top and bottom. */
    private static List<RuneColor> split(RuneColor top, RuneColor bottom)
    {
        List<RuneColor> ring = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            ring.add(i < 6 ? top : bottom);
        }
        return ring;
    }

    public static List<Chapter> chapters()
    {
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(beforeTheRecalling());
        chapters.add(whatYourHandsMake());
        chapters.add(theWellspringSpells());
        chapters.add(commandingTheHeartstone());
        chapters.add(yllumere());
        chapters.add(theBlessing());
        chapters.add(theAncientHeartstone());
        chapters.add(theDreamElk());
        chapters.add(theOtherSide());
        chapters.add(thisVeryBook());
        return chapters;
    }

    private static Chapter beforeTheRecalling()
    {
        return new Chapter("Before the Recalling", List.of(
                Block.text("Long before these hills were counted, the Faye walked beneath open sky, drawing their magic from the Wellspring - a river of silver water unlike any other."),
                Block.text("The Faye believed the Wellspring was the last remnant of the Font of Making: the very tool the Worldsmiths once used to shape all worlds from the formless dark."),
                Block.text("Other creatures drank of that same silver water and could live nowhere else - small drifting lights the Faye called Wellwisps, and pale naiads who never left its banks."),
                Block.text("What the Faye did to earn the wrath of the Worldsmiths, no scroll now records. Some songs speak of a stolen secret. Others, of a broken oath. None agree."),
                Block.text("Whatever the offense, judgment came. Queen Zuzo felt it first: a silence where the Wellspring's song should be, and a certainty the overworld itself would soon be unmade."),
                Block.text("She would not wait to learn if she was right. Zuzo called her people to carry what water they could into a shard of unmade space, hastily woven before the end."),
                Block.text("They saved only a trickle of what once flowed free. It was not enough for a kingdom. It was enough, Zuzo swore, for her people to survive whatever came next."),
                Block.text("Then the Recalling came. The Worldsmiths did not shatter the overworld as the Faye had feared. They simply reached in and drew every drop of Wellspring water back out."),
                Block.text("The world above was left whole, untouched, ignorant of what it had lost. Only the Faye, and the creatures bound to the Wellspring's magic, understood what was gone."),
                Block.text("The Worldsmiths judged exile in that one small saved shard punishment enough, and left the Faye to it. Mortals later found its door and named it the Fairy Realm."),
                Block.text("The Faye have another name for their refuge: Yllumere, the Sheltered Water. There, still, the Wellspring runs - thin, precious, and utterly alone in all the worlds.")
        ));
    }

    private static Chapter whatYourHandsMake()
    {
        return new Chapter("What Your Hands Must Make", List.of(
                Block.text("Grind Redstone together with Glowstone Dust and you have Arcane Dust, two at a time. Everything else begins there."),
                Block.figure(FayeFigure.shapeless(row(Items.REDSTONE, Items.GLOWSTONE_DUST), stack(ModItems.ARCANE_DUST.get()), FRAME, FILL)),
                Block.text("Arcane Dust and Bone Meal make plain Chalk. Each piece draws two hundred runes before it is spent."),
                Block.figure(FayeFigure.shapeless(row(ModItems.ARCANE_DUST.get(), Items.BONE_MEAL), stack(ModItems.CHALK.get()), FRAME, FILL)),
                Block.text("Swap the Bone Meal for a Gold Nugget and the chalk comes out Gold instead."),
                Block.figure(FayeFigure.shapeless(row(ModItems.ARCANE_DUST.get(), Items.GOLD_NUGGET), stack(ModItems.GOLD_CHALK.get()), FRAME, FILL)),
                Block.text("Chalk takes color after the fact as well. Thelia Dust turns it Green; coal or charcoal turns it Black. Tinting never restores a worn piece - what is spent stays spent."),
                Block.figure(FayeFigure.shapeless(row(ModItems.CHALK.get(), ModItems.THELIA_DUST.get()), stack(ModItems.GREEN_CHALK.get()), FRAME, FILL)),
                Block.figure(FayeFigure.shapeless(row(ModItems.CHALK.get(), Items.COAL), stack(ModItems.BLACK_CHALK.get()), FRAME, FILL)),
                Block.text("Thelia Dust is what a Living Wood Log becomes in a furnace. The log comes from the World Tree; you will not find it under your own sky."),
                Block.text("Red Chalk answers to no recipe at all. Only a Dream Elk will give it, and only if you feed it first."),
                Block.text("Dig for Fairy Fossil Ore: a pink bone-ridden stone, as common as coal and lying at the same depths. A stone pick will break it; a wooden one will not."),
                Block.text("Nineteen in twenty fossils give up nothing but a Bone. Roughly one in twenty yields a Fairy Horn, and one in a hundred a Heartstone already holding five hundred mana."),
                Block.figure(FayeFigure.items(row(ModItems.FAIRY_FOSSIL_ORE.get(), Items.BONE, ModItems.FAIRY_HORN.get(), ModItems.HEARTSTONE.get()), FRAME, FILL)),
                Block.text("The Horn is the key to every circle. It does nothing in the hand alone - its whole purpose is to be touched to a finished ring."),
                Block.text("An Athame - one Iron Ingot above one Stick - cuts nothing but the veil. Keep it away from black chalk unless you mean it."),
                Block.figure(FayeFigure.shaped(grid(Items.IRON_INGOT, null, null, Items.STICK, null, null, null, null, null),
                        stack(ModItems.ATHAME.get()), FRAME, FILL))
        ));
    }

    private static Chapter theWellspringSpells()
    {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.text("Draw the ring in chalk, then touch a Fairy Horn to its heart. A ring is twelve runes around a single Heart Core, and each color - and each pairing of colors - answers differently."));
        blocks.add(Block.text("Position never matters. Only how many runes of each color the ring holds."));

        addSpell(blocks, "Zuzo's Wrathful Downpour", solid(RuneColor.BLUE),
                "Pure Blue, 100 mana. Zuzo's tempest rages thirty seconds; lightning finds foes beyond the circle.");
        addSpell(blocks, "Korrin's Unbroken Ward", solid(RuneColor.PURPLE),
                "Pure Purple. Unbreakable wards rise, and every hit they take costs the heart a point of mana. Purple wisps trace the true edge.");
        addSpell(blocks, "The Verdant Mother's Blessing", solid(RuneColor.GOLD),
                "Pure Gold, 30 mana. Wildflowers bloom across the grass around the circle.");
        addSpell(blocks, "Sylvaine's Mercy", solid(RuneColor.RED),
                "Pure Red, 60 mana. Every friendly creature nearby is restored to full health.");
        addSpell(blocks, "The Gleaner's Fortune", solid(RuneColor.GREEN),
                "Pure Green, 40 mana. Loose experience the Gleaner gathered scatters free.");
        addSpell(blocks, "Zuzo and Sylvaine's Absolution", split(RuneColor.BLUE, RuneColor.RED),
                "Half Blue, half Red, 50 mana. Washes poison and fire from every creature nearby.");
        addSpell(blocks, "Korrin and Zuzo's Last Stand", split(RuneColor.BLUE, RuneColor.PURPLE),
                "Half Blue, half Purple, 120 mana. Wards rise forty-five seconds, striking foes with lightning.");
        addSpell(blocks, "Korrin and the Gleaner's Bargain", split(RuneColor.PURPLE, RuneColor.GREEN),
                "Half Purple, half Green. Draws mana from a Wellspring within fifteen blocks - and nothing at all without one.");
        addSpell(blocks, "Verdant Mother and Sylvaine's Bloom", split(RuneColor.GOLD, RuneColor.RED),
                "Half Gold, half Red, 80 mana. Flowers bloom, wounds close, and vigor lingers after.");
        addSpell(blocks, "Verdant Mother and Gleaner's Harvest", split(RuneColor.GOLD, RuneColor.GREEN),
                "Half Gold, half Green, 50 mana. Crops ripen fully; a little fortune gleaned too.");
        addSpell(blocks, "Sylvaine and the Gleaner's Vigor", split(RuneColor.RED, RuneColor.GREEN),
                "Half Red, half Green. Fifty mana a minute for as long as it holds; grants allies vigor.");

        blocks.add(Block.text("Zuzo's Crossing is not a ring of colors at all, but a pattern - redstone at the corners, Blue and Gold between. Five hundred mana."));
        blocks.add(Block.figure(FayeFigure.pattern(portalCells(), Component.literal("Zuzo's Crossing"))));
        blocks.add(Block.text("It deepens the water around the heart. Submerge yourself and hold your breath, and you will wake on the other side."));
        blocks.add(Block.text("Not every ring answers. Blue and Gold. Blue and Green. Gold and Purple. Purple and Red. Perhaps their spells are lost - or the Faye never found them at all."));
        return new Chapter("The Wellspring Spells", blocks);
    }

    private static void addSpell(List<Block> blocks, String name, List<RuneColor> ring, String description)
    {
        blocks.add(Block.figure(FayeFigure.circle(ring, Component.literal(name))));
        blocks.add(Block.text(description));
    }

    /** The portal's own border - redstone corners, Blue on the axes, Gold between. */
    private static int[] portalCells()
    {
        int redstone = 0xC03030;
        int blue = FayeFigure.packed(RuneColor.BLUE.wispColor());
        int gold = FayeFigure.packed(RuneColor.GOLD.wispColor());
        int[] cells = new int[]{
                redstone, blue, gold, blue, redstone,
                gold, 0, 0, 0, gold,
                blue, 0, FayeFigure.HEART, 0, blue,
                gold, 0, 0, 0, gold,
                redstone, blue, gold, blue, redstone
        };
        return Arrays.copyOf(cells, cells.length);
    }

    private static Chapter commandingTheHeartstone()
    {
        return new Chapter("Commanding the Heartstone", List.of(
                Block.text("Eat of a Mana Wyrm - a slithering, glowing thing that lives only in Wellspring water - and you are Blessed By the Wellspring: one minute raw, two minutes cooked."),
                Block.figure(FayeFigure.items(row(ModItems.RAW_MANA_WYRM.get(), ModItems.COOKED_MANA_WYRM.get(), ModItems.HEARTSTONE.get()), FRAME, FILL)),
                Block.text("While the Blessing holds, stand empty-handed before a working Heart Core whose ring is complete and idle, and touch it. Its wisps will pour into the heart itself."),
                Block.text("After a pause, the heart and its ring both vanish, and a Heartstone - commanded to whatever spell that ring once cast - appears straight in your hand."),
                Block.text("A commanded Heartstone answers to a simple touch. Hold it, use it, and it casts its spell around you rather than around any circle - lasting twenty seconds, whatever the spell."),
                Block.text("Three white wisps circle you the whole while a lasting spell holds. The spell's own colors fly from the stone itself."),
                Block.text("Command Korrin's Ward this way and its purple wisps will not follow you - they trace the ward's true boundary, fixed where you stood the instant you cast it."),
                Block.text("A commanded Heartstone cannot be commanded again while it still answers to its first spell. Only charging it with mana, and the Cleansing itself, may be done to a stone already spoken for."),
                Block.text("Only the Cleansing unmakes a commission. Kneel at the Wellspring's edge, heartstone in hand, and touch the water. The old commission washes away and the stone is blank again.")
        ));
    }

    private static Chapter yllumere()
    {
        return new Chapter("Yllumere, and the Road to It", List.of(
                Block.text("Zuzo's Crossing opens the way: the portal ring, cast like any other spell. Submerge yourself in the deepened water, hold your breath, and you will wake in the Fairy Realm."),
                Block.text("Crossing back is the same drowning in reverse. Whoever opened a portal always returns to their own; anyone else who follows them through lands at some other open door."),
                Block.text("There is a second road, and it runs one way only. Let an Ender Pearl fall into Wellspring Water and leave it there. The wisps will come for it, circling tighter for three long seconds."),
                Block.figure(FayeFigure.items(row(Items.ENDER_PEARL, ModItems.LOST_WAYSTONE.get()), FRAME, FILL)),
                Block.text("What they leave behind is a Lost Waystone. Hold it, use it, and you are pulled to Yllumere at once - but the stone is spent in the carrying, and it will not bring you home."),
                Block.text("In Yllumere stands the World Tree, and at its foot a well of silver water: the Wellspring itself, the last of it in all the worlds. A stair is cut into the well's rim to climb down."),
                Block.text("The water is thick with life. Mana Wyrms glide in the deep ocean beneath, Pixies drift about the branches, and Dream Elk wander the islands with antlers taller than they are."),
                Block.text("Glitter Weed grows in the shallows. Wyrms like to hide in it, and they are hard to catch anywhere else.")
        ));
    }

    private static Chapter theBlessing()
    {
        return new Chapter("The Blessing of the Wellspring", List.of(
                Block.text("Eat a Mana Wyrm and the Wellspring marks you kindly: one minute raw, two minutes cooked. While it holds, you are something closer to Faye than not."),
                Block.text("Blessed, you may fly. Double-tap your jump in open air and the fairy wings open; double-tap again and they fold and you fall. No fall will hurt you while the Blessing lasts."),
                Block.text("Blessed, the Wellspring is no longer drowning water. You breathe it freely and see through it perfectly, as clear below the surface as above it."),
                Block.text("And Blessed, swimming in that same water slowly renews the Blessing - but only if you still carry it. Let it lapse entirely and the water will not give it back. Eat another Wyrm."),
                Block.text("The Blessing is also what lets you take a ring's spell into a Heartstone, and what lets you command one afterwards.")
        ));
    }

    private static Chapter theAncientHeartstone()
    {
        return new Chapter("The Ancient Heartstone", List.of(
                Block.text("Above the Wellspring itself hangs a heart no ring ever summoned and no Faye ever commanded - gold where every other heart glows blue-white, and utterly unbreakable."),
                Block.text("The old songs call it the Ancient Heartstone, and say it is this heart, not any ward the Faye could raise, that holds Yllumere's true boundary against what waits beyond it."),
                Block.text("Whatever hunts in the space between worlds, the songs say, cannot cross while the Ancient Heartstone still hangs there gold and burning. No Faye now living remembers a time it did not."),
                Block.text("A faint ring of purple ever circles it, close and dormant - the same tell any idle heart shows, and proof enough that it has never once needed to be cast to keep working.")
        ));
    }

    private static Chapter theDreamElk()
    {
        return new Chapter("The Dream Elk", List.of(
                Block.text("They wander Yllumere on long legs, antlers branching higher than their own shoulders, and flowers open in the grass wherever they walk."),
                Block.text("An Elk may be tamed as a horse is tamed - mount it and be thrown, and mount it again, until it stops throwing you. Saddle it after that and it will carry you."),
                Block.text("Offer one grass seeds, or any flower, and it will take the gift. Press plain Chalk to it afterwards and the chalk comes away Red, worn exactly as much as it was before."),
                Block.figure(FayeFigure.items(row(Items.WHEAT_SEEDS, ModItems.CHALK.get(), ModItems.RED_CHALK.get()), FRAME, FILL)),
                Block.text("One gift buys one piece. Feed it again for the next."),
                Block.text("They are beasts as well as wonders, and those who hunt them come away with meat and hide. Whether that is worth the flowers they leave behind is a question the Faye never settled.")
        ));
    }

    private static Chapter theOtherSide()
    {
        return new Chapter("The Other Side", List.of(
                Block.text("When you die, you do not leave. Your body stays where it fell, holding everything you carried, and you remain beside it - unseen, unheard, drifting where you please and touching nothing."),
                Block.text("The living cannot see you at all. Neither can they see your hunters, nor anything else behind the veil. To them that ground is simply empty."),
                Block.text("You cannot break, build, lift or strike. You can only wait. After one full day the waiting ends badly: your body and everything in it crumbles, and you wake at your bed with nothing."),
                Block.text("A pet that dies waits in the same way, beside its own body, and waits forever. Nothing brings an animal back on its own."),
                Block.text("To fetch anyone back, someone living must come after them. That is the Rite, and it is forbidden work."),
                Block.text("Draw a ring entirely in Black Chalk and cut its heart with an Athame. Half your blood is the toll. A Ferryman rises where you stood, and you step out of your own body and across."),
                Block.figure(FayeFigure.circle(solid(RuneColor.BLACK), Component.literal("The Rite of Passage"))),
                Block.figure(FayeFigure.items(row(ModItems.BLACK_CHALK.get(), ModItems.ATHAME.get()), FRAME, FILL)),
                Block.text("You keep everything - your gear, your health, your hunger - because unlike the honest dead you can still be hurt over there, and you will need to be armed."),
                Block.text("Phantoms hunt whoever crosses by the Rite. The longer you linger the more of them come, and they come only for you; the honest dead they ignore. Daylight does not burn them."),
                Block.text("Lead the dead you came for to the Ferryman and have them touch him. They wake in their own body with half their strength. Any animal within fifteen paces of him comes home when you do."),
                Block.text("Touch him yourself and you are finished: he goes out in a puff of smoke and you return to the body you left. Do it last, or the way home closes on whoever is still out there."),
                Block.text("Die over there yourself and everything you carried is gone for good, as though you had fallen into lava. The Ferryman leaves without you."),
                Block.text("The Rite leaves a mark. Marked by the Dark, no heartstone will sing for you and no circle will answer. Swim ten unbroken seconds in Wellspring Water to wash it off.")
        ));
    }

    private static Chapter thisVeryBook()
    {
        return new Chapter("This Very Book", List.of(
                Block.text("What you hold is not paper and ink, nor any binding a mortal press ever made - the Faye's own script does not sit still on a flat page the way yours does."),
                Block.text("Sylvaine herself is said to have written it, in her own hand, into a shape closer to a living thing than a book - which is why, they say, it never truly closes, even resting on a shelf.")
        ));
    }

    /** Slot colors, kept here so the figures match the screen's own parchment. */
    static final int FRAME = 0xFF5A4A2E;
    static final int FILL = 0xFFD9CDA8;
}
