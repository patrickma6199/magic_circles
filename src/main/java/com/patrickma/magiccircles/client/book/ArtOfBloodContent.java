package com.patrickma.magiccircles.client.book;

import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.client.book.FayeBookContent.Block;
import com.patrickma.magiccircles.client.book.FayeBookContent.Chapter;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Art of Blood - every dark rite, and where they came from.
 *
 * <p>Shares the whole of {@code BookOfTheFayeScreen}'s machinery and only differs in binding (see
 * {@link BookStyle#BLOOD}) and in what it says. Still a fairy's book, written for other fairies -
 * but by one of the thinned bloodlines, who lost their song when Zuzo poured every line into her
 * seed and learned to take back what the Wellspring would no longer give them. Colder and more
 * bitter than the Book of the Faye, and not written to be read kindly.
 */
public final class ArtOfBloodContent
{
    private ArtOfBloodContent()
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

    /** Six Black and six of the partner colour - the shape every curse takes. */
    private static List<RuneColor> half(RuneColor partner)
    {
        List<RuneColor> ring = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            ring.add(i < 6 ? RuneColor.BLACK : partner);
        }
        return ring;
    }

    public static List<Chapter> chapters()
    {
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(whatThisIs());
        chapters.add(theBlade());
        chapters.add(theCurses());
        chapters.add(theSplitRings());
        chapters.add(theSummoning());
        return chapters;
    }

    private static Chapter whatThisIs()
    {
        return new Chapter("What This Is", List.of(
                Block.text("Sister, if you are reading this, then someone thought you were ready, or you stole it. Either way, read all of it before you touch a knife."),
                Block.text("The Wellspring gives. That is the whole of our magic and the whole of its limit: it can only ever hand you something. And when Zuzo poured the five lines into her seed, it gave everything it took from us to her."),
                Block.text("My line was thinned that day, as yours was. We sing one song each, in one stone each, and we call it enough because the queen tells us it is. Some of us learned otherwise."),
                Block.text("Every rite in this book is one of the Wellspring's own gifts, held backwards. Not a different power - the same power, with the sign turned over. Anyone who can give can learn to take."),
                Block.text("The Wellspring will not carry these rites. It refuses them the way a river refuses to run uphill. So they are carried in the only other thing that holds power and remembers whose it was. Blood. Hence the name, and hence the knife."),
                Block.text("Understand before you begin: the Wellspring knows. Work any of this and it will not sing for you again until its water has washed you clean - and that is the smallest of the prices."),
                Block.text("Every rite here takes half of whatever life you have left, and marks you. One time in four it also sets something in the sky to hunt you, where everyone can see what follows you home. None of that is punishment. It is only what taking costs."),
                Block.text("The queen sells this book. Think about why, before you are grateful.")
        ));
    }

    private static Chapter theBlade()
    {
        return new Chapter("The Athame", List.of(
                Block.text("An iron ingot over a stick - a thing the wingless make without a second thought. There is no art in the making. The art is in what it remembers."),
                Block.figure(FayeFigure.shaped(List.of(
                                stack(Items.IRON_INGOT), ItemStack.EMPTY, ItemStack.EMPTY,
                                stack(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY,
                                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY),
                        stack(ModItems.ATHAME.get()), FRAME, FILL)),
                Block.text("A clean blade does nothing. Work a rite with it, or draw it across your own palm, and it signs itself to you. Cut anyone else and it signs itself to them. It holds whoever bled on it last, and only the last."),
                Block.text("That signature is the whole of the Art. A curse does not choose whom it falls on; it follows the blood. To curse someone you must first come close enough to cut them - and that, not the price, is what stops most of us."),
                Block.text("Turn the blade in the light and it will tell you what it is holding shut, and on whom."),
                Block.text("So the knife is worth more than anything else you own, and everyone who knows what it is will want it off you."),
                Block.text("Rinse it in any water and it forgets. Everything it was holding comes undone in that same moment - every prisoner freed, wherever they are."),
                Block.figure(FayeFigure.items(row(ModItems.ATHAME.get(), Items.WATER_BUCKET), FRAME, FILL)),
                Block.text("Burn it, lose it down a ravine, let it lie on the ground until the world forgets it - the same. A curse lasts exactly as long as its blade, and not a breath longer."),
                Block.text("Nor any longer than its ring. Break a single rune of the circle it was worked in, or spoil its colours, and the curse comes apart there and then, wherever its victim stands. Guard the ring as you guard the knife.")
        ));
    }

    private static Chapter theCurses()
    {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.text("Six runes of black and six of one other colour, in any order. Set a heart in the middle if you like; the rite does not care. Cut the ring with a signed blade."));
        blocks.add(Block.text("Whoever's blood is on the knife takes the curse, wherever in any world they happen to be standing."));

        addCurse(blocks, RuneColor.PURPLE, "The Cell",
                "Korrin's wall, built inward. They are dragged to the ring from wherever they were and closed inside it. They cannot leave it, cannot break the circle, cannot dig their way out. Nothing they do matters - only the blade does, or someone outside willing to break the ring.");
        addCurse(blocks, RuneColor.BLUE, "The Stormcalled",
                "Zuzo's storm, given a name to follow. The sky finds them wherever it can see them. Open ground becomes a sentence, and a roof becomes a cell of its own. That it is the first queen's song we turn on them is lost on none of us.");
        addCurse(blocks, RuneColor.GOLD, "The Blight",
                "The Verdant Mother's bloom, rotting. Grass dies to dirt and flowers fall wherever they walk, and the same rot works inward on them. Their trail can be followed for weeks.");
        addCurse(blocks, RuneColor.RED, "Denied Mercy",
                "Sylvaine's mercy, withheld. Wounds simply stop closing. Food, potion, song, time - none of it answers. Whatever hurt them last is still hurting them.");
        addCurse(blocks, RuneColor.GREEN, "The Gleaning",
                "The Gleaner's fortune, gleaned from the living. Everything they learn bleeds out of them and comes to you instead. The only rite in this book that pays.");

        blocks.add(Block.text("Kill someone who carries one of these and they do not cross quietly. The curse goes with them and makes a poltergeist of them: a ghost whose touch still reaches doors, levers and chest lids, who can call rain down on the living and drag the dead up out of the ground. Their way home is the same as any ghost's."));
        blocks.add(Block.text("None of it cares whether its victim walks on two legs. A horse, a wolf, a villager, one of the wingless or one of us - blood is blood."));
        return new Chapter("The Curses", blocks);
    }

    private static void addCurse(List<Block> blocks, RuneColor partner, String name, String description)
    {
        blocks.add(Block.figure(FayeFigure.circle(half(partner), Component.literal(name))));
        blocks.add(Block.text(description));
    }

    /** Six black, then three each of two colours - the shape every split ring takes. */
    private static List<RuneColor> split(RuneColor first, RuneColor second)
    {
        List<RuneColor> ring = new ArrayList<>(Collections.nCopies(6, RuneColor.BLACK));
        ring.addAll(Collections.nCopies(3, first));
        ring.addAll(Collections.nCopies(3, second));
        return ring;
    }

    private static Chapter theSplitRings()
    {
        return new Chapter("The Split Rings", List.of(
                Block.text("Half the ring black, and the other half shared between two colours, three runes each. These are not curses. They are worked on yourself, and paid for the same way."),
                Block.figure(FayeFigure.circle(split(RuneColor.RED, RuneColor.GREEN), Component.literal("Deathsight"))),
                Block.text("Red and green - Sylvaine's mending and the Gleaner's fortune, both turned to looking. Cut it and your eyes open onto the other side without the rest of you following."),
                Block.text("You will see the dead, the things that hunt them, and whatever the Ferryman keeps, and you will hear both sides at once. You cannot touch them, and they cannot touch you. You are a window, not a door."),
                Block.text("It passes after three minutes or so. The Mark does not.")
        ));
    }

    private static Chapter theSummoning()
    {
        return new Chapter("The Door That Opens Wrong", List.of(
                Block.text("This is the last rite and the worst, and I write it down only because someone will find it again if I do not."),
                Block.text("One rune of every colour the Wellspring answers to - blue, gold, purple, red, green - and black for all the rest. The five lines, the whole of what Zuzo took from us, laid in a ring and drowned in the dark."),
                Block.figure(FayeFigure.circle(summoningRing(), Component.literal("The Summoning"))),
                Block.text("At its heart, a Heartstone brimming full: a thousand mana. The rite does not spend the stone, it eats it. Nothing is left after, not even the heart."),
                Block.text("Cut the heart itself. It will not refuse you. It refuses the Marked every other time, but here the Wellspring's will is weaker than yours, because you are willing to die for this and it is not."),
                Block.figure(FayeFigure.items(row(ModItems.HEARTSTONE.get(), ModItems.ATHAME.get()), FRAME, FILL)),
                Block.text("Cut it, and the Ferryman comes through - not on the other side, but here, in the living world where the stone was, plain to anyone who passes."),
                Block.text("He is a door that opens the wrong way. Touch him and you cross on the spot: you die where you stand, and your body keeps everything you carried, as in any other death."),
                Block.text("And in that same moment every soul still lost behind the veil is pulled back into its own body. All of them. Anywhere."),
                Block.text("It is the only way to empty the other side at once, and it costs one life to do. Once the price is paid he goes out like a snuffed candle, in a breath of smoke. The door opens once.")
        ));
    }

    /** One of each colour, black for the remaining seven. */
    private static List<RuneColor> summoningRing()
    {
        List<RuneColor> ring = new ArrayList<>(List.of(
                RuneColor.BLUE, RuneColor.GOLD, RuneColor.PURPLE, RuneColor.RED, RuneColor.GREEN));
        ring.addAll(Collections.nCopies(7, RuneColor.BLACK));
        return ring;
    }

    /** Slot colours matched to the torn, greyed paper this book is written on. */
    private static final int FRAME = 0xFF3A2430;
    private static final int FILL = 0xFFC4B9AC;
}
