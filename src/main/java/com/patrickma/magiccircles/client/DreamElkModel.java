package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.DreamElkEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Dream Elk's own Blockbench model - the mesh in {@link #createBodyLayer} is the export,
 * transcribed unchanged, and replaces the reskinned-vanilla-horse-plus-grafted-antlers stand-in
 * this used to be.
 *
 * <p>Being a {@link HierarchicalModel} is what lets the keyframe animations in {@link
 * DreamElkAnimation} drive it. Those animations are additive onto whatever pose a part is already
 * in, which is why {@link #setupAnim} resets every part first and applies head-tracking before
 * animating rather than after - a grazing elk should still be looking at the ground, not snapped
 * back at whoever walked past.
 */
public class DreamElkModel<T extends DreamElkEntity> extends HierarchicalModel<T>
{
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(MagicCircles.MOD_ID, "dream_elk"), "main");

    /** How far the head is allowed to turn to track something, in degrees. */
    private static final float HEAD_YAW_LIMIT = 45.0f;
    private static final float HEAD_PITCH_LIMIT = 30.0f;

    private final ModelPart root;
    private final ModelPart head;

    public DreamElkModel(ModelPart root)
    {
        this.root = root;
        this.head = root.getChild("bone9").getChild("bone10").getChild("bone7").getChild("bone6");
    }

    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone9 = partdefinition.addOrReplaceChild("bone9", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 1.5F));

        bone9.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(70, 0).addBox(-1.5F, -2.25F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(32, 56).addBox(-1.5F, 5.75F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, -14.0F, -12.0F));

        bone9.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(16, 64).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(70, 11).addBox(-1.5F, 5.75F, -1.5F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, -13.0F, 12.0F));

        bone9.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(58, 11).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(40, 70).addBox(-1.5F, 6.0F, -1.5F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, -13.0F, 12.0F));

        bone9.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(28, 67).addBox(-1.5F, -2.25F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(58, 0).addBox(-1.5F, 5.75F, -1.5F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, -14.0F, -12.0F));

        PartDefinition bone10 = bone9.addOrReplaceChild("bone10", CubeListBuilder.create(), PartPose.offset(0.0F, -20.0F, 14.5F));

        PartDefinition bone7 = bone10.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(0, 56).addBox(-2.0F, -12.0F, -1.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        bone7.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(50, 52).addBox(-4.0F, -7.0F, -5.0F, 8.0F, 8.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(57, 36).addBox(-3.0F, -4.0F, -1.0F, 6.0F, 4.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(52, 70).addBox(-4.0F, -10.0F, 1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(64, 70).addBox(-5.0F, -12.0F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 79).addBox(-7.0F, -13.0F, -3.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(10, 80).addBox(-9.0F, -14.0F, -4.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(40, 80).addBox(-11.0F, -13.0F, -5.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(50, 81).addBox(-12.0F, -12.0F, -6.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(60, 81).addBox(-13.0F, -10.0F, -7.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(70, 81).addBox(-12.0F, -8.0F, -8.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(80, 81).addBox(-11.0F, -6.0F, -9.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(82, 0).addBox(-9.0F, -5.0F, -10.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(58, 22).addBox(1.0F, -10.0F, 1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(70, 21).addBox(2.0F, -12.0F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 74).addBox(5.0F, -13.0F, -3.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(10, 75).addBox(7.0F, -14.0F, -4.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(52, 76).addBox(9.0F, -13.0F, -5.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(62, 76).addBox(10.0F, -12.0F, -6.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(76, 70).addBox(11.0F, -10.0F, -7.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(72, 76).addBox(10.0F, -8.0F, -8.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(20, 78).addBox(9.0F, -6.0F, -9.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(30, 78).addBox(7.0F, -5.0F, -10.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, 2.0F));

        bone10.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(0, 30).addBox(-6.0F, -7.0F, 4.0F, 12.0F, 13.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-5.0F, -6.0F, -14.0F, 10.0F, 11.0F, 19.0F, new CubeDeformation(0.0F))
                .texOffs(16, 56).addBox(-2.0F, -8.0F, -17.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -17.0F));

        partdefinition.addOrReplaceChild("bone8", CubeListBuilder.create(), PartPose.offset(0.0F, 3.0F, 5.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public ModelPart root()
    {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // Negated because the model faces +Z rather than vanilla's -Z, so DreamElkRenderer spins
        // the finished result 180 degrees - without this the head would track the wrong way.
        this.head.yRot = -Mth.clamp(netHeadYaw, -HEAD_YAW_LIMIT, HEAD_YAW_LIMIT) * Mth.DEG_TO_RAD;
        this.head.xRot = -Mth.clamp(headPitch, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT) * Mth.DEG_TO_RAD;

        // Driven by how fast it's actually moving rather than by wall-clock time, so the legs keep
        // pace with the animal instead of sliding.
        this.animateWalk(DreamElkAnimation.WALKING, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        this.animate(entity.idleAnimationState, DreamElkAnimation.IDLE, ageInTicks);
        this.animate(entity.eatAnimationState, DreamElkAnimation.EATING, ageInTicks);
        this.animate(entity.deathAnimationState, DreamElkAnimation.DEATH, ageInTicks);
    }
}
