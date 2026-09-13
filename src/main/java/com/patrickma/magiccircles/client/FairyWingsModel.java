package com.patrickma.magiccircles.client;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The same physical mesh as vanilla's own {@code ElytraModel} (reuses its {@code
 * ModelLayers.ELYTRA} bake layer - no new layer definition needed), but with its own animation
 * method instead of {@code setupAnim}: vanilla's own open/closed wing math is keyed off {@code
 * entity.isFallFlying()}, which this mod never sets (see {@code FairyFlightManager}'s own doc
 * comment for why forcing that flag would fight with forcing the *swimming* pose instead - the
 * two are mutually exclusive in vanilla's own {@code Player#updatePlayerPose}). {@link
 * #setupGlideAnim} takes an explicit {@code gliding} flag instead, so {@code FairyWingsLayer} can
 * drive the open-wing pose from whatever signal it wants (in practice, {@code isSwimming() &&
 * !isInWater()}) without needing the entity's pose to be FALL_FLYING at all.
 */
public class FairyWingsModel<T extends LivingEntity> extends AgeableListModel<T>
{
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public FairyWingsModel(ModelPart root)
    {
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
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
        // Intentionally does nothing - see this class's own doc comment. FairyWingsLayer always
        // calls #setupGlideAnim directly instead, right before rendering.
    }

    public void setupGlideAnim(T entity, boolean gliding)
    {
        float closedX = 0.2617994F;
        float closedZ = -0.2617994F;
        float openX = closedX;
        float openZ = closedZ;

        if (gliding)
        {
            float openness = 1.0F;
            Vec3 deltaMovement = entity.getDeltaMovement();
            if (deltaMovement.y < 0.0D)
            {
                Vec3 normalized = deltaMovement.normalize();
                openness = 1.0F - (float) Math.pow(-normalized.y, 1.5D);
            }
            openX = openness * 0.34906584F + (1.0F - openness) * closedX;
            openZ = openness * (-(float) Math.PI / 2F) + (1.0F - openness) * closedZ;
        }

        if (entity instanceof AbstractClientPlayer player)
        {
            player.elytraRotX += (openX - player.elytraRotX) * 0.1F;
            player.elytraRotZ += (openZ - player.elytraRotZ) * 0.1F;
            this.leftWing.xRot = player.elytraRotX;
            this.leftWing.zRot = player.elytraRotZ;
        }
        else
        {
            this.leftWing.xRot = openX;
            this.leftWing.zRot = openZ;
        }

        this.rightWing.xRot = this.leftWing.xRot;
        this.rightWing.zRot = -this.leftWing.zRot;
    }
}
