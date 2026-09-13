package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.entity.FerrymanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Ferryman from his Blockbench model via GeckoLib - see {@link FerrymanModel} for the
 * asset paths and {@code FerrymanEntity#registerControllers} for the single idle animation. The
 * model needs GeckoLib rather than a vanilla one because every cube in it carries its own rotation
 * and several use per-face UVs, neither of which vanilla's model system can represent.
 */
public class FerrymanRenderer extends GeoEntityRenderer<FerrymanEntity>
{
    public FerrymanRenderer(EntityRendererProvider.Context context)
    {
        super(context, new FerrymanModel());
        this.shadowRadius = 0.5f;
    }
}
