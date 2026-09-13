package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.limbo.Veil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the veil a real plane of existence rather than a cosmetic trick.
 *
 * <p>{@code broadcastToPlayer} is what decides whether an entity is tracked - i.e. sent - to a
 * given player at all ({@code ChunkMap.TrackedEntity#updatePlayer} calls it). Cancelling here means
 * a living player's client is never even told the entity exists, so there is nothing to render and
 * nothing to tick: no body, no nameplate, no particles, no phantom wing trail, no glowing eyes.
 * Every one of those leaked through when this was done with an invisibility effect instead.
 */
@Mixin(Entity.class)
public abstract class EntityMixin
{
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void magiccircles$veilVisibility(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir)
    {
        Entity self = (Entity) (Object) this;
        if (Veil.isBehindVeil(self))
        {
            cir.setReturnValue(Veil.visibleTo(self, viewer));
        }
    }
}
