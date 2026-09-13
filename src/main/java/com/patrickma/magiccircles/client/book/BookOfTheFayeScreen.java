package com.patrickma.magiccircles.client.book;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The Book of the Faye's own reading screen, in place of vanilla's {@code BookViewScreen}.
 *
 * <p>Vanilla's book is locked to its own 256x256 parchment texture and a text column barely a
 * hundred pixels wide, with no way to widen it or put a picture in it - which is why this is a
 * plain {@link Screen} drawn from primitives instead. That buys a page nearly three times as wide,
 * room for real item icons and ring diagrams beside the prose, and a green binding to match the
 * Faye rather than vanilla's brown.
 *
 * <p>Pages are not written by hand. {@link FayeBookContent} supplies chapters as runs of
 * paragraphs and figures, and {@link #paginate} flows them into whatever number of pages they
 * actually need at this font and width, starting each chapter on a fresh page. Adding a sentence
 * can never push a diagram onto the wrong page or silently clip the bottom of a chapter.
 */
public class BookOfTheFayeScreen extends Screen
{
    /** Unifont - a genuinely different hand from Minecraft's default, and still perfectly readable. */
    private static final Style BOOK_FONT = Style.EMPTY.withFont(new ResourceLocation("minecraft", "uniform"));

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 222;
    private static final int MARGIN = 18;
    private static final int LINE_HEIGHT = 10;
    private static final int PARAGRAPH_GAP = 5;

    /** Height of the chapter-title block: the title line, the rule under it, and the space around both. */
    private static final int TITLE_BLOCK = LINE_HEIGHT + 7;
    /** Vertical step between contents entries - shared by drawing and hit-testing so they cannot drift apart. */
    private static final int CONTENTS_STEP = LINE_HEIGHT + 3;

    private static final int BORDER_DARK = 0xFF16301C;
    private static final int BORDER_MID = 0xFF2F6B3A;
    private static final int BORDER_LIGHT = 0xFF6BB877;
    private static final int PARCHMENT = 0xFFEDE4CB;
    private static final int INK = 0xFF3A2F1B;
    private static final int HEADING = 0xFF1E5B2E;
    private static final int FAINT = 0xFF8A7B58;

    private final List<Page> pages = new ArrayList<>();
    private int index;
    private int left;
    private int top;

    public BookOfTheFayeScreen()
    {
        super(Component.literal("Book of the Faye"));
    }

    @Override
    protected void init()
    {
        left = (this.width - PANEL_WIDTH) / 2;
        top = (this.height - PANEL_HEIGHT) / 2;

        if (pages.isEmpty())
        {
            paginate();
        }

        int buttonY = top + PANEL_HEIGHT - 24;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(left + MARGIN, buttonY, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(left + PANEL_WIDTH - MARGIN - 22, buttonY, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Contents"), b -> jumpTo(0))
                .bounds(left + PANEL_WIDTH / 2 - 34, buttonY, 68, 16).build());
    }

    private void turn(int delta)
    {
        jumpTo(this.index + delta);
    }

    private void jumpTo(int target)
    {
        this.index = Math.max(0, Math.min(pages.size() - 1, target));
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** One rendered page: a chapter title (only on its first page) and the lines/figures under it. */
    private record Page(String chapter, boolean chapterStart, List<Object> items)
    {
    }

    private int textWidth()
    {
        return PANEL_WIDTH - MARGIN * 2;
    }

    private int bodyHeight()
    {
        return PANEL_HEIGHT - MARGIN * 2 - 34;
    }

    /**
     * Flows every chapter into pages. Items are either a wrapped line ({@link
     * FormattedCharSequence}) or a {@link FayeFigure}; a figure that will not fit in what is left
     * of a page moves whole to the next one rather than being split across the fold.
     */
    private void paginate()
    {
        pages.clear();
        pages.add(new Page("Contents", true, List.of()));

        for (FayeBookContent.Chapter chapter : FayeBookContent.chapters())
        {
            List<Object> current = new ArrayList<>();
            boolean firstPage = true;
            int used = 0;
            int available = bodyHeight() - LINE_HEIGHT - 6;

            for (FayeBookContent.Block block : chapter.blocks())
            {
                List<Object> pieces = new ArrayList<>();
                int needed;

                if (block.figure() != null)
                {
                    needed = block.figure().height() + PARAGRAPH_GAP * 2;
                    pieces.add(block.figure());
                }
                else
                {
                    List<FormattedCharSequence> lines =
                            this.font.split(block.text().copy().withStyle(BOOK_FONT), textWidth());
                    needed = lines.size() * LINE_HEIGHT + PARAGRAPH_GAP;
                    pieces.addAll(lines);
                }

                if (used + needed > available && !current.isEmpty())
                {
                    pages.add(new Page(chapter.title(), firstPage, current));
                    current = new ArrayList<>();
                    firstPage = false;
                    used = 0;
                    available = bodyHeight();
                }

                current.addAll(pieces);
                current.add(PARAGRAPH_BREAK);
                used += needed;
            }

            if (!current.isEmpty())
            {
                pages.add(new Page(chapter.title(), firstPage, current));
            }
        }
    }

    /** Marker object for the blank space between paragraphs. */
    private static final Object PARAGRAPH_BREAK = new Object();

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        drawFrame(graphics);

        Page page = pages.get(index);
        int y = top + MARGIN;

        if (page.chapterStart())
        {
            Component title = Component.literal(page.chapter()).withStyle(BOOK_FONT);
            int titleWidth = this.font.width(title);
            graphics.drawString(this.font, title, left + (PANEL_WIDTH - titleWidth) / 2, y, HEADING, false);
            graphics.fill(left + MARGIN + 30, y + LINE_HEIGHT + 2,
                    left + PANEL_WIDTH - MARGIN - 30, y + LINE_HEIGHT + 3, BORDER_MID);
            y += TITLE_BLOCK;
        }

        if (index == 0)
        {
            renderContents(graphics, y);
        }
        else
        {
            renderBody(graphics, page, y);
        }

        Component footer = Component.literal((index + 1) + " / " + pages.size()).withStyle(BOOK_FONT);
        graphics.drawString(this.font, footer,
                left + PANEL_WIDTH / 2 - this.font.width(footer) / 2, top + PANEL_HEIGHT - 36, FAINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBody(GuiGraphics graphics, Page page, int startY)
    {
        int y = startY;
        for (Object item : page.items())
        {
            if (item == PARAGRAPH_BREAK)
            {
                y += PARAGRAPH_GAP;
            }
            else if (item instanceof FormattedCharSequence line)
            {
                graphics.drawString(this.font, line, left + MARGIN, y, INK, false);
                y += LINE_HEIGHT;
            }
            else if (item instanceof FayeFigure figure)
            {
                y += PARAGRAPH_GAP;
                figure.render(graphics, left + MARGIN, y, textWidth(), this.font);
                y += figure.height() + PARAGRAPH_GAP;
            }
        }
    }

    /** The contents page - every chapter and the page it starts on, clickable. */
    private void renderContents(GuiGraphics graphics, int startY)
    {
        int y = startY;
        for (int i = 1; i < pages.size(); i++)
        {
            Page page = pages.get(i);
            if (!page.chapterStart())
            {
                continue;
            }
            Component entry = Component.literal(page.chapter()).withStyle(BOOK_FONT);
            Component number = Component.literal(String.valueOf(i + 1)).withStyle(BOOK_FONT);
            graphics.drawString(this.font, entry, left + MARGIN + 6, y, INK, false);
            graphics.drawString(this.font, number,
                    left + PANEL_WIDTH - MARGIN - 6 - this.font.width(number), y, FAINT, false);
            y += CONTENTS_STEP;
        }
    }

    /** Where the first contents entry sits - the one place both drawing and clicking agree on. */
    private int contentsTop()
    {
        return top + MARGIN + TITLE_BLOCK;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (index == 0 && button == 0)
        {
            int y = contentsTop();
            for (int i = 1; i < pages.size(); i++)
            {
                if (!pages.get(i).chapterStart())
                {
                    continue;
                }
                if (mouseY >= y && mouseY < y + CONTENTS_STEP
                        && mouseX >= left + MARGIN && mouseX <= left + PANEL_WIDTH - MARGIN)
                {
                    jumpTo(i);
                    return true;
                }
                y += CONTENTS_STEP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The green binding: three nested rules around the parchment. */
    private void drawFrame(GuiGraphics graphics)
    {
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        graphics.fill(left - 4, top - 4, right + 4, bottom + 4, BORDER_DARK);
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, BORDER_MID);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER_LIGHT);
        graphics.fill(left, top, right, bottom, PARCHMENT);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
