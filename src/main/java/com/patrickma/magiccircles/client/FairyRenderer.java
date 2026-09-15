package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.FairyEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * A fairy, drawn with the player's own model - so every walking, looking and arm-swinging
 * animation is exactly a player's - in one of two floral skins (see {@code
 * tools/gen_fairy_textures.py}), slim-armed for women the way Alex is. The fairy wings are always
 * on (see {@link FairyWingsLayer}), and in fast flight the whole body leans into it the way a
 * player on an elytra does.
 */
public class FairyRenderer extends HumanoidMobRenderer<FairyEntity, PlayerModel<FairyEntity>>
{
    /** One skin per colour and sex - see tools/gen_fairy_textures.py. */
    private static final java.util.Map<String, ResourceLocation> TEXTURES = new java.util.HashMap<>();
    /** Roughly the body's middle, so leaning pivots about the fairy's centre rather than its feet. */
    private static final float LEAN_PIVOT = 0.9f;

    private final PlayerModel<FairyEntity> wideModel;
    private final PlayerModel<FairyEntity> slimModel;

    public FairyRenderer(EntityRendererProvider.Context context)
    {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4f);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new FairyWingsLayer<>(this, context.getModelSet()));
        // A fairy's ghost still has its wings (see FairyWingsLayer), but whatever it carried stays
        // with the living - no solid Heartstone hanging beside a see-through fairy.
        this.layers.removeIf(layer -> layer instanceof net.minecraft.client.renderer.entity.layers.ItemInHandLayer);
        this.addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this, context.getItemInHandRenderer())
        {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, FairyEntity fairy,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch)
            {
                if (!fairy.isInvisible())
                {
                    super.render(poseStack, buffers, packedLight, fairy, limbSwing, limbSwingAmount, partialTick,
                            ageInTicks, netHeadYaw, headPitch);
                }
            }
        });
    }

    @Override
    public void render(FairyEntity fairy, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight)
    {
        this.model = fairy.isFemale() ? this.slimModel : this.wideModel;
        super.render(fairy, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FairyEntity fairy)
    {
        String key = fairy.color().getSerializedName() + (fairy.isFemale() ? "_female" : "_male");
        return TEXTURES.computeIfAbsent(key, k -> new ResourceLocation(MagicCircles.MOD_ID, "textures/entity/fairy/fairy_" + k + ".png"));
    }

    @Override
    protected void setupRotations(FairyEntity fairy, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
        super.setupRotations(fairy, poseStack, ageInTicks, rotationYaw, partialTick);
        float lean = fairy.lean(partialTick);
        if (lean > 0.01f)
        {
            // Negative about X tips the head forward, toward the way the body faces.
            poseStack.translate(0.0f, LEAN_PIVOT, 0.0f);
            poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
            poseStack.translate(0.0f, -LEAN_PIVOT, 0.0f);
        }
    }
}
