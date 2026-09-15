package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.entity.PlayerCorpseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Draws a {@link PlayerCorpseEntity} as the player it came from - real skin, real armor, real held
 * item - lying flat on the ground rather than standing like the ArmorStand this used to be.
 *
 * <p>The skin is taken straight from the client's own player list ({@link PlayerInfo}), which
 * already holds the downloaded texture for anyone who is or recently was connected - no extra
 * fetch, no profile lookup. A body whose owner has since logged off falls back to the default
 * skin for their UUID, the same Steve/Alex choice vanilla makes for any unknown player.
 */
public class PlayerCorpseRenderer extends LivingEntityRenderer<PlayerCorpseEntity, PlayerModel<PlayerCorpseEntity>>
{
    private final PlayerModel<PlayerCorpseEntity> wideModel;
    private final PlayerModel<PlayerCorpseEntity> slimModel;

    public PlayerCorpseRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(PlayerCorpseEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int packedLight)
    {
        this.model = entity.hasSlimArms() ? this.slimModel : this.wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerCorpseEntity entity)
    {
        UUID owner = entity.getOwnerUuid();
        if (owner == null)
        {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        if (Minecraft.getInstance().getConnection() != null)
        {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(owner);
            if (info != null)
            {
                return info.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(owner);
    }

    /**
     * Tips the whole body onto its back. This runs before {@code LivingEntityRenderer}'s own
     * flip-and-drop, so here the model is still upright with its origin at the feet: rotating about
     * X lays it out along the ground, and the small lift afterwards keeps it from z-fighting with
     * the block it's resting on.
     */
    @Override
    protected void setupRotations(PlayerCorpseEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks)
    {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        // After that rotation, local -Z is world up, so this small negative lifts the body just
        // clear of the ground rather than z-fighting with it. It was a full -0.95 before, which is
        // exactly why the body floated a block off the floor.
        poseStack.translate(0.0f, 0.0f, -0.05f);
    }

    @Override
    protected boolean shouldShowName(PlayerCorpseEntity entity)
    {
        return entity.isCustomNameVisible();
    }

    /**
     * Puts the name over the body's head rather than its feet.
     *
     * <p>Vanilla hangs a nameplate at {@code bbHeight + 0.5}, which assumes the thing it is naming
     * is standing up. A corpse is lying down, so that lands on the ground at the foot end. The body
     * is tipped backwards out of its facing direction (see {@link #setupRotations}), which puts the
     * head roughly {@link #HEAD_REACH} blocks behind where the entity nominally stands - so the
     * plate is walked back along that axis and lifted clear of the ground.
     *
     * <p>This runs outside {@code setupRotations}, in the ordinary world-aligned frame, so the
     * offset is computed straight from the body's yaw rather than inherited from that rotation.
     */
    @Override
    protected void renderNameTag(PlayerCorpseEntity entity, net.minecraft.network.chat.Component displayName,
                                 PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
                                 int packedLight)
    {
        // The very angle setupRotations turned the body by, so the plate always follows the head.
        float yaw = entity.yBodyRot * ((float) Math.PI / 180.0f);
        double dx = Math.sin(yaw) * HEAD_REACH;
        double dz = -Math.cos(yaw) * HEAD_REACH;

        poseStack.pushPose();
        poseStack.translate(dx, NAME_LIFT - entity.getNameTagOffsetY(), dz);
        super.renderNameTag(entity, displayName, poseStack, buffers, packedLight);
        poseStack.popPose();
    }

    /** How far behind the body's own position its head ends up once it is lying down. */
    private static final double HEAD_REACH = 1.1;
    /** Height the plate floats at above the ground, replacing vanilla's standing-height assumption. */
    private static final double NAME_LIFT = 0.9;
}
