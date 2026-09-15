package com.patrickma.magiccircles.client.book;

/**
 * How a book is bound. The Faye's own book is green on clean parchment; the Art of Blood is dark
 * purple on pages someone tore out of something else.
 *
 * @param borderDark  outermost rule of the binding
 * @param borderMid   the binding proper
 * @param borderLight the inner highlight, where light catches the edge
 * @param page        the page itself
 * @param ink         body text
 * @param heading     chapter titles and rules
 * @param faint       page numbers and captions
 * @param torn        whether the page edges are ragged rather than cut straight
 */
public record BookStyle(int borderDark, int borderMid, int borderLight, int page,
                        int ink, int heading, int faint, boolean torn)
{
    /** Green binding, clean parchment, straight edges. */
    public static final BookStyle FAYE = new BookStyle(
            0xFF16301C, 0xFF2F6B3A, 0xFF6BB877, 0xFFEDE4CB,
            0xFF3A2F1B, 0xFF1E5B2E, 0xFF8A7B58, false);

    /**
     * Dark purple, on paper gone grey and brown with age. Torn, because the Art of Blood was never
     * a book anyone bound on purpose - it is pages taken out of other books and kept together.
     */
    public static final BookStyle BLOOD = new BookStyle(
            0xFF17091F, 0xFF3D1457, 0xFF6A2E8F, 0xFFD8CFC0,
            0xFF2A2028, 0xFF5B1B3A, 0xFF7A6B70, true);
}
