package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.block.RuneColor;

/**
 * The curses, one per colour paired six-and-six with Black.
 *
 * <p>Each is the Wellspring's own gift turned against its holder. The Faye's magic gives - shelter,
 * rain, bloom, mercy, fortune - and every rite here takes the same shape and reverses the sign of
 * it. That is what the Art of Blood is: not new power, but the Wellspring's power held backwards.
 */
public enum CurseKind
{
    /**
     * Black and Purple. Korrin's ward, built inward - the same unbreakable wall, with the victim
     * on the wrong side of it. Holds until the blade that laid it is clean or gone.
     */
    IMPRISONMENT(RuneColor.PURPLE, "imprisonment"),

    /**
     * Black and Blue. Zuzo's storm, given a name to follow - the sky itself keeps finding the
     * cursed wherever they stand under it.
     */
    STORMCALLED(RuneColor.BLUE, "stormcalled"),

    /**
     * Black and Gold. The Verdant Mother's bloom run backwards: everything green the victim walks
     * past dies, and the rot works inward on them too.
     */
    BLIGHT(RuneColor.GOLD, "blight"),

    /**
     * Black and Red. Sylvaine's mercy withheld - wounds simply stop closing, and whatever would
     * have healed them hurts instead.
     */
    DENIED_MERCY(RuneColor.RED, "denied_mercy"),

    /**
     * Black and Green. The Gleaner's fortune, gleaned from the living - the victim's own
     * experience bleeds out of them and finds whoever cursed them.
     */
    GLEANING(RuneColor.GREEN, "gleaning");

    private final RuneColor partner;
    private final String key;

    CurseKind(RuneColor partner, String key)
    {
        this.partner = partner;
        this.key = key;
    }

    /** The colour that must fill the other six runes of the ring. */
    public RuneColor partner()
    {
        return partner;
    }

    public String translationKey()
    {
        return "curse.magiccircles." + key;
    }

    public static CurseKind forPartner(RuneColor color)
    {
        for (CurseKind kind : values())
        {
            if (kind.partner == color)
            {
                return kind;
            }
        }
        return null;
    }
}
