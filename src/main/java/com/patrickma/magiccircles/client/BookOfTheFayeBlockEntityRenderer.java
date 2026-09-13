package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.BookOfTheFayeBlock;
import com.patrickma.magiccircles.block.entity.BookOfTheFayeBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the placed Book of the Faye as a real, open, gently animating 3D book - vanilla's own
 * {@link BookModel} (the same one the Enchanting Table's floating book, and a Lectern's own open
 * book, both already use), recolored (see {@code tools/gen_book_of_the_faye_texture.py}: green
 * cover, brown spine) rather than remeshed. Always at least slightly open (unlike the held item,
 * which looks closed - see {@code block/BookOfTheFayeBlock}'s own doc comment for why the two
 * differ), with its pages slowly, endlessly turning - the same idle animation the Enchanting
 * Table's own book plays.
 *
 * <p>Tilted like a Lectern's own resting book, not standing upright facing the player, which an
 * earlier version did by omitting the tilt entirely. Confirmed by decompiling both vanilla
 * renderers that already draw this exact model: the Enchanting Table's own "floating" book tilts
 * {@code Axis.ZP.rotationDegrees(80.0F)}, and the Lectern's own (resting flat on its stand) tilts
 * {@code 67.5F} - this uses that same {@code 67.5F}, per an explicit follow-up request to match
 * the Lectern's angle rather than the fully-flat {@code 90.0F} an earlier version used.
 * Neither vanilla renderer flips any axis via a negative {@code scale} either (an earlier version
 * of this renderer did, copying the unrelated convention {@code HeartstoneItemRenderer} needs for
 * its own different model/origin) - removed, matching both vanilla references exactly.
 */
public class BookOfTheFayeBlockEntityRenderer implements BlockEntityRenderer<BookOfTheFayeBlockEntity>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/book_of_the_faye_open.png");
    private static final double FLOAT_HEIGHT = 0.45;
    private static final float LIE_FLAT_DEGREES = 67.5F;

    private final BookModel model;

    public BookOfTheFayeBlockEntityRenderer(BlockEntityRendererProvider.Context context)
    {
        this.model = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(BookOfTheFayeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
        poseStack.pushPose();
        poseStack.translate(0.5, FLOAT_HEIGHT, 0.5);
        // Exactly vanilla's own Lectern math for this same model at this same tilt, rather than
        // the hand-rolled mapping this used before: the clockwise turn and the negation together
        // are what make the book's spine line up with the block's facing, and the small drop after
        // the tilt is what keeps it sitting on the block instead of hovering off its own corner.
        // Decompiled from LecternRenderer rather than reasoned about, because getting any one of
        // those three pieces wrong reads as "the book ignores how I placed it".
        float facingYRot = blockEntity.getBlockState().getValue(BookOfTheFayeBlock.FACING).getClockWise().toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(-facingYRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(LIE_FLAT_DEGREES));
        poseStack.translate(0.0F, -0.125F, 0.0F);

        float age = blockEntity.getAge() + partialTick;
        float pageAngle = (age % 300.0F) / 300.0F;
        float open = 1.0F;
        model.setupAnim(age, pageAngle, 0.0F, open);

        VertexConsumer consumer = buffer.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}
