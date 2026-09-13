package com.patrickma.magiccircles.ritual;

import com.patrickma.magiccircles.block.RuneColor;
import org.jetbrains.annotations.Nullable;

/**
 * Every spell a Heart Core can cast, and what it costs - see {@link MagicCircleRitual#detectSpell}
 * for how a ring's color composition maps to one of these. Five are solo spells, one per
 * {@link RuneColor} ({@link #soloFor}); the other six are combo spells cast by splitting the
 * ring exactly 6-and-6 between two colors ({@link #comboFor}) - of the 10 possible color pairs,
 * only 6 are wired to a spell so far, chosen where a color pair's two solo themes suggested a
 * natural combined effect (e.g. Gold's growth + Red's vitality = Bloom of Life). The other 4
 * pairs (Blue+Gold, Blue+Green, Gold+Purple, Purple+Red) are real, valid 6-6 splits that simply
 * aren't recognized yet - {@link #comboFor} returns {@code null} for them, same as any other
 * unrecognized ring.
 *
 * <p>{@link #manaCost()} is charged upfront for every spell here except {@link #SHIELD},
 * {@link #MANA_FONT}, and {@link #VITAL_SURGE}, which have their own ongoing mana logic in
 * {@code HeartCoreBlockEntity} (the listed cost for those three is just the minimum needed to
 * bother starting, not an upfront charge) - see that class for what each spell actually does.
 * Opening a portal to the Fairy Realm is a spell too (500 mana) but isn't one of these - it's
 * matched by an entirely different pattern ({@link PortalRitual}, not a ring color), so
 * {@code HeartCoreBlock#interact} checks for it separately, before ever looking at a ring's
 * color composition at all.
 */
public enum HeartSpell
{
    STORM(100),
    SHIELD(50),
    FLOWERS(30),
    HEAL(60),
    XP(40),
    CLEANSING_RAIN(50),
    TEMPEST_WARD(120),
    MANA_FONT(10),
    BLOOM_OF_LIFE(80),
    VERDANT_HARVEST(50),
    VITAL_SURGE(50);

    private final int manaCost;

    HeartSpell(int manaCost)
    {
        this.manaCost = manaCost;
    }

    public int manaCost()
    {
        return manaCost;
    }

    /**
     * True for the 5 spells that run for a duration rather than firing once and finishing -
     * STORM, SHIELD, TEMPEST_WARD, MANA_FONT, VITAL_SURGE - each already tracked by its own
     * dedicated state in {@code HeartCoreBlockEntity} rather than a shared "current spell"
     * field, which is what actually lets two of them run on the same heart at once. {@code
     * HeartCoreBlock#interact} uses this to cap a heart at 2 concurrent prolonged spells while
     * leaving one-shot spells (Flowers, Heal, XP, Cleansing Rain, Bloom of Life, Verdant
     * Harvest) free to fire regardless of what's already running - casting Cleansing Rain while
     * Shield is still up doesn't touch the shield at all, for instance.
     */
    public boolean isProlonged()
    {
        return switch (this)
        {
            case STORM, SHIELD, TEMPEST_WARD, MANA_FONT, VITAL_SURGE -> true;
            default -> false;
        };
    }

    /**
     * The cheapest cost across every spell here (currently {@code MANA_FONT}'s 10) - what
     * {@code HeartCoreBlockEntity} treats as "not worth calling this mana anymore": once a
     * heart's mana drops below this, on the theory that no spell could ever be cast with it
     * anyway, it's zeroed out rather than left as unusable leftover dust. Computed rather than
     * hand-copied so it can never drift out of sync with the values above.
     */
    public static int minManaCost()
    {
        int min = Integer.MAX_VALUE;
        for (HeartSpell spell : values())
        {
            min = Math.min(min, spell.manaCost);
        }
        return min;
    }

    /**
     * Gold casts Flowers and Purple casts Shield - the reverse of how the mod first shipped -
     * because {@link com.patrickma.magiccircles.entity.ShieldOrbEntity}'s texture (borrowed
     * from the Fairy Portal block) reads as purple, so a purple ring raising it looks right
     * rather than mismatched. Gold keeps its unrelated job as one of the two colors
     * {@link PortalRitual} requires - that's a different pattern entirely and isn't affected
     * by which spell a *solid* gold ring casts here.
     */
    public static HeartSpell soloFor(RuneColor color)
    {
        return switch (color)
        {
            case BLUE -> STORM;
            case GOLD -> FLOWERS;
            case PURPLE -> SHIELD;
            case RED -> HEAL;
            case GREEN -> XP;
            // Never a Heart Core spell at all - a solid Black ring is exclusively
            // limbo/RiteOfPassage's own trigger (see item/AthameItem), activated by an Athame
            // right-click rather than a Fairy Horn, and never reaches this method in practice.
            case BLACK -> null;
        };
    }

    @Nullable
    public static HeartSpell comboFor(RuneColor a, RuneColor b)
    {
        RuneColor lo = a.ordinal() < b.ordinal() ? a : b;
        RuneColor hi = a.ordinal() < b.ordinal() ? b : a;
        if (lo == RuneColor.BLUE && hi == RuneColor.PURPLE) return TEMPEST_WARD;
        if (lo == RuneColor.BLUE && hi == RuneColor.RED) return CLEANSING_RAIN;
        if (lo == RuneColor.GOLD && hi == RuneColor.GREEN) return VERDANT_HARVEST;
        if (lo == RuneColor.GOLD && hi == RuneColor.RED) return BLOOM_OF_LIFE;
        if (lo == RuneColor.PURPLE && hi == RuneColor.GREEN) return MANA_FONT;
        if (lo == RuneColor.RED && hi == RuneColor.GREEN) return VITAL_SURGE;
        return null;
    }
}
