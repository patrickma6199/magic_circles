package com.patrickma.magiccircles.worldgen;

import com.mojang.serialization.Codec;
import com.patrickma.magiccircles.registry.ModPlacementModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Keeps Living Wood Trees from ever spawning within 5 blocks of either the World Tree's own flat
 * disc or the portal ruins' hill/apron - not a cleanup pass after the fact (that's what {@code
 * WorldTree#clearInterior}/{@code FairyPortalRuins#clearTreeBlocks} already are, and they stay),
 * but stopping the natural tree feature from ever rolling a position there in the first place.
 *
 * <p>Without this, a tree could spawn right at the edge of one of those flat discs with its own
 * canopy straddling the boundary - and since the flat-earth carving is a perfect circle, whatever
 * of that canopy happened to fall inside got clipped away in one clean arc, which read as
 * obviously artificial (a tree "cut to fit" a circular cutout) rather than a natural clearing.
 * Excluding a 5-block buffer *beyond* the flat radius means the nearest tree's own trunk can
 * never be closer than that to the boundary, so even its widest overhanging branch has room to
 * clear the flat disc's edge without ever needing to be cut.
 */
public final class FairyLandmarkExclusionFilter extends PlacementModifier
{
    public static final FairyLandmarkExclusionFilter INSTANCE = new FairyLandmarkExclusionFilter();
    public static final Codec<FairyLandmarkExclusionFilter> CODEC = Codec.unit(INSTANCE);

    private static final double TREE_BUFFER = 5.0;

    private FairyLandmarkExclusionFilter()
    {
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos)
    {
        double treeExclusion = FairyRealmChunkGenerator.flatRadius() + TREE_BUFFER;
        double dxTree = pos.getX() - WorldTree.CENTER_X;
        double dzTree = pos.getZ() - WorldTree.CENTER_Z;
        if (dxTree * dxTree + dzTree * dzTree < treeExclusion * treeExclusion)
        {
            return Stream.empty();
        }

        double ruinsExclusion = FairyPortalRuins.flatApronRadius() + TREE_BUFFER;
        double dxRuins = pos.getX() - FairyPortalRuins.centerX();
        double dzRuins = pos.getZ() - FairyPortalRuins.centerZ();
        if (dxRuins * dxRuins + dzRuins * dzRuins < ruinsExclusion * ruinsExclusion)
        {
            return Stream.empty();
        }

        return Stream.of(pos);
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModPlacementModifiers.FAIRY_LANDMARK_EXCLUSION.get();
    }
}
