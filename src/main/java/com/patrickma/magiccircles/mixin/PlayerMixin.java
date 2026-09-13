package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.limbo.Veil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the dead right-click the Ferryman.
 *
 * <p>Vanilla's {@code Player#interactOn} opens with "if I am a spectator, open a menu if there is
 * one and otherwise do nothing." Everything else already works: the client sends the interaction
 * packet even in spectator mode, and the server's own packet handler processes it without checking
 * game mode - this one guard is the only thing in the way. Bypassing it purely for veil-to-veil
 * interaction is what makes the whole rescue mechanic survive the move to real spectator mode,
 * without giving spectators any ability to touch the ordinary world.
 */
@Mixin(Player.class)
public abstract class PlayerMixin
{
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void magiccircles$veilInteract(Entity target, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir)
    {
        Player self = (Player) (Object) this;
        if (self.isSpectator() && Veil.isBehindVeil(self) && Veil.isBehindVeil(target))
        {
            cir.setReturnValue(target.interact(self, hand));
        }
    }
}
