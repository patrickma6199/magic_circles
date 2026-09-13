package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.FerrymanEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the Ferryman's three asset files. Spelled out rather than using {@code
 * DefaultedEntityGeoModel} so the paths are visible here instead of implied by a naming convention.
 */
public class FerrymanModel extends GeoModel<FerrymanEntity>
{
    private static final ResourceLocation MODEL =
            new ResourceLocation(MagicCircles.MOD_ID, "geo/ferryman.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/ferryman.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(MagicCircles.MOD_ID, "animations/ferryman.animation.json");

    @Override
    public ResourceLocation getModelResource(FerrymanEntity animatable)
    {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FerrymanEntity animatable)
    {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FerrymanEntity animatable)
    {
        return ANIMATION;
    }
}
