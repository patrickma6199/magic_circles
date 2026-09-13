package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders nothing - a shield node's boundary-enforcement/repel behavior ({@code
 * HeartCoreBlockEntity#tickShieldBoundary}, {@code FairyRealmShield#onServerTick}) has never
 * depended on the entity actually being visible, so making every shield (the player-cast spell
 * and the permanent Fairy Realm boundary alike) fully invisible is just a matter of skipping the
 * model draw entirely - the entity itself, its position, and its collision-less "shove anything
 * that crosses it back inward" behavior are all completely unaffected either way.
 */
public class ShieldOrbRenderer extends EntityRenderer<ShieldOrbEntity>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "textures/block/fairy_portal.png");

    public ShieldOrbRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ShieldOrbEntity entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(ShieldOrbEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
        // Intentionally does not call super.render() either - that would still draw the vanilla
        // nameplate/leash/etc. hooks, none of which apply here, but skipping it outright is
        // simplest and matches "completely invisible."
    }
}
