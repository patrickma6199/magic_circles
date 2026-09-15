package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.entity.ArtOfBloodBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Art of Blood with the same vanilla {@link BookModel} the Faye's book uses.
 *
 * <p>On the shelf it lies flat and shut, lower than the Faye's book, and does not stir - like
 * something set down carefully rather than left mid-read. Only for someone who has paid to read it
 * does it open, lift and turn its torn pages toward them ({@code
 * block/entity/ReadableBookBlockEntity}). The rotation math is the Faye's book's own - see {@code
 * BookOfTheFayeBlockEntityRenderer}.
 */
public class ArtOfBloodBlockEntityRenderer implements BlockEntityRenderer<ArtOfBloodBlockEntity>
{
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/art_of_blood.png");
    /** Slower than the Faye's book - these pages are turned reluctantly. */
    private static final float PAGE_TURN_TICKS = 420.0F;

    private final BookModel model;

    public ArtOfBloodBlockEntityRenderer(BlockEntityRendererProvider.Context context)
    {
        this.model = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(ArtOfBloodBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
        poseStack.pushPose();
        poseStack.translate(0.5, blockEntity.restingHeight() + blockEntity.lift(partialTick), 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blockEntity.yaw(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(blockEntity.tilt(partialTick)));
        poseStack.translate(0.0F, -0.125F, 0.0F);

        float age = blockEntity.getAge() + partialTick;
        float pageTurn = (age % PAGE_TURN_TICKS) / PAGE_TURN_TICKS;
        // Shut (openness 0) the model holds perfectly still; the page turn only shows once open.
        model.setupAnim(age, pageTurn, 0.0F, blockEntity.openness(partialTick));

        VertexConsumer consumer = buffer.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}
