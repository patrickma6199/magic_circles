package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.FairyQueenEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Fairy Queen: a fairy's own model, a head taller than her people, in her white gown (see
 * {@code tools/gen_fairy_textures.py}). Her wings, like any fairy's, never fold, and she leans into
 * her flight the same way. Her wisps are {@link QueenWisps}.
 */
public class FairyQueenRenderer extends HumanoidMobRenderer<FairyQueenEntity, PlayerModel<FairyQueenEntity>>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/fairy/fairy_queen.png");
    /** "Slightly enlarged" - tall, not a giant. */
    private static final float SCALE = 1.15f;
    private static final float LEAN_PIVOT = 0.9f;

    public FairyQueenRenderer(EntityRendererProvider.Context context)
    {
        super(context, new QueenModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
        this.addLayer(new FairyWingsLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(FairyQueenEntity queen)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(FairyQueenEntity queen, PoseStack poseStack, float partialTick)
    {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected void setupRotations(FairyQueenEntity queen, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
        super.setupRotations(queen, poseStack, ageInTicks, rotationYaw, partialTick);
        float lean = queen.lean(partialTick);
        if (lean > 0.01f)
        {
            poseStack.translate(0.0f, LEAN_PIVOT, 0.0f);
            poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
            poseStack.translate(0.0f, -LEAN_PIVOT, 0.0f);
        }
    }
}
