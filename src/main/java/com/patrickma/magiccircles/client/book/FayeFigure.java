package com.patrickma.magiccircles.client.book;

import com.patrickma.magiccircles.block.RuneColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.List;

/**
 * An illustration inside the Book of the Faye - a recipe, a row of items, or a circle diagram.
 * Each one knows how tall it is so {@code BookOfTheFayeScreen} can flow text and pictures together
 * onto a page without any hand-counted layout.
 *
 * <p>Everything is drawn from primitives and real {@link ItemStack} icons rather than from bespoke
 * artwork, so a figure always shows whatever the item actually looks like today - retexture an
 * item and its picture in the book follows on its own.
 */
public interface FayeFigure
{
    int SLOT = 18;
    int PAD = 3;

    int height();

    /** Draws centered within {@code [left, left + width)}, starting at {@code top}. */
    void render(GuiGraphics graphics, int left, int top, int width, Font font);

    // ------------------------------------------------------------------
    // Slot + item helpers
    // ------------------------------------------------------------------

    static void slot(GuiGraphics graphics, int x, int y, int frame, int fill)
    {
        graphics.fill(x, y, x + SLOT, y + SLOT, frame);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, fill);
    }

    static void item(GuiGraphics graphics, Font font, ItemStack stack, int x, int y)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }
        graphics.renderItem(stack, x + 1, y + 1);
        graphics.renderItemDecorations(font, stack, x + 1, y + 1);
    }

    // ------------------------------------------------------------------
    // Recipes
    // ------------------------------------------------------------------

    /** A 3x3 bench, an arrow, and what comes out. {@code grid} is nine entries, nulls for empty slots. */
    static FayeFigure shaped(List<ItemStack> grid, ItemStack result, int frame, int fill)
    {
        return new FayeFigure()
        {
            @Override
            public int height()
            {
                return SLOT * 3 + PAD * 2;
            }

            @Override
            public void render(GuiGraphics graphics, int left, int top, int width, Font font)
            {
                int gridW = SLOT * 3 + PAD * 2;
                int totalW = gridW + 16 + SLOT;
                int x = left + (width - totalW) / 2;

                for (int row = 0; row < 3; row++)
                {
                    for (int col = 0; col < 3; col++)
                    {
                        int sx = x + col * (SLOT + PAD);
                        int sy = top + row * (SLOT + PAD);
                        slot(graphics, sx, sy, frame, fill);
                        item(graphics, font, grid.get(row * 3 + col), sx, sy);
                    }
                }

                int midY = top + height() / 2 - 4;
                graphics.drawString(font, "→", x + gridW + 4, midY, frame, false);

                int rx = x + gridW + 16;
                int ry = top + height() / 2 - SLOT / 2;
                slot(graphics, rx, ry, frame, fill);
                item(graphics, font, result, rx, ry);
            }
        };
    }

    /** Ingredients in a row with plus signs between them - what a shapeless recipe actually asks for. */
    static FayeFigure shapeless(List<ItemStack> ingredients, ItemStack result, int frame, int fill)
    {
        return new FayeFigure()
        {
            @Override
            public int height()
            {
                return SLOT;
            }

            @Override
            public void render(GuiGraphics graphics, int left, int top, int width, Font font)
            {
                int gap = 11;
                int totalW = ingredients.size() * SLOT + (ingredients.size() - 1) * gap + 16 + SLOT;
                int x = left + (width - totalW) / 2;

                for (int i = 0; i < ingredients.size(); i++)
                {
                    slot(graphics, x, top, frame, fill);
                    item(graphics, font, ingredients.get(i), x, top);
                    x += SLOT;
                    if (i < ingredients.size() - 1)
                    {
                        graphics.drawString(font, "+", x + 3, top + 5, frame, false);
                        x += gap;
                    }
                }
                graphics.drawString(font, "→", x + 4, top + 5, frame, false);
                x += 16;
                slot(graphics, x, top, frame, fill);
                item(graphics, font, result, x, top);
            }
        };
    }

    /** A labelled row of items - "this is what these look like". */
    static FayeFigure items(List<ItemStack> stacks, int frame, int fill)
    {
        return new FayeFigure()
        {
            @Override
            public int height()
            {
                return SLOT + 11;
            }

            @Override
            public void render(GuiGraphics graphics, int left, int top, int width, Font font)
            {
                int gap = 8;
                int totalW = stacks.size() * SLOT + (stacks.size() - 1) * gap;
                int x = left + (width - totalW) / 2;
                for (ItemStack stack : stacks)
                {
                    slot(graphics, x, top, frame, fill);
                    item(graphics, font, stack, x, top);
                    x += SLOT + gap;
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Circle diagrams
    // ------------------------------------------------------------------

    /**
     * The ring as you would actually chalk it: a 5x5 footprint whose twelve edge cells are the
     * runes and whose middle is the Heart Core. {@code ring} is those twelve, read clockwise from
     * the top-left of the top row - null anywhere means "leave that cell bare".
     */
    int CELL = 13;
    int CELL_GAP = 2;
    /** Marker for the middle of a diagram - the Heart Core rather than a chalked rune. */
    int HEART = -1;

    /**
     * A 5x5 footprint, one entry per cell read left-to-right and top-to-bottom: {@code 0} for bare
     * ground, {@link #HEART} for the Heart Core, or a packed {@code 0xRRGGBB} for a chalked rune.
     * Both the ring spells and the portal are drawn through this, since the portal chalks its
     * corners too and a ring never does.
     */
    static FayeFigure pattern(int[] cells, Component caption)
    {
        return new FayeFigure()
        {
            @Override
            public int height()
            {
                return CELL * 5 + CELL_GAP * 4 + (caption == null ? 0 : 12);
            }

            @Override
            public void render(GuiGraphics graphics, int left, int top, int width, Font font)
            {
                int size = CELL * 5 + CELL_GAP * 4;
                int x0 = left + (width - size) / 2;

                for (int row = 0; row < 5; row++)
                {
                    for (int col = 0; col < 5; col++)
                    {
                        int cx = x0 + col * (CELL + CELL_GAP);
                        int cy = top + row * (CELL + CELL_GAP);
                        int cell = cells[row * 5 + col];

                        if (cell == HEART)
                        {
                            graphics.fill(cx, cy, cx + CELL, cy + CELL, 0xFF2B2118);
                            graphics.fill(cx + 3, cy + 3, cx + CELL - 3, cy + CELL - 3, 0xFFE8D48A);
                        }
                        else if (cell == 0)
                        {
                            graphics.fill(cx + 5, cy + 5, cx + CELL - 5, cy + CELL - 5, 0x22000000);
                        }
                        else
                        {
                            graphics.fill(cx, cy, cx + CELL, cy + CELL, 0xFF000000 | darken(cell));
                            graphics.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, 0xFF000000 | cell);
                        }
                    }
                }

                if (caption != null)
                {
                    int cw = font.width(caption);
                    graphics.drawString(font, caption, left + (width - cw) / 2, top + size + 3, 0xFF4A3A22, false);
                }
            }
        };
    }

    /**
     * The ring as you would actually chalk it - the twelve edge cells (corners bare) around a
     * Heart Core, read left-to-right along each row from the top.
     */
    static FayeFigure circle(List<RuneColor> ring, Component caption)
    {
        int[] cells = new int[25];
        int index = 0;
        for (int row = 0; row < 5; row++)
        {
            for (int col = 0; col < 5; col++)
            {
                int i = row * 5 + col;
                if (row == 2 && col == 2)
                {
                    cells[i] = HEART;
                }
                else if (isRingCell(row, col))
                {
                    RuneColor color = index < ring.size() ? ring.get(index) : null;
                    index++;
                    cells[i] = color == null ? 0 : packed(color.wispColor());
                }
            }
        }
        return pattern(cells, caption);
    }

    /** The twelve chalked cells of a 5x5 ring - everything on the edge except the four corners. */
    static boolean isRingCell(int row, int col)
    {
        boolean edge = row == 0 || row == 4 || col == 0 || col == 4;
        boolean corner = (row == 0 || row == 4) && (col == 0 || col == 4);
        return edge && !corner;
    }

    static int packed(Vector3f color)
    {
        int r = Math.round(Math.min(1.0f, color.x()) * 255.0f);
        int g = Math.round(Math.min(1.0f, color.y()) * 255.0f);
        int b = Math.round(Math.min(1.0f, color.z()) * 255.0f);
        return (r << 16) | (g << 8) | b;
    }

    static int darken(int rgb)
    {
        int r = ((rgb >> 16) & 0xFF) / 2;
        int g = ((rgb >> 8) & 0xFF) / 2;
        int b = (rgb & 0xFF) / 2;
        return (r << 16) | (g << 8) | b;
    }
}
