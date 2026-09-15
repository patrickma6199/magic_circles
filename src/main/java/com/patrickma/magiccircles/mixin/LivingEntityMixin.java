package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.FairyFlightManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fairy wings, flown as a real elytra glide - the gliding pose and animation are vanilla's own,
 * only the permission to glide and the physics of it are ours.
 *
 * <p>Vanilla ties gliding to an elytra in the chest slot twice over: {@code updateFallFlying} cancels
 * the glide every tick unless the chest item can fly, and {@code travel}'s glide branch applies
 * elytra physics - a constant downward pull, and speed that bleeds away the moment you climb, which
 * is why an elytra can never simply fly straight up. Both are replaced here for a Blessed glider,
 * and nothing else.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
    /** Keeps the wings open without an elytra, for as long as the glider is Blessed and airborne. */
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void magiccircles$keepFairyWingsOpen(CallbackInfo ci)
    {
        LivingEntity self = (LivingEntity) (Object) this;
        // Only a glide already under way is kept going - opening the wings is FairyFlightInput's
        // call alone. Forcing them open here for anyone Blessed and airborne turned every ordinary
        // jump into a glide, and reopened them the instant they were folded.
        if (self instanceof Player player && self.isFallFlying() && FairyFlightManager.canKeepGliding(player))
        {
            ci.cancel();
        }
    }

    /**
     * Swaps elytra physics for fairy flight: you go wherever you are looking, up as readily as down,
     * with no gravity and no crash damage. Only the local player's own client actually moves - the
     * server's copy of a player is positioned by the client's movement packets anyway, so there it
     * just skips vanilla's glide branch (and the wall-impact damage that branch deals).
     */
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void magiccircles$fairyGlide(Vec3 input, CallbackInfo ci)
    {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || !self.isFallFlying() || !FairyFlightManager.canKeepGliding(player))
        {
            return;
        }
        if (self.level().isClientSide && self.isControlledByLocalInstance())
        {
            FairyFlightManager.steer(player, input);
        }
        ci.cancel();
    }

    /**
     * In flight the arms and legs hang still under the wings, as a fairy's do (see {@code
     * FairyEntity#calculateEntityAnimation}). Vanilla only damps the walking cycle by speed - enough
     * at an elytra's pace, not at a fairy glide's, where the limbs kept pumping through the air.
     */
    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void magiccircles$stillLimbsInFlight(boolean includeHeight, CallbackInfo ci)
    {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && self.isFallFlying() && FairyFlightManager.canGlide(player))
        {
            self.walkAnimation.update(0.0f, 0.4f);
            ci.cancel();
        }
    }

    /**
     * The local player's crosshair passes straight through anything on the other side of the veil
     * from them - see {@code client/VeilPicking}. Client-side only: what the server lets hit what is
     * untouched, so a rite caster's arrows still find the phantoms hunting them.
     */
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void magiccircles$pickOnlyYourOwnSide(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir)
    {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide && com.patrickma.magiccircles.client.VeilPicking.hiddenFromLocalPlayer(self))
        {
            cir.setReturnValue(false);
        }
    }
}
