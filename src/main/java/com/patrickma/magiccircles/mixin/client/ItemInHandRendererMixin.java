package com.patrickma.magiccircles.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.patrickma.magiccircles.item.HeartstoneItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A heartstone glows in the hand that holds it. Everything held - in your own first-person view,
 * in another player's hand, in a fairy's - is drawn through {@link ItemInHandRenderer#renderItem},
 * so a heartstone passing through there is simply drawn at full brightness, whatever the light
 * around it. (It lights nothing else: the world's light comes from blocks, and a held item isn't one.)
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin
{
    @ModifyArg(method = "renderItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V"),
            index = 7)
    private int magiccircles$heartstoneGlows(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                             PoseStack poseStack, MultiBufferSource buffer, Level level, int light, int overlay, int seed)
    {
        return stack.getItem() instanceof HeartstoneItem ? LightTexture.FULL_BRIGHT : light;
    }
}
