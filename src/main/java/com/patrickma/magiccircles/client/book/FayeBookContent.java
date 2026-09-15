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
 * <p>It is the Faye's own book, written by fairies for fairies: their songs and their history, and
 * their craft taught the way one of them would teach another - with the wingless (whoever is
 * actually reading it) spoken of from the outside. The figures beside the prose are the practical
 * half: real item icons, real recipes, and the ring diagrams drawn as the footprint you would chalk.
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
        chapters.add(theThiefAndTheSeed());
        chapters.add(theRecalling());
        chapters.add(whatOurHandsMake());
        chapters.add(theSongsOfTheRings());
        chapters.add(songsInStone());
        chapters.add(theRoadsToYllumere());
        chapters.add(ourPeople());
        chapters.add(theQueenAndHerCourt());
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
                Block.text("Sit close, little one, and I will sing it to you the way it was sung to me, and to the one who sang it to me, back and back to the first of us who ever had wings."),
                Block.text("Once the Faye lived beneath open sky, in the world the wingless call the overworld, and all our magic rose out of the Wellspring - a river of silver water that ran through that world like a vein of song."),
                Block.text("The Wellspring was the last of the Font of Making, spilled when the Worldsmiths - the outer gods, who stand beyond every sky - shaped the worlds out of the formless dark. Where it ran, things grew that could grow nowhere else: the Wellwisps, the pale naiads of its banks, and us."),
                Block.text("We were five peoples then, not one. Each bloodline carried one colour of the Wellspring's song. Zuzo's line sang storms, in blue. Korrin's wardens sang walls, in purple. The Verdant Mother's line sang things into flower, in gold. Sylvaine's menders sang wounds closed, in red. And the Gleaner's quiet folk sang fortune out of whatever others left behind, in green."),
                Block.text("Those are the colours of our runes to this day. When you chalk a ring, you are chalking the old bloodlines, one rune at a time.")
        ));
    }

    private static Chapter theThiefAndTheSeed()
    {
        return new Chapter("The Thief and the Seed", List.of(
                Block.text("Zuzo loved someone she should not have. He was a demigod - half of the outer gods' own making - and the songs do not keep his name. We call him the Thief, and we sing him kindly, because he earned it."),
                Block.text("For her he climbed past the last sky and stole from the Worldsmiths the one thing they never meant to share: a seed of the Font of Making itself. The very power that made the world above, closed up small enough to carry in two hands."),
                Block.text("He brought it to her, and he did not get away. The outer gods found him and pulled his soul apart, thread by thread, until there was nothing of him left to find. Some say the black wisp that circles every queen is the last of him. Some say it is only her grief. Both may be true."),
                Block.text("Zuzo did not waste what he died for. She called the five bloodlines together and asked each of them to give the seed its song - blue and purple, gold and red and green - and every line gave, and the seed drank it all."),
                Block.text("What woke in her hands woke in her too. She rose from it the first queen of all the Faye, carrying every colour at once - the only one of us who ever has, save the queens who have come after her."),
                Block.text("The giving cost the rest of us. Our lines were thinned by it and have never filled again. That is why each of us carries a single song in a single stone, and why all of us look to the queen: for her strength, for her shelter, and for the lightning that answers anyone who harms us in her sight.")
        ));
    }

    private static Chapter theRecalling()
    {
        return new Chapter("The Recalling, and Yllumere", List.of(
                Block.text("A theft like that is not forgiven. Zuzo felt the judgement coming before anyone else - a silence where the Wellspring's song should have been - and knew the outer gods were coming for what was taken."),
                Block.text("She did not wait to learn what they meant to do. Out past the edge of everything she wove a shard of unmade space, in haste, and called her people to carry what silver water they could into it."),
                Block.text("Then the Recalling came. The Worldsmiths did not break the world above, as we feared they would. They only reached into it and drew every drop of the Wellspring back out. The overworld was left whole, and never knew what it had lost."),
                Block.text("But the seed they did not find. Zuzo carried it through with her, and in the middle of the saved water she planted it."),
                Block.text("The shard she shaped in her own image: its meadows and rivers, its sky full of light, and the Living Wood of its groves, pale and sweet, which is her wood and grows nowhere else. That is why all of Yllumere feels like her. It is her."),
                Block.text("The tree she did not shape. It grew out of the seed by itself, and it is the seed's, not hers: its heart is the same oak and dark oak and spruce the world above is made of, because it was made by the same power that made the world above. That is why we call it the World Tree, and why its wood is like no other wood in Yllumere."),
                Block.text("It is the tree that holds the shard together. Everything here - the islands, the water, the light, us - lives on the tree, and the tree lives on the Wellspring pooled at its foot. The wingless found our door long after and called our home the Fairy Realm. We call it Yllumere: the Sheltered Water."),
                Block.text("Look at the bark around the great arch of the court and you will see her wood creeping into the seed's, vein by vein - Zuzo's making and the Thief's gift, growing into one another a little more with every age. The eldest say that is how it was always meant to be.")
        ));
    }

    private static Chapter whatOurHandsMake()
    {
        return new Chapter("What Our Hands Make", List.of(
                Block.text("Every working begins with Arcane Dust. Grind the red dust of the world above with the sun-dust of its deep places - redstone and glowstone - and you will have two pinches of it."),
                Block.figure(FayeFigure.shapeless(row(Items.REDSTONE, Items.GLOWSTONE_DUST), stack(ModItems.ARCANE_DUST.get()), FRAME, FILL)),
                Block.text("Arcane Dust with blue dye, or with lapis whole, makes Blue Chalk. A stick of it draws two hundred runes before it is worn away to nothing."),
                Block.figure(FayeFigure.shapeless(row(ModItems.ARCANE_DUST.get(), Items.BLUE_DYE), stack(ModItems.CHALK.get()), FRAME, FILL)),
                Block.text("Trade the blue for a nugget of gold and the chalk comes out Gold."),
                Block.figure(FayeFigure.shapeless(row(ModItems.ARCANE_DUST.get(), Items.GOLD_NUGGET), stack(ModItems.GOLD_CHALK.get()), FRAME, FILL)),
                Block.text("A stick of Blue Chalk will take other colours after it is made. Rub it with Thelia Dust and it greens; rub it with coal or charcoal and it blackens. A tinted stick is only as long as it was - what is worn away stays worn."),
                Block.figure(FayeFigure.shapeless(row(ModItems.CHALK.get(), ModItems.THELIA_DUST.get()), stack(ModItems.GREEN_CHALK.get()), FRAME, FILL)),
                Block.figure(FayeFigure.shapeless(row(ModItems.CHALK.get(), Items.COAL), stack(ModItems.BLACK_CHALK.get()), FRAME, FILL)),
                Block.text("Thelia Dust is Living Wood fired in a furnace. Cut the logs from Zuzo's pale groves out across the islands - never from the World Tree, which is not ours to burn."),
                Block.text("Red Chalk no hand makes. A Dream Elk gives it, and only once it has eaten; the chapter on the elk tells how."),
                Block.text("Purple Chalk only we can make, and we do not say how. The wingless must buy it from us, and they do - for redstone."),
                Block.text("Down in the world above, at the depths where coal lies, there is a pink stone full of bones: Fairy Fossil Ore. Those bones are ours - the ones who never reached the shard - and we do not grudge the wingless what they give. A stone pick will open it; a wooden one will not."),
                Block.text("Nineteen stones in twenty give only a bone. About one in twenty-five gives a Fairy Horn, and one in a hundred a Heartstone still holding five hundred mana, as if its keeper had only just set it down."),
                Block.figure(FayeFigure.items(row(ModItems.FAIRY_FOSSIL_ORE.get(), Items.BONE, ModItems.FAIRY_HORN.get(), ModItems.HEARTSTONE.get()), FRAME, FILL)),
                Block.text("The Horn is the key to every ring. It does nothing in the hand; touch it to a finished circle and the circle wakes."),
                Block.text("An Athame - an iron ingot above a stick - cuts nothing but the veil and whoever stands beside it. Keep it far from black chalk, unless you mean what that means."),
                Block.figure(FayeFigure.shaped(grid(Items.IRON_INGOT, null, null, Items.STICK, null, null, null, null, null),
                        stack(ModItems.ATHAME.get()), FRAME, FILL))
        ));
    }

    private static Chapter theSongsOfTheRings()
    {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.text("A ring is twelve runes chalked around a single Heart Core. Touch it with a Fairy Horn and it sings the song of whichever bloodlines it holds - one colour alone, or two colours, six and six."));
        blocks.add(Block.text("Where the runes sit never matters. Only how many of each colour the ring holds."));

        addSong(blocks, "Zuzo's Wrathful Downpour", solid(RuneColor.BLUE),
                "All blue, a hundred mana. The first queen's own storm rages for half a minute, and its lightning finds whatever means harm outside the circle.");
        addSong(blocks, "Korrin's Unbroken Ward", solid(RuneColor.PURPLE),
                "All purple. Korrin's wardens raise walls nothing can break, and every blow the walls take costs the heart a mana. Purple wisps walk their true edge.");
        addSong(blocks, "The Verdant Mother's Blessing", solid(RuneColor.GOLD),
                "All gold, thirty mana. Wildflowers open across the grass all around the ring.");
        addSong(blocks, "Sylvaine's Mercy", solid(RuneColor.RED),
                "All red, sixty mana. Every friendly creature near is mended whole.");
        addSong(blocks, "The Gleaner's Fortune", solid(RuneColor.GREEN),
                "All green, forty mana. What the Gleaner gathered scatters free as loose experience.");
        addSong(blocks, "Zuzo and Sylvaine's Absolution", split(RuneColor.BLUE, RuneColor.RED),
                "Half blue, half red, fifty mana. A rain that washes poison and fire from every creature near.");
        addSong(blocks, "Korrin and Zuzo's Last Stand", split(RuneColor.BLUE, RuneColor.PURPLE),
                "Half blue, half purple, a hundred and twenty mana. Walls rise for three quarters of a minute, and lightning strikes whatever comes at them.");
        addSong(blocks, "Korrin and the Gleaner's Bargain", split(RuneColor.PURPLE, RuneColor.GREEN),
                "Half purple, half green, ten mana to wake it. For a minute the heart draws mana out of the air itself - fifty every few breaths, a thousand in all, enough to fill any heart from empty. No stone can carry this song.");
        addSong(blocks, "Verdant Mother and Sylvaine's Bloom", split(RuneColor.GOLD, RuneColor.RED),
                "Half gold, half red, eighty mana. Flowers open, wounds close, and a vigour lingers after.");
        addSong(blocks, "Verdant Mother and Gleaner's Harvest", split(RuneColor.GOLD, RuneColor.GREEN),
                "Half gold, half green, fifty mana. Every crop near ripens, and a little fortune is gleaned besides.");
        addSong(blocks, "Sylvaine and the Gleaner's Vigor", split(RuneColor.RED, RuneColor.GREEN),
                "Half red, half green. Fifty mana a minute for as long as it holds, and vigour for everyone who stands with you.");

        blocks.add(Block.text("Zuzo's Crossing is no ring of colours but a pattern: redstone at the corners, blue and gold between. Five hundred mana. It is the door she wove to bring us here, and it opens still."));
        blocks.add(Block.figure(FayeFigure.pattern(portalCells(), Component.literal("Zuzo's Crossing"))));
        blocks.add(Block.text("It deepens the water around the heart. Swim down and touch the bottom of it, and you wake on the other side."));
        blocks.add(Block.text("Some pairings do not sing at all. Blue with gold. Blue with green. Gold with purple. Purple with red. Perhaps those songs were lost in the giving, when the seed drank us thin. Perhaps no two lines ever learned them together."));
        return new Chapter("The Songs of the Rings", blocks);
    }

    private static void addSong(List<Block> blocks, String name, List<RuneColor> ring, String description)
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

    private static Chapter songsInStone()
    {
        return new Chapter("Songs Carried in Stone", List.of(
                Block.text("A song need not stay in its ring. Held in a Heartstone, it goes wherever you go - it is how each of us carries our one song. We are born knowing how. The wingless must first be Blessed, by eating of a Mana Wyrm: a minute raw, five cooked."),
                Block.figure(FayeFigure.items(row(ModItems.RAW_MANA_WYRM.get(), ModItems.COOKED_MANA_WYRM.get(), ModItems.HEARTSTONE.get()), FRAME, FILL)),
                Block.text("Stand before a heart whose ring is whole and quiet, with nothing in your hands, and touch it. Its wisps pour back into the heart; then heart and ring are gone, and in your hand is a Heartstone that remembers that song."),
                Block.text("Use the stone and it sings around you rather than around any ring. A lasting song holds for twenty seconds - and if you use the stone again while it holds, it falls silent early."),
                Block.text("Three white wisps circle you while a lasting song holds. The song's own colours fly from the stone."),
                Block.text("Korrin's walls, sung from a stone, close tight about whoever sings them, as ours do about us when we are struck: the wings open and lift them a length into the air, and there they hang in the ward's heart while it throws everything else out and turns every blow and bolt from outside, for twenty seconds. The walls cost nothing to raise: the stone pays for them only as they are struck, a mana for every point of harm they turn aside, and they fall if it runs dry."),
                Block.text("A stone remembers one song only. You may pour mana into it and it keeps its song, but it will not learn another until it is Cleansed."),
                Block.text("To Cleanse it, kneel at the Wellspring's edge with the stone in your hand and touch the water. The old song washes out of it and the stone is blank again.")
        ));
    }

    private static Chapter theRoadsToYllumere()
    {
        return new Chapter("The Roads to Yllumere", List.of(
                Block.text("The wingless come to us by Zuzo's Crossing: sing the pattern, swim down through the deepened water until they touch its bottom, and wake in Yllumere. Going home is the same dive the other way. Whoever opened a door comes back through their own; whoever followed them lands at some other open one."),
                Block.text("Even the Marked can open the Crossing. It is the one door the dark does not shut, and it must not - the Wellspring is the only thing that washes the Mark away."),
                Block.text("There is a second road, and it runs one way. Let an Ender Pearl fall into Wellspring water and leave it there: the wisps will come for it and circle tighter for three long seconds, and leave behind a Lost Waystone."),
                Block.figure(FayeFigure.items(row(Items.ENDER_PEARL, ModItems.LOST_WAYSTONE.get()), FRAME, FILL)),
                Block.text("Use a waystone anywhere and you are pulled here at once, and the stone is spent in the carrying. Use one here and it tears you back out - through your own door if it still stands open, and otherwise to wherever you last slept. The wise keep one for the day the pool goes dark behind them."),
                Block.text("Or sing the Crossing from this side. Sung anywhere in Yllumere, it opens the way home, not another way in. And in the ruined chamber where the Crossing comes up, a heart waits on its pedestal, full, its ring chalked in blue and gold - all but the redstone at its corners. Whoever is left here when the pool goes dark need only lay the redstone and wake it."),
                Block.text("In the middle of Yllumere stands the World Tree, and at its foot the Wellspring pools in a well with a stair cut down into it. It is the last of the silver water in all the worlds."),
                Block.text("From the well it runs out across the islands in rivers, and the Wellwisps drift thickest over them."),
                Block.text("Mana Wyrms glide in the deep sea under the island, close beneath the Ancient Heartstone - thick as minnows, and however many are taken, the deep is never empty for long - and nowhere else, until one of them is killed down there. Then the rest scatter, and from that day a few are born in the rivers and pools out across the islands as well. Only a few: out there they are too far from the prime heart to thrive."),
                Block.text("Nothing drowns in the Wellspring. A creature that goes under it and cannot breathe it is never hurt - the water simply takes it, there and then, and a wyrm swims away where it was. The eldest say that is where every wyrm first came from."),
                Block.text("Glitter Weed grows up tall from the floor of the deep sea, and the wyrms love to hide in it."),
                Block.text("Pixies drift about the branches, and Dream Elk walk the islands with antlers taller than they are.")
        ));
    }

    private static Chapter ourPeople()
    {
        return new Chapter("Our People", List.of(
                Block.text("We live in the World Tree, fewer than the songs remember. You know our shape: like the wingless, but never without our wings, each of us with a Heartstone in hand that remembers one song - walls and storms for the dark things, mending for the hurt, washing for the poisoned and the burning, flowers when we are at ease, and a little fortune for anyone kind enough to come near."),
                Block.text("We spend our days on the wing among the branches, and now and then fly a whole lap of the tree for the joy of it. But every half minute or so one of us comes down to sit on a branch a while. Tell the wingless: that is when to speak with us."),
                Block.text("We trade with them, and only for redstone. Yllumere has none, and every door we open to another world drinks it."),
                Block.text("Each of us has exactly one thing to sell, and no more, so whoever wants something must ask around. Purple Chalk is common among us, and cooked wyrm a little less so. Only a few of us part with Arcane Dust, and a living wyrm, a full Heartstone, or this very book are rare and dear. The Art of Blood no fairy sells - only the queen, and for a fortune."),
                Block.figure(FayeFigure.items(row(Items.REDSTONE, ModItems.PURPLE_CHALK.get(), ModItems.COOKED_MANA_WYRM.get(), ModItems.HEARTSTONE.get()), FRAME, FILL)),
                Block.text("Strike one of us and we close ourselves in a ward for twenty seconds that throws everything else out, and loose ten purple wisps that hunt the striker down like arrows - walls stop them, and so do their own wards - and then we fly. We do not trade with anyone who has struck us."),
                Block.text("When one of us falls, the soul does not linger where the body lies, as the wingless do. It goes to the court and waits in line along the north wall for the queen. She never lets more than six wait: when a seventh comes, she breathes life back into whichever of them has waited longest."),
                Block.text("The pixies are our small companions, as dogs are to the wingless. They are harmless and easily startled, and they flee rather than fight. They love the queen, and two or three drift along beside her wherever she goes. Taken far from the Wellspring's mana, they sicken and die.")
        ));
    }

    private static Chapter theQueenAndHerCourt()
    {
        return new Chapter("The Queen and Her Court", List.of(
                Block.text("There is always one queen, and never two. When she falls, a fairy woman steps up to the crown - the seed's song finds her - and she takes a name with it. Zuzo, Queen of the Faye, was the first; every queen since has carried her title."),
                Block.text("Every colour of wisp circles her, each on its own path, and the dark rite's black among them. She alone of us holds all the lines at once, and she alone has found a balance between the Wellspring and the dark."),
                Block.text("Raise a hand against her, or against any of us in her sight, and the sky darkens to rain and answers with lightning until you are dead. She wards herself as we do, but she does not flee. She stays, and hunts you with her own wisps."),
                Block.text("Her court is carved into the heartwood of the tree, its great arch opening onto the eastern sky. She holds court one whole day and roams the next, turn and turn about, and on her court days she sits her throne from dawn to dawn."),
                Block.text("While she sits, her people come to her, a few at a time. Three stand on the floor beside the runner. The rest fly up into the heart of the tree above her, to ledges cut into the wood beneath the crown of shroomlight, and listen from there."),
                Block.text("She has walked both sides of the veil. She sees the dead and speaks with them, and now and then she calls a lost soul to her from wherever it wanders, to offer it a bargain: its body back, and a debt to her that is not yet called in."),
                Block.text("A fallen queen never waits behind the veil as the rest of us do. She has passed through it once already, and she goes straight home to the Wellspring - and all of Yllumere weeps rain for her.")
        ));
    }

    private static Chapter theBlessing()
    {
        return new Chapter("The Blessing of the Wellspring", List.of(
                Block.text("A wingless one who eats of a Mana Wyrm is marked kindly by the Wellspring for a while - a minute raw, five cooked - and while it holds, they are closer to us than to their own kind. A wyrm can always be eaten, however full the belly, and each one adds to what is left, as far as two moons."),
                Block.text("Wisps of the Wellspring's five colours circle the Blessed, each on its own tilted path, the way they circle the queen - all but the dark one, which is hers, and the Marked's. It is only a little of what she carries. It is enough."),
                Block.text("The Blessed have wings. Leap twice, quickly, or simply fall five lengths, and ours open on their backs. Leap again in flight to fold them, or come down on anything solid."),
                Block.text("They go wherever they look - up as easily as down, with nothing pulling them earthward. Leaning forward, they go faster. No fall and no wall will hurt them while the Blessing lasts."),
                Block.text("Leaning back, they slow to a stop and hang in the air on beating wings, the way we hover, for as long as they please."),
                Block.text("The Blessed breathe the Wellspring as we do, and see through it as clearly as through air."),
                Block.text("And while they swim in it, the Wellspring tops their Blessing back up - as far as two moons, two whole days and nights, and no further. But only while they still carry it: let it lapse, and the water will not give it back. They must eat again."),
                Block.text("The Blessing is also what lets them take a song into a Heartstone, and sing from it after.")
        ));
    }

    private static Chapter theAncientHeartstone()
    {
        return new Chapter("The Ancient Heartstone", List.of(
                Block.text("Above the Wellspring hangs a heart no ring summoned and no fairy ever commanded - gold, where every other heart burns blue-white, and nothing in any world can break it."),
                Block.text("We call it the Ancient Heartstone. The eldest say it hung there before the first branch; that Zuzo set it there to hold the edge of the shard; and that whatever hunts in the space between worlds cannot cross while it still burns."),
                Block.text("Wisps of every colour circle it, two of each, every one on its own tilted path, the way they circle the queen. It is the prime heart, the first of them all, and every other heart and every wyrm in the deep draws on it - which is why the wyrms thin out the farther they stray from it.")
        ));
    }

    private static Chapter theDreamElk()
    {
        return new Chapter("The Dream Elk", List.of(
                Block.text("They walk the islands on long legs, antlers branching higher than their shoulders, and flowers open in the grass wherever they go. Now and then one simply leaps - four lengths into the air and eight across - for no reason any of us has found but gladness."),
                Block.text("The wingless tame them the way they tame horses: mount, be thrown, mount again, until the elk stops throwing them. Saddled, it will carry them."),
                Block.text("Ridden, it leaps just as it does when it is free. Let it gather the leap long enough and it clears four lengths upward - and eight forward, if its rider presses on."),
                Block.text("Offer one a pinch of Arcane Dust and it will eat it, grumble about it for a few moments, and pass a fresh stick of Red Chalk out behind it. One pinch, one stick; feed it again for the next."),
                Block.figure(FayeFigure.items(row(ModItems.ARCANE_DUST.get(), ModItems.RED_CHALK.get()), FRAME, FILL)),
                Block.text("They are beasts as well as wonders, and those who hunt them take meat and hide. Whether that is worth the flowers is a question we have never settled.")
        ));
    }

    private static Chapter theOtherSide()
    {
        return new Chapter("The Other Side", List.of(
                Block.text("When the wingless die, they do not leave. The body stays where it fell, holding everything they carried, and they stay beside it - unseen, unheard, drifting where they please and touching nothing."),
                Block.text("The living cannot see them, nor the things that hunt them, nor anything else behind the veil. To the living, that ground is only empty."),
                Block.text("They cannot break or build, lift or strike. They can only wait, and not for long: after half a moon - ten short minutes - the waiting ends badly. The body and everything in it crumbles, and they wake in their bed with nothing."),
                Block.text("A pet that dies waits the same way, beside its own body, and waits forever. So does any villager, and anything that was ever given a name - a name is what makes something someone, and someone is what the veil keeps. Our own fallen go to the court instead, to wait for the queen."),
                Block.text("A zombie was someone once. Kill one, and now and then a villager steps free of it on the other side, waiting to be led home."),
                Block.text("Someone living can go after the dead. That is the Rite, and it is forbidden work."),
                Block.text("Chalk a ring all in black and cut its heart with an Athame. Half your blood is the toll. A Ferryman rises where you stood, and you step out of your body and across."),
                Block.figure(FayeFigure.circle(solid(RuneColor.BLACK), Component.literal("The Rite of Passage"))),
                Block.figure(FayeFigure.items(row(ModItems.BLACK_CHALK.get(), ModItems.ATHAME.get()), FRAME, FILL)),
                Block.text("Whoever crosses by the Rite keeps everything - gear, health, hunger - because unlike the honest dead they can still be hurt over there, and they will need to be armed."),
                Block.text("Phantoms hunt the Rite-crossed. The longer they linger the more of them come, and they come only for them; the honest dead are left alone. Daylight does not burn these."),
                Block.text("Lead the dead to the Ferryman and have them touch him, and they wake in their own bodies with half their strength. Any animal within fifteen paces of him comes home with whoever summoned him."),
                Block.text("The summoner touches him last. He goes out in a breath of smoke, and they return to the body they left. Touch him first, and the way home closes on whoever is still out there."),
                Block.text("Die over there and everything you carried is gone for good, as though into lava. The Ferryman leaves without you, and all the living hear is that your soul has rejoined the crucible and been reborn."),
                Block.text("The Rite leaves a mark. The Marked cannot make a heartstone sing or a ring answer; a single dark wisp circles them, as the black one circles the queen; and now and then they glimpse the Ferryman himself watching them - gone when they look again. Ten unbroken seconds swimming in the Wellspring washes the Mark away."),
                Block.text("Die with a curse on you and you come back wrong: a poltergeist, a ghost whose touch still reaches doors, chests and buttons, who can call the rain and now and then raise a skeleton out of the ground. The Ferryman brings a poltergeist home the same as anyone.")
        ));
    }

    private static Chapter thisVeryBook()
    {
        return new Chapter("This Very Book", List.of(
                Block.text("What you hold is not paper and ink. Sylvaine wrote it in her own hand, into a shape closer to a living thing than a book - which is why it never truly closes, even resting on a shelf, and why it lifts itself up to face whoever reads it."),
                Block.text("Now and then one of us sells a copy to the wingless, for a handful of redstone. Let them read it. It is still ours, every word, and it was written for us.")
        ));
    }

    /** Slot colors, kept here so the figures match the screen's own parchment. */
    static final int FRAME = 0xFF5A4A2E;
    static final int FILL = 0xFFD9CDA8;
}
