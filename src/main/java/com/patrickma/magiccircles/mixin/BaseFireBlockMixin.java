package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.FairyRealmWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No fire in the Fairy Realm, from any source - both kinds, ordinary and soul.
 *
 * <p>Refusing the player's lighter was never enough: fire spreads, lava lights what it touches,
 * lightning and fireballs set blocks alight, and none of that goes through a player. Every one of
 * those ends in a fire block being set, and every fire block's {@code onPlace} runs when it is - so
 * that is where it goes out, removed in the same instant it appears (exactly how vanilla removes
 * fire that can't survive where it was put), before any client is ever told it was there.
 * {@code canBePlacedAt} is asked first by flint and steel, fire charges and dispensers, so those
 * simply fail rather than lighting something that then vanishes.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin
{
    @Inject(method = "canBePlacedAt", at = @At("HEAD"), cancellable = true)
    private static void magiccircles$noFireToPlace(Level level, BlockPos pos, Direction direction,
                                                   CallbackInfoReturnable<Boolean> cir)
    {
        if (FairyRealmWeather.isFireless(level))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void magiccircles$goOut(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving,
                                    CallbackInfo ci)
    {
        if (!level.isClientSide && FairyRealmWeather.isFireless(level))
        {
            level.removeBlock(pos, false);
            ci.cancel();
        }
    }
}
