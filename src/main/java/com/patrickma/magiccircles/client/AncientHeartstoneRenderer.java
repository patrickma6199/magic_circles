package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.AncientHeartstoneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Reuses {@link HeartCoreModel} - the same orb+aura shape a Heart Core floats above its own block
 * - tinted permanently gold, since this entity (unlike a real Heart Core) never depletes and
 * never needs to look "off." See {@link AncientHeartstoneEntity}'s own doc comment for why it's a
 * free-floating entity rather than another Heart Core block.
 */
public class AncientHeartstoneRenderer extends EntityRenderer<AncientHeartstoneEntity>
{
    private static final ResourceLocation ORB_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/heart_core.png");
    private static final ResourceLocation AURA_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/heart_core_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float GOLD_R = 1.0f;
    private static final float GOLD_G = 0.85f;
    private static final float GOLD_B = 0.35f;

    private final HeartCoreModel model;

    public AncientHeartstoneRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new HeartCoreModel(context.bakeLayer(HeartCoreModel.LAYER));
    }

    @Override
    public ResourceLocation getTextureLocation(AncientHeartstoneEntity entity)
    {
        return ORB_TEXTURE;
    }

    @Override
    public void render(AncientHeartstoneEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        model.animate(entity.tickCount + partialTick);

        VertexConsumer orbConsumer = buffer.getBuffer(RenderType.entityCutout(ORB_TEXTURE));
        model.renderOrb(poseStack, orbConsumer, packedLight, OverlayTexture.NO_OVERLAY, GOLD_R, GOLD_G, GOLD_B, 1.0F);

        VertexConsumer auraConsumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(AURA_TEXTURE));
        model.renderAura(poseStack, auraConsumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, GOLD_R, GOLD_G, GOLD_B, 1.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
