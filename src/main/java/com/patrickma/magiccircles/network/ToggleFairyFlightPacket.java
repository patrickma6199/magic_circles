package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.FairyFlightManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server the instant the local player double-taps jump while Blessed by the
 * Wellspring and airborne (see {@code client/FairyFlightInput}) - empty payload, it's a pure
 * event, not data. {@link FairyFlightManager#toggleFairyFlight} does the actual toggling
 * server-side, with its own defensive re-checks (never trusts the client's word alone for
 * anything that matters).
 */
public final class ToggleFairyFlightPacket
{
    public static void encode(ToggleFairyFlightPacket packet, FriendlyByteBuf buf)
    {
    }

    public static ToggleFairyFlightPacket decode(FriendlyByteBuf buf)
    {
        return new ToggleFairyFlightPacket();
    }

    public static void handle(ToggleFairyFlightPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                FairyFlightManager.toggleFairyFlight(player);
            }
        });
        context.setPacketHandled(true);
    }
}
