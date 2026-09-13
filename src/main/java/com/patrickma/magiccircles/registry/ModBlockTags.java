package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags the mod defines (as opposed to ones it just adds entries to - see data/minecraft/tags). */
public class ModBlockTags
{
    /**
     * Blocks that count as "the Wellspring" for {@code HeartSpell#MANA_FONT}'s proximity check
     * (see {@code HeartCoreBlock#interact}) - tagged via
     * data/magiccircles/tags/blocks/wellspring.json, which lists
     * {@code magiccircles:wellspring_water}: the well at the base of the World Tree (see
     * {@code worldgen/WorldTree.java#carveWell}) is filled with that fluid's block and nothing
     * else is, so this tag now picks out that one specific pool precisely - no imprecision left
     * over from the earlier sea-lantern-based version (any Sea Lantern anywhere used to count).
     */
    public static final TagKey<Block> WELLSPRING = TagKey.create(Registries.BLOCK, new ResourceLocation(MagicCircles.MOD_ID, "wellspring"));

    private ModBlockTags()
    {
    }
}
