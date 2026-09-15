package com.patrickma.magiccircles.client;

import net.minecraft.client.Minecraft;

/**
 * The client half of {@code network/OpenFairyDialoguePacket} - kept in its own class so the packet,
 * which the dedicated server loads too, never names a client-only type itself.
 */
public final class FairyDialogueOpener
{
    private FairyDialogueOpener()
    {
    }

    public static void open(int entityId, boolean queen, boolean bargain, int trade)
    {
        Minecraft.getInstance().setScreen(new FairyDialogueScreen(entityId, queen, bargain, trade));
    }
}
