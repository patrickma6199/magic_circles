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
 * <p>Opens into the glide pose (via {@link FairyWingsModel}, the same mesh as vanilla's own {@code
 * ElytraModel}) exactly while the wearer {@code isFallFlying()} - a synced flag, so every observer
 * sees the same thing - and folds shut the rest of the time, the way a worn elytra looks when not
 * gliding. Vanilla's own elytra layer never draws anything here, since there is no elytra in the
 * chest slot for it to find.
 *
 * <p>The Fairy Queen wears her own, larger wings instead (see {@link QueenWingsModel}).
 */
public class FairyWingsLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/fairy_wings.png");
    private static final ResourceLocation QUEEN_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/fairy_queen_wings.png");
    /** On top of her renderer's own scale - her wings are half again a fairy's. */
    private static final float QUEEN_WING_SCALE = 1.25F;
    private final FairyWingsModel<T> model;
    private final QueenWingsModel<T> queenModel;
    /** How solid a ghost's wings look - about as faint as the ghost's own body. */
    private static final float GHOST_ALPHA = 0.3F;

    public FairyWingsLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet)
    {
        super(parent);
        this.model = new FairyWingsModel<>(modelSet.bakeLayer(ModelLayers.ELYTRA));
        this.queenModel = new QueenWingsModel<>(modelSet.bakeLayer(QueenWingsModel.LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch)
    {
        // A fairy is born with its wings; everyone else wears them only while Blessed.
        boolean fairy = entity instanceof com.patrickma.magiccircles.entity.FairyEntity;
        if (!fairy && !entity.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            return;
        }

        // Spread in flight, folded on the ground - and folded on the throne too.
        boolean seated = entity instanceof com.patrickma.magiccircles.entity.FairyQueenEntity queen && queen.isSeated();
        boolean gliding = fairy ? !entity.onGround() && !seated : entity.isFallFlying();

        boolean queen = entity instanceof com.patrickma.magiccircles.entity.FairyQueenEntity;
        ResourceLocation texture = queen ? QUEEN_TEXTURE : TEXTURE;
        net.minecraft.client.model.EntityModel<T> wings;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        if (queen)
        {
            poseStack.scale(QUEEN_WING_SCALE, QUEEN_WING_SCALE, QUEEN_WING_SCALE);
            this.getParentModel().copyPropertiesTo(this.queenModel);
            this.queenModel.setupGlideAnim(entity, gliding);
            wings = this.queenModel;
        }
        else
        {
            this.getParentModel().copyPropertiesTo(this.model);
            this.model.setupGlideAnim(entity, gliding);
            wings = this.model;
        }
        if (entity.isInvisible())
        {
            // A fairy's ghost: the wings are as faint as the rest of it.
            VertexConsumer ghostly = buffer.getBuffer(RenderType.entityTranslucent(texture));
            wings.renderToBuffer(poseStack, ghostly, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, GHOST_ALPHA);
        }
        else
        {
            VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffer, RenderType.armorCutoutNoCull(texture), false, false);
            wings.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        poseStack.popPose();
    }
}
