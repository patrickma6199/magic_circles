package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.client.book.BookOfTheFayeScreen;
import net.minecraft.client.Minecraft;

/**
 * The actual {@code Minecraft.getInstance().setScreen(...)} call for {@code
 * item/BookOfTheFayeItem#use} - split into its own class specifically so that call, and every
 * client-only type it touches ({@link Minecraft}, {@link BookViewScreen}), never appears in
 * {@code BookOfTheFayeItem}'s own bytecode at all.
 *
 * <p>This isn't optional cosmetic organization - Forge's {@code RuntimeDistCleaner} statically
 * scans every class's bytecode for {@code @OnlyIn(Dist.CLIENT)} references the moment that class
 * is *loaded*, regardless of whether the referencing method ever actually runs on this side (an
 * {@code if (level.isClientSide)} guard around the call doesn't help - the class still fails to
 * verify). {@code BookOfTheFayeItem} is a common class (registered on the mod bus, loaded on a
 * dedicated server too), so it can never directly reference this class's own two imports.
 * Routing the call through {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
 * BookOfTheFayeScreenOpener::open)} instead works because that method reference's own signature
 * (a plain, no-argument, {@code void}-returning method) is dist-clean - only *this* class, never
 * loaded at all on a dedicated server, ever needs to resolve {@link Minecraft}/{@link
 * BookViewScreen} for real.
 */
public final class BookOfTheFayeScreenOpener
{
    private BookOfTheFayeScreenOpener()
    {
    }

    /** {@code pos} is the placed book being read, told when the screen closes so it closes for everyone. */
    public static void open(net.minecraft.core.BlockPos pos)
    {
        Minecraft.getInstance().setScreen(BookOfTheFayeScreen.ofTheFaye().readingAt(pos));
    }

    /** The Art of Blood's own reading screen - same machinery, dark purple binding and torn pages. */
    public static void openArtOfBlood(net.minecraft.core.BlockPos pos)
    {
        Minecraft.getInstance().setScreen(BookOfTheFayeScreen.artOfBlood().readingAt(pos));
    }
}
