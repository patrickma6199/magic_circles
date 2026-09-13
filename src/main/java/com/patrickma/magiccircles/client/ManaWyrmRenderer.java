package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.ManaWyrmEntity;
import net.minecraft.client.model.SalmonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Reuses vanilla's own {@link SalmonModel} - the most elongated of the vanilla fish models, and
 * the closest free stand-in for "slithers like a snake" (see {@link ManaWyrmEntity}'s own doc
 * comment on why this doesn't attempt a real snake rig) - the same "borrow the model, swap the
 * texture" approach {@code PixieRenderer} already uses for Allay.
 */
public class ManaWyrmRenderer extends MobRenderer<ManaWyrmEntity, SalmonModel<ManaWyrmEntity>>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/mana_wyrm.png");

    public ManaWyrmRenderer(EntityRendererProvider.Context context)
    {
        super(context, createFinlessModel(context), 0.4f);
    }

    /**
     * "Just a swimming blob" - no side, top, or back fin. {@link SalmonModel}'s own fins are
     * separate named {@link net.minecraft.client.model.geom.ModelPart}s (confirmed by decompiling
     * it - {@code top_front_fin}/{@code top_back_fin}/{@code back_fin}/{@code left_fin}/
     * {@code right_fin}, all thin, zero-thickness planes), so hiding them is a matter of toggling
     * each part's own {@code visible} flag once, right here, rather than trying to paint over
     * their UV regions in the texture - those regions are degenerate slivers that would be prone
     * to bleeding into the body's own texture space if edited directly.
     */
    private static SalmonModel<ManaWyrmEntity> createFinlessModel(EntityRendererProvider.Context context)
    {
        SalmonModel<ManaWyrmEntity> model = new SalmonModel<>(context.bakeLayer(ModelLayers.SALMON));
        net.minecraft.client.model.geom.ModelPart root = model.root();
        root.getChild("body_front").getChild("top_front_fin").visible = false;
        root.getChild("body_back").getChild("back_fin").visible = false;
        root.getChild("body_back").getChild("top_back_fin").visible = false;
        root.getChild("right_fin").visible = false;
        root.getChild("left_fin").visible = false;
        return model;
    }

    @Override
    public ResourceLocation getTextureLocation(ManaWyrmEntity entity)
    {
        return TEXTURE;
    }
}
