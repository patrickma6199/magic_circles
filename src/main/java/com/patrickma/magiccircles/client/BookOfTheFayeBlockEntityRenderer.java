package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.entity.BookOfTheFayeBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the placed Book of the Faye with vanilla's own {@link BookModel} (the Enchanting Table's
 * and the Lectern's book), recolored (see {@code tools/gen_book_of_the_faye_texture.py}: green
 * cover, brown spine) rather than remeshed.
 *
 * <p>At rest it lies shut at a Lectern's angle ({@code 67.5F} about Z - the Lectern's own value;
 * 90 would be fully flat, 0 stood on end). While someone reads it, it opens, lifts, and turns and
 * tilts to hold its pages toward them, pages slowly turning - all of which {@code
 * block/entity/ReadableBookBlockEntity} works out; this only draws it.
 *
 * <p>The rotation order - heading about Y (negated), tilt about Z, then a small drop - is exactly
 * vanilla's Lectern math, decompiled rather than reasoned about, because getting any one of those
 * pieces wrong reads as "the book ignores how I placed it". The resting heading is the block's own
 * facing, so a book nobody is reading still lies the way it was placed.
 */
public class BookOfTheFayeBlockEntityRenderer implements BlockEntityRenderer<BookOfTheFayeBlockEntity>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/book_of_the_faye_open.png");
    private static final float PAGE_TURN_TICKS = 300.0F;

    private final BookModel model;

    public BookOfTheFayeBlockEntityRenderer(BlockEntityRendererProvider.Context context)
    {
        this.model = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(BookOfTheFayeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
        poseStack.pushPose();
        poseStack.translate(0.5, blockEntity.restingHeight() + blockEntity.lift(partialTick), 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blockEntity.yaw(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(blockEntity.tilt(partialTick)));
        poseStack.translate(0.0F, -0.125F, 0.0F);

        float age = blockEntity.getAge() + partialTick;
        float pageTurn = (age % PAGE_TURN_TICKS) / PAGE_TURN_TICKS;
        // The model scales the page turn by how open the book is, so a shut book stays still.
        model.setupAnim(age, pageTurn, 0.0F, blockEntity.openness(partialTick));

        VertexConsumer consumer = buffer.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}
