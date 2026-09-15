package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.FairyFlightManager;
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
 * Two small permissions vanilla's {@link Player} withholds.
 *
 * <p>{@code interactOn}: lets the dead right-click the Ferryman. Vanilla opens it with "if I am a
 * spectator, open a menu if there is one and otherwise do nothing." The client still sends the
 * interaction packet in spectator mode and the server still processes it - that one guard is the
 * only thing in the way. Bypassing it purely for veil-to-veil interaction keeps the rescue working
 * without giving spectators any reach into the ordinary world.
 *
 * <p>{@code tryToStartFallFlying}: lets the Blessed open their fairy wings without an elytra. See
 * {@link LivingEntityMixin} for keeping them open and for how the flight itself behaves.
 */
@Mixin(Player.class)
public abstract class PlayerMixin
{
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void magiccircles$veilInteract(Entity target, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResult> cir)
    {
        Player self = (Player) (Object) this;
        // The Fairy Queen stands on both sides at once - the dead can speak to her too.
        if (self.isSpectator() && Veil.isBehindVeil(self)
                && (Veil.isBehindVeil(target) || target instanceof com.patrickma.magiccircles.entity.FairyQueenEntity))
        {
            cir.setReturnValue(target.interact(self, hand));
        }
    }

    /**
     * Vanilla only lets you start gliding if the chest slot holds a working elytra. The server runs
     * this same method when the client reports it has opened its wings, so passing it here is what
     * makes the server agree rather than snapping them shut again.
     */
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void magiccircles$openFairyWings(CallbackInfoReturnable<Boolean> cir)
    {
        Player self = (Player) (Object) this;
        if (!self.isFallFlying() && !self.onGround() && FairyFlightManager.canKeepGliding(self))
        {
            self.startFallFlying();
            cir.setReturnValue(true);
        }
    }

    /**
     * A player's own sounds - footsteps, hurt cries - from behind the veil, heard only there. Player
     * overrides {@code Entity#playSound}, so {@code EntityMixin}'s version never sees these.
     */
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void magiccircles$veilSounds(net.minecraft.sounds.SoundEvent sound, float volume, float pitch,
                                         org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci)
    {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide && Veil.isBehindVeil(self))
        {
            ci.cancel();
            com.patrickma.magiccircles.limbo.VeilSounds.play(self, sound, volume, pitch, self);
        }
    }
}
