package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;

/** A single small cube - one node of a shield sphere. Reuses the fairy portal's texture. */
public class ShieldOrbModel extends EntityModel<ShieldOrbEntity>
{
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(MagicCircles.MOD_ID, "shield_orb"), "main");

    private final ModelPart cube;

    public ShieldOrbModel(ModelPart root)
    {
        this.cube = root.getChild("cube");
    }

    public static LayerDefinition createBodyLayer()
    {
        // Close to the entity's actual 1.6-block hitbox (see ModEntities#SHIELD_ORB) so
        // neighboring orbs read as a near-solid wall rather than small dots with visible gaps
        // between them, even though the collision itself is already gap-free either way.
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube",
                CubeListBuilder.create().texOffs(0, 0).addBox(-11f, -11f, -11f, 22f, 22f, 22f),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void setupAnim(ShieldOrbEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        cube.yRot = ageInTicks * 0.02f;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a)
    {
        cube.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
    }
}
