package com.patrickma.magiccircles.item;

import com.patrickma.magiccircles.block.RuneColor;
import net.minecraft.world.level.block.Block;

/**
 * Identical to {@link ChalkItem} in every way except which {@link RuneColor} it draws - same
 * symbols, same 200 uses, just a different border/wisp color (and, once a ring is a uniform
 * color, a different spell available to it).
 */
public class ColoredChalkItem extends ChalkItem
{
    private final RuneColor color;

    public ColoredChalkItem(Block block, Properties properties, RuneColor color)
    {
        super(block, properties);
        this.color = color;
    }

    @Override
    protected RuneColor getColor()
    {
        return color;
    }
}
