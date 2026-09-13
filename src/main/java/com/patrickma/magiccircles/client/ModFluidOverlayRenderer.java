package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a full-screen fluid overlay texture at a caller-chosen alpha, instead of going through
 * vanilla's own {@code ScreenEffectRenderer#renderFluid} (which every {@code
 * IClientFluidTypeExtensions#getRenderOverlayTexture} override, including this mod's, ends up
 * calling by default via {@code renderOverlay}).
 *
 * <p>This exists because of a real bug decompiling that method turned up: it hardcodes
 * {@code RenderSystem.setShaderColor(f, f, f, 0.1F)} - a global alpha multiplier of exactly 10%,
 * applied on top of (not instead of) the overlay texture's own per-pixel alpha, baked directly
 * into that method's body where no {@code IClientFluidTypeExtensions} override can reach or
 * bypass it. That's why three separate attempts at a more visible portal-water underwater tint
 * (see {@code tools/gen_portal_underwater_overlay.py}'s own docstring for all three) kept reading
 * as negligible no matter how high the texture's own alpha was pushed - 95 or 245 out of 255, the
 * real effective alpha after that hidden 10% cap barely moves (roughly 3.7% vs. 9.6%). Vanilla's
 * own water tint gets away with the same cap because it's a strongly saturated blue - a hue shift
 * stays visible even at ~5% real alpha - but a deliberately pale, near-white tint doesn't get
 * that for free, which is exactly the "changing the alpha does nothing" symptom that showed up.
 *
 * <p>This is {@code ScreenEffectRenderer#renderFluid}'s own quad/shader setup, copied verbatim
 * except for that one hardcoded value - the caller's {@code alpha} takes its place, so the
 * overlay texture's own alpha (not a hidden 10% ceiling) finally decides how strong the tint
 * actually is.
 */
public final class ModFluidOverlayRenderer
{
    private ModFluidOverlayRenderer()
    {
    }

    public static void renderFluidOverlay(Minecraft mc, PoseStack poseStack, ResourceLocation texture, float alpha)
    {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        BlockPos eyePos = BlockPos.containing(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        float brightness = LightTexture.getBrightness(mc.player.level().dimensionType(), mc.player.level().getMaxLocalRawBrightness(eyePos));
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(brightness, brightness, brightness, alpha);
        float yRotOffset = -mc.player.getYRot() / 64.0F;
        float xRotOffset = mc.player.getXRot() / 64.0F;
        Matrix4f matrix4f = poseStack.last().pose();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix4f, -1.0F, -1.0F, -0.5F).uv(4.0F + yRotOffset, 4.0F + xRotOffset).endVertex();
        bufferBuilder.vertex(matrix4f, 1.0F, -1.0F, -0.5F).uv(0.0F + yRotOffset, 4.0F + xRotOffset).endVertex();
        bufferBuilder.vertex(matrix4f, 1.0F, 1.0F, -0.5F).uv(0.0F + yRotOffset, 0.0F + xRotOffset).endVertex();
        bufferBuilder.vertex(matrix4f, -1.0F, 1.0F, -0.5F).uv(4.0F + yRotOffset, 0.0F + xRotOffset).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
