package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A small glowing cube - the Heart Core's whole visual. Two parts: a small opaque {@code orb}
 * (lit normally, textured with a per-face gradient in {@code heart_core.png} so it doesn't
 * read as one flat color) and a larger, translucent {@code aura} shell a few pixels bigger on
 * every side and centered on the same point, rendered separately by
 * {@code HeartCoreBlockEntityRenderer} full-bright and alpha-blended - the same "glowing wire
 * / neon sign" trick of layering a soft translucent glow shape over a solid core. It renders
 * above a block position rather than as part of an Entity, so this extends the plain
 * {@link Model} base rather than {@code EntityModel} - {@link #animate} is our own method,
 * not an override.
 */
public class HeartCoreModel extends Model
{
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(MagicCircles.MOD_ID, "heart_core"), "main");

    private final ModelPart root;
    private final ModelPart orb;
    private final ModelPart aura;

    public HeartCoreModel(ModelPart root)
    {
        super(RenderType::entityCutout);
        this.root = root;
        this.orb = root.getChild("orb");
        this.aura = root.getChild("aura");
    }

    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        var parts = mesh.getRoot();

        parts.addOrReplaceChild("orb", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3f, -3f, -3f, 6f, 6f, 6f),
                PartPose.ZERO);

        parts.addOrReplaceChild("aura", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5f, -5f, -5f, 10f, 10f, 10f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Gentle bob + slow spin. Call before either render pass each frame. */
    public void animate(float ageInTicks)
    {
        root.y = Mth.sin(ageInTicks * 0.1f) * 1.5f;
        root.yRot = ageInTicks * 0.02f;
    }

    public void renderOrb(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a)
    {
        orb.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
    }

    public void renderAura(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a)
    {
        aura.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a)
    {
        renderOrb(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
    }
}
