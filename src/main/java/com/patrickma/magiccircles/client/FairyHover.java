package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.FairyFlightManager;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a Blessed player is hanging still in the air on their wings (see {@link
 * FairyFlightManager#isHovering}). The local player knows for certain; anyone else is judged by
 * whether they are moving. Hovering is drawn and heard as a fairy's flight is - upright on beating
 * wings ({@code mixin/client/PlayerRendererMixin}, {@link FairyWingsModel}), with the flutter
 * ({@link PlayerWingSounds}) - where ordinary flight keeps vanilla's glide pose and elytra rush.
 */
public final class FairyHover
{
    private static final double STILL_SQR = 0.004;

    private FairyHover()
    {
    }

    public static boolean isHovering(Player player)
    {
        if (!player.isFallFlying() || !player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            return false;
        }
        if (player == Minecraft.getInstance().player)
        {
            return FairyFlightManager.isHovering();
        }
        return player.getDeltaMovement().lengthSqr() < STILL_SQR;
    }
}
