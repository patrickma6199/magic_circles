package com.patrickma.magiccircles.mixin.client;

import com.patrickma.magiccircles.client.FairyHover;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A gliding player's head is tipped up 45 degrees by vanilla, which is right for someone lying
 * flat in the air and wrong for someone hovering upright (see {@code PlayerRendererMixin}): their
 * head follows their look, as a fairy's does.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin
{
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void magiccircles$hoverHead(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci)
    {
        if (entity instanceof Player player && FairyHover.isHovering(player))
        {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            model.head.xRot = headPitch * ((float) Math.PI / 180F);
            model.hat.copyFrom(model.head);
        }
    }
}
