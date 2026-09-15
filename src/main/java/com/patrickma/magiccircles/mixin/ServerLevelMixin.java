package com.patrickma.magiccircles.mixin;

import com.patrickma.magiccircles.FairyRealmWeather;
import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives the Fairy Realm weather of its own. Vanilla hands every dimension but the overworld the
 * overworld's weather - its level data is a read-only view of the overworld's, and setting the
 * weather there quietly does nothing - so the realm could only ever rain when the overworld did.
 *
 * <p>{@code advanceWeatherCycle} is where a level decides how hard it is raining: it eases its rain
 * level toward whatever its level data says, and tells the players there. Asked instead whether the
 * realm is raining ({@link FairyRealmWeather#isRaining}), vanilla does all the rest itself - the rain
 * level, the packets, the rain the players see and hear.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin
{
    @Redirect(method = "advanceWeatherCycle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WritableLevelData;isRaining()Z"))
    private boolean magiccircles$fairyRealmRain(WritableLevelData data)
    {
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            return FairyRealmWeather.isRaining();
        }
        return data.isRaining();
    }
}
