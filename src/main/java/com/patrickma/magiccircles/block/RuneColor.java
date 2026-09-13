package com.patrickma.magiccircles.block;

import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;

/**
 * Which chalk drew a given {@link MagicCircleBlock} rune. {@code BLUE} is regular Chalk;
 * everything else is a distinctly-colored chalk that places the exact same block, just with
 * this property set differently (see {@code ChalkItem#getColor}). A ring's color composition -
 * see {@code MagicCircleRitual#detectSpell} - is what decides which spell a Heart Core there
 * can cast: uniform casts that color's solo spell, a 6-6 split of two colors casts (some of)
 * that pair's combo spell.
 */
public enum RuneColor implements StringRepresentable
{
    BLUE("blue", new Vector3f(0.55f, 0.72f, 1.0f)),
    GOLD("gold", new Vector3f(1.0f, 0.82f, 0.3f)),
    PURPLE("purple", new Vector3f(0.72f, 0.45f, 1.0f)),
    RED("red", new Vector3f(1.0f, 0.35f, 0.35f)),
    GREEN("green", new Vector3f(0.45f, 1.0f, 0.5f)),
    /** Forbidden magic - drawn with Black Chalk, the only color that ever activates {@code RiteOfPassage} instead of a Heart Core spell. */
    BLACK("black", new Vector3f(0.35f, 0.05f, 0.45f));

    private final String serializedName;
    private final Vector3f wispColor;

    RuneColor(String serializedName, Vector3f wispColor)
    {
        this.serializedName = serializedName;
        this.wispColor = wispColor;
    }

    /** The color a rune of this type glows/wisps in - see {@code MagicCircleBlockEntity}, {@code ClientCircleWisps}. */
    public Vector3f wispColor()
    {
        return wispColor;
    }

    @Override
    public String getSerializedName()
    {
        return serializedName;
    }
}
