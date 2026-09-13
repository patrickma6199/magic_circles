package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws Fairy Wings on anyone Blessed by the Wellspring (see {@code
 * registry/ModEffects#BLESSED_BY_WELLSPRING}, {@code FairyFlightManager}) - purely a rendering
 * effect keyed off the effect itself, not an item: there's no Fairy Wings item at all (it can't be
 * held, equipped as armor, or obtained any other way), so a real chestplate the player is wearing
 * renders completely normally underneath, at the same time.
 *
 * <p>Opens into the real elytra glide pose (via {@link FairyWingsModel}, the same mesh as
 * vanilla's own {@code ElytraModel}) exactly while {@code isSwimming() && !isInWater()} - the
 * synced signal {@code FairyFlightManager} forces true precisely while a Blessed player is
 * actually fairy-flying (and never true for a genuinely wet, ordinarily-swimming player, since
 * real swimming requires being in water and fairy-flight requires not being in it) - not vanilla's
 * own {@code isFallFlying()}, which this mod deliberately never touches (see {@link
 * FairyWingsModel}'s own doc comment for why). Folded closed the rest of the time, same as a worn
 * Elytra looks when not gliding.
 */
public class FairyWingsLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/fairy_wings.png");
    private final FairyWingsModel<T> model;

    public FairyWingsLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet)
    {
        super(parent);
        this.model = new FairyWingsModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch)
    {
        if (!entity.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            return;
        }

        boolean gliding = entity.isSwimming() && !entity.isInWater();

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        this.getParentModel().copyPropertiesTo(this.model);
        this.model.setupGlideAnim(entity, gliding);
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffer, RenderType.armorCutoutNoCull(TEXTURE), false, false);
        this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
