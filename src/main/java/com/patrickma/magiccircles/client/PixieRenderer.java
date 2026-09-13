package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.PixieEntity;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.allay.Allay;

/**
 * Reuses vanilla's own {@link AllayModel} (baked from the vanilla-registered {@link
 * ModelLayers#ALLAY} layer - no need to define or bake our own copy of it) exactly as {@code
 * AllayRenderer} does, differing only in which texture gets drawn onto that model - see {@link
 * PixieEntity}'s own doc comment for why reusing the model/animations wholesale was the point.
 * Typed against {@link Allay} (matching {@link AllayModel}'s own generic exactly) rather than
 * {@link PixieEntity} specifically - registering this for {@code ModEntities.PIXIE} (an {@code
 * EntityType<PixieEntity>}, and {@code PixieEntity extends Allay}) is exactly what {@code
 * EntityRenderers.register}'s own bound expects, and this renderer never needs to know it's
 * actually looking at a Pixie rather than a plain Allay - it always draws the same texture.
 */
public class PixieRenderer extends MobRenderer<Allay, AllayModel>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/pixie/pixie.png");

    public PixieRenderer(EntityRendererProvider.Context context)
    {
        super(context, new AllayModel(context.bakeLayer(ModelLayers.ALLAY)), 0.3f);
    }

    @Override
    public ResourceLocation getTextureLocation(Allay entity)
    {
        return TEXTURE;
    }
}
