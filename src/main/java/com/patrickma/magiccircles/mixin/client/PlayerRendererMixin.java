package com.patrickma.magiccircles.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.FairyFlightManager;
import com.patrickma.magiccircles.client.FairyHover;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hovering player stands upright in the air, the way a hovering fairy does. Vanilla lays a
 * gliding player out flat along their look direction and rolls them into their motion - with no
 * motion at all that pose just freezes wherever it was. While they hover (see {@link FairyHover})
 * the glide pose is skipped entirely and only the body's own facing is applied.
 *
 * <p>A glider's legs also stay put: vanilla marks the local player as crouching whenever a full
 * standing hitbox wouldn't fit where they are - which, gliding through the branches on the small
 * fall-flying box, is most of the time - and that shoves the legs into the sneaking offset with
 * every leaf that passes. On fairy wings that flag is simply not drawn.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin
{
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void magiccircles$noCrouchOnTheWing(AbstractClientPlayer player, CallbackInfo ci)
    {
        if (player.isFallFlying() && FairyFlightManager.canGlide(player))
        {
            ((PlayerRenderer) (Object) this).getModel().crouching = false;
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"), cancellable = true)
    private void magiccircles$hoverUpright(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                           float partialTicks, CallbackInfo ci)
    {
        if (FairyHover.isHovering(player))
        {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
            ci.cancel();
        }
    }
}
