package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Makes the Heartstone render as the same glowing orb+aura the placed Heart Core uses,
 * rather than a flat 2D icon, wherever it's shown in 3D (in hand, on the ground, in an item
 * frame) - the item model just points here (see {@code models/item/heartstone.json}'s
 * {@code "parent": "builtin/entity"}, the same mechanism vanilla uses for chests/shields/
 * tridents) instead of shipping baked quads. The 2D icon file still exists and is still used
 * for the inventory-slot render and the item-break particle, since those don't go through
 * this renderer.
 */
public class HeartstoneItemRenderer extends BlockEntityWithoutLevelRenderer
{
    private static final ResourceLocation ORB_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/heart_core.png");
    private static final ResourceLocation AURA_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/heart_core_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final HeartCoreModel model;

    public HeartstoneItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet)
    {
        super(dispatcher, modelSet);
        this.model = new HeartCoreModel(modelSet.bakeLayer(HeartCoreModel.LAYER));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay)
    {
        poseStack.pushPose();
        // Our model is centered on its own origin and authored at roughly block scale, same as
        // the placed Heart Core - recenter and mirror the same way, then scale *down* to
        // something that reads as a hand-sized stone rather than a full-block floating heart
        // (an earlier scale of 1.4, bigger than the block it's modeled on, was what made this
        // look absurdly oversized both in-hand and on the ground).
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(-0.45F, -0.45F, 0.45F);

        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        model.animate(gameTime * 0.4F);

        VertexConsumer orbConsumer = buffer.getBuffer(RenderType.entityCutout(ORB_TEXTURE));
        model.renderOrb(poseStack, orbConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        VertexConsumer auraConsumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(AURA_TEXTURE));
        model.renderAura(poseStack, auraConsumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}
