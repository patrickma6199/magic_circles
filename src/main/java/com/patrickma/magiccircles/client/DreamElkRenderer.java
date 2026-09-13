package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.DreamElkEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the Dream Elk's own Blockbench model - see {@link DreamElkModel}. */
public class DreamElkRenderer extends MobRenderer<DreamElkEntity, DreamElkModel<DreamElkEntity>>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/dream_elk.png");

    public DreamElkRenderer(EntityRendererProvider.Context context)
    {
        super(context, new DreamElkModel<>(context.bakeLayer(DreamElkModel.LAYER)), 0.75f);
    }

    @Override
    public ResourceLocation getTextureLocation(DreamElkEntity entity)
    {
        return TEXTURE;
    }

    /**
     * The model is built facing +Z (its snout runs that way and its antlers sweep back over -Z),
     * but Minecraft points entities down -Z, so it rendered rump-first. Spinning the finished model
     * here rather than rotating a bone leaves the authored animations alone - they and the geometry
     * share one local frame, so turning the whole result keeps them consistent with each other.
     * {@code DreamElkModel} negates its head-tracking to match.
     */
    @Override
    protected void setupRotations(DreamElkEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks)
    {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
    }

    /**
     * Vanilla tips a dying mob onto its side by rotating the whole renderer 90 degrees. The elk has
     * its own Death animation that already does exactly that, so leaving this at its default would
     * roll it through the floor and out the other side.
     */
    @Override
    protected float getFlipDegrees(DreamElkEntity entity)
    {
        return 0.0f;
    }
}
