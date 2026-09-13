package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.limbo.Veil;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same veil rule as {@code EntityMixin}, applied again because {@link ServerPlayer} overrides
 * {@code broadcastToPlayer} and would otherwise never reach the base version's injection.
 *
 * <p>This override is also the one that matters most. Vanilla's version reads, in effect, "if I am
 * a spectator, no non-spectator may ever see me" - unconditional, with no hook. That single line is
 * what made real spectator mode unusable for the dead: a rite caster, who has a living body and is
 * not a spectator, could never see the ghost they crossed over to rescue. Overriding it here is
 * what lets the dead simply *be* spectators, with vanilla providing flight, no-clip, invulnerability
 * and the inability to touch the world, instead of all of that being imitated by hand.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void magiccircles$veilVisibility(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir)
    {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (Veil.isBehindVeil(self))
        {
            cir.setReturnValue(Veil.visibleTo(self, viewer));
        }
    }
}
