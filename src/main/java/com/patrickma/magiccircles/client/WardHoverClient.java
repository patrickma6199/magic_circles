package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.FairyFlightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of {@code network/WardHoverPacket} - kept in its own class so the packet, which
 * the dedicated server loads too, never names a client-only type itself.
 */
public final class WardHoverClient
{
    private WardHoverClient()
    {
    }

    /** Wings open where the ward has lifted you, and you hang still in its heart - see {@link FairyFlightManager#isHovering}. */
    public static void hover()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !FairyFlightManager.canGlide(player))
        {
            return;
        }
        if (!player.isFallFlying())
        {
            player.startFallFlying();
        }
        player.setDeltaMovement(Vec3.ZERO);
        FairyFlightManager.beginHover();
    }
}
