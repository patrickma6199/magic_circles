package com.patrickma.magiccircles.mixin.client;

import com.patrickma.magiccircles.limbo.GhostVisibility;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps a rite caster's gear on their side of the veil.
 *
 * <p>Vanilla invisibility hides only the body: armor, held items, elytra and the rest are drawn by
 * render layers that don't care whether the wearer is invisible - which is why a caster's armor
 * stood around in the living world with nobody inside it. Vanilla already skips every one of those
 * layers for a spectator, which is why an ordinary ghost never showed any. A rite caster stays in
 * survival on purpose (they have to be able to fight), so this gives them the spectator's answer to
 * that one question and nothing else.
 *
 * <p>"Invisible and on the veil team" is exactly someone behind the veil. Deathsight puts the living
 * on the same team to let them see across, but never makes them invisible, so their own gear still
 * shows.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z"))
    private boolean magiccircles$hideGearBehindVeil(LivingEntity entity)
    {
        // A fairy's wings are part of it, not gear - its ghost keeps them, and still flies on them.
        // FairyRenderer draws them ghostly and keeps the rest of its layers to itself.
        return entity.isSpectator() || (entity.isInvisible() && GhostVisibility.isOnVeilTeam(entity)
                && !(entity instanceof com.patrickma.magiccircles.entity.FairyEntity));
    }
}
