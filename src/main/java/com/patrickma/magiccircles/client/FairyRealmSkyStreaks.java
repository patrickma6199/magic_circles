package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Fairy Realm's colored sky streaks - one slowly-drifting ribbon of color per rune, arcing
 * from near the zenith down toward the horizon, rather than the single permanent horizon-hugging
 * "sunset" glow band {@link FairyRealmEffects#getSunriseColor} used to force on unconditionally.
 * That approach read as exactly what it was - a sky frozen at one moment - which is why it's
 * gone now (see that class's own doc comment for what replaced it): six independent streaks
 * (every {@link RuneColor}'s own wisp color, plus white) each at their own fixed compass
 * direction, each rotating slowly around the sky at their own independent rate, so the sky reads
 * as alive rather than static.
 *
 * <p>Hooked on {@link RenderLevelStageEvent.Stage#AFTER_SKY} - "render custom objects into the
 * skybox," fired regardless of whether the sky itself actually rendered - rather than replacing
 * {@code DimensionSpecialEffects#renderSky} outright: that would mean losing (and having to
 * hand-rebuild, with no way to see the result without a real client) vanilla's own dome/sun/moon/
 * star rendering just to add a few extra shapes on top of it. This is purely additive instead -
 * one extra draw call per streak, after everything vanilla already draws.
 *
 * <p>Each streak is a plain color-only ribbon (no texture - {@code POSITION_COLOR}, the same
 * vertex format vanilla's own sunrise-glow fan uses), built as a {@code TRIANGLE_STRIP} climbing
 * from near the horizon up toward the zenith with alpha fading to 0 at both ends (peaking in the
 * middle) so it reads as a soft streak rather than a hard-edged wedge.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FairyRealmSkyStreaks
{
    private static final float RADIUS = 100.0F;
    private static final float HALF_WIDTH = 4.0F;
    private static final int SEGMENTS = 16;
    private static final float START_ELEVATION_DEG = 12.0F;
    private static final float END_ELEVATION_DEG = 85.0F;
    private static final float MAX_ALPHA = 0.55F;

    private static final Streak[] STREAKS = {
            streak(RuneColor.BLUE.wispColor(), 0.0F, 0.0016F),
            streak(goldColor(), 60.0F, -0.0011F),
            streak(RuneColor.PURPLE.wispColor(), 120.0F, 0.0009F),
            streak(RuneColor.RED.wispColor(), 180.0F, -0.0014F),
            streak(RuneColor.GREEN.wispColor(), 240.0F, 0.0007F),
            streak(new Vector3f(1.0F, 1.0F, 1.0F), 300.0F, -0.0010F),
    };

    private FairyRealmSkyStreaks()
    {
    }

    private static Vector3f goldColor()
    {
        // Matches FairyRealmEffects' own former GOLD constant - Gold's own RuneColor wisp color
        // reads slightly too pale/yellow next to the sky dome, this is a closer match to the
        // horizon-glow gold this replaces.
        return new Vector3f(1.0F, 0.82F, 0.3F);
    }

    private static Streak streak(Vector3f color, float baseAzimuthDeg, float degreesPerTick)
    {
        return new Streak(color, baseAzimuthDeg, degreesPerTick);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }

        float time = mc.level.getGameTime() + event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        for (Streak streak : STREAKS)
        {
            poseStack.pushPose();
            float azimuth = streak.baseAzimuthDeg + time * streak.degreesPerTick;
            poseStack.mulPose(Axis.YP.rotationDegrees(azimuth));
            drawStreak(poseStack, streak.color);
            poseStack.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drawStreak(PoseStack poseStack, Vector3f color)
    {
        Matrix4f matrix4f = poseStack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS; i++)
        {
            float t = (float) i / SEGMENTS;
            float elevationDeg = START_ELEVATION_DEG + (END_ELEVATION_DEG - START_ELEVATION_DEG) * t;
            float elevationRad = (float) Math.toRadians(elevationDeg);
            float y = RADIUS * (float) Math.sin(elevationRad);
            float z = RADIUS * (float) Math.cos(elevationRad);
            float alpha = MAX_ALPHA * (float) Math.sin(t * Math.PI);

            bufferBuilder.vertex(matrix4f, -HALF_WIDTH, y, z).color(color.x, color.y, color.z, alpha).endVertex();
            bufferBuilder.vertex(matrix4f, HALF_WIDTH, y, z).color(color.x, color.y, color.z, alpha).endVertex();
        }

        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    private static final class Streak
    {
        final Vector3f color;
        final float baseAzimuthDeg;
        final float degreesPerTick;

        Streak(Vector3f color, float baseAzimuthDeg, float degreesPerTick)
        {
            this.color = color;
            this.baseAzimuthDeg = baseAzimuthDeg;
            this.degreesPerTick = degreesPerTick;
        }
    }
}
