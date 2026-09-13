package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the floating heart above a {@link com.patrickma.magiccircles.block.HeartCoreBlock},
 * which has no static model of its own (see {@code RenderShape.INVISIBLE} there) - this is
 * the only thing that makes the block visible at all. Two layers: a small opaque "orb" lit
 * normally, and a larger translucent "aura" shell around it, rendered full-bright and
 * additively-blended so it reads as a soft glow regardless of ambient light - the same
 * layering trick as a glowing wire or neon sign texture, rather than a light actually
 * illuminating anything (see {@link HeartCoreModel} for why real colored light isn't
 * possible here at all).
 */
public class HeartCoreBlockEntityRenderer implements BlockEntityRenderer<HeartCoreBlockEntity>
{
    private static final ResourceLocation ORB_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/heart_core.png");
    private static final ResourceLocation AURA_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/heart_core_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double FLOAT_HEIGHT = 1.5;

    // A depleted heart (see HeartCoreBlockEntity#isManaDepletedSynced) reads as "off" rather
    // than "the same heart, slightly dimmer": a dark tint on the orb, a shrunk aura, and -
    // critically - the aura drops the full-bright emissive render path entirely in favor of a
    // normal translucent one lit by real ambient light, since that emissive path is the entire
    // reason it reads as "glowing" at all (see the class doc comment below). Charging (or fully
    // charged) mana is simply the absence of this - the original always-on look.
    private static final float DEPLETED_TINT = 0.28F;
    private static final float DEPLETED_AURA_SCALE = 0.55F;
    private static final float DEPLETED_AURA_ALPHA = 0.35F;

    private final HeartCoreModel model;

    public HeartCoreBlockEntityRenderer(BlockEntityRendererProvider.Context context)
    {
        this.model = new HeartCoreModel(context.bakeLayer(HeartCoreModel.LAYER));
    }

    @Override
    public void render(HeartCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
        poseStack.pushPose();
        poseStack.translate(0.5, FLOAT_HEIGHT, 0.5);
        // Vanilla entity/block models are authored with +Y pointing down; mirror back to world space.
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        model.animate(blockEntity.getAge() + partialTick);

        boolean depleted = blockEntity.isManaDepletedSynced();
        float tint = depleted ? DEPLETED_TINT : 1.0F;

        VertexConsumer orbConsumer = buffer.getBuffer(RenderType.entityCutout(ORB_TEXTURE));
        model.renderOrb(poseStack, orbConsumer, packedLight, OverlayTexture.NO_OVERLAY, tint, tint, tint, 1.0F);

        poseStack.pushPose();
        if (depleted)
        {
            poseStack.scale(DEPLETED_AURA_SCALE, DEPLETED_AURA_SCALE, DEPLETED_AURA_SCALE);
        }
        RenderType auraRenderType = depleted ? RenderType.entityTranslucent(AURA_TEXTURE) : RenderType.entityTranslucentEmissive(AURA_TEXTURE);
        int auraLight = depleted ? packedLight : FULL_BRIGHT;
        float auraAlpha = depleted ? DEPLETED_AURA_ALPHA : 1.0F;
        VertexConsumer auraConsumer = buffer.getBuffer(auraRenderType);
        model.renderAura(poseStack, auraConsumer, auraLight, OverlayTexture.NO_OVERLAY, tint, tint, tint, auraAlpha);
        poseStack.popPose();

        poseStack.popPose();
    }
}
