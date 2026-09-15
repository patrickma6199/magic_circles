package com.patrickma.magiccircles.client;

import com.google.common.collect.ImmutableList;
import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Fairy Queen's wings - not a fairy's. Each is two panels: a great forewing, half again the
 * size of anyone else's, and a long tail hanging from its lower edge and sweeping outward, like a
 * swallowtail's (see {@code tools/gen_fairy_queen_wings_texture.py} for the gold-veined design on
 * them). Hung from the same point on the back as an elytra and opened with the same angles, so she
 * flies the way her people do - only with a slower, deeper beat.
 */
public class QueenWingsModel<T extends LivingEntity> extends AgeableListModel<T>
{
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(MagicCircles.MOD_ID, "fairy_queen_wings"), "main");

    private static final float CLOSED_X = 0.2617994F;
    private static final float CLOSED_Z = -0.2617994F;
    private static final float OPEN_X = 0.34906584F;
    private static final float OPEN_Z = -(float) Math.PI / 2F;
    private static final float TAIL_SWEEP = 0.5F;

    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftTail;
    private final ModelPart rightTail;

    public QueenWingsModel(ModelPart root)
    {
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.leftTail = this.leftWing.getChild("left_tail");
        this.rightTail = this.rightWing.getChild("right_tail");
    }

    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition left = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -3.0F, 0.0F, 16.0F, 26.0F, 1.0F),
                PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, CLOSED_X, 0.0F, CLOSED_Z));
        left.addOrReplaceChild("left_tail",
                CubeListBuilder.create().texOffs(0, 28).addBox(-10.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(-3.0F, 18.0F, 0.1F, 0.0F, 0.0F, TAIL_SWEEP));
        PartDefinition right = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.0F, -3.0F, 0.0F, 16.0F, 26.0F, 1.0F),
                PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, CLOSED_X, 0.0F, -CLOSED_Z));
        right.addOrReplaceChild("right_tail",
                CubeListBuilder.create().texOffs(0, 28).mirror().addBox(0.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(3.0F, 18.0F, 0.1F, 0.0F, 0.0F, -TAIL_SWEEP));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected Iterable<ModelPart> headParts()
    {
        return ImmutableList.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts()
    {
        return ImmutableList.of(this.leftWing, this.rightWing);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        // FairyWingsLayer calls #setupGlideAnim directly instead, right before rendering.
    }

    public void setupGlideAnim(T entity, boolean gliding)
    {
        float x = CLOSED_X;
        float z = CLOSED_Z;
        if (gliding)
        {
            x = OPEN_X;
            // A slow, deep beat - a queen is in no hurry.
            z = OPEN_Z + (float) Math.sin(entity.tickCount * 0.7F) * 0.28F;
        }
        this.leftWing.xRot = x;
        this.leftWing.zRot = z;
        this.rightWing.xRot = x;
        this.rightWing.zRot = -z;
        // The tails trail a little behind the beat.
        float sway = gliding ? (float) Math.sin(entity.tickCount * 0.7F - 0.8F) * 0.12F : 0.0F;
        this.leftTail.zRot = TAIL_SWEEP + sway;
        this.rightTail.zRot = -TAIL_SWEEP - sway;
    }
}
