package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.FairyFlightManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when a gliding player jumps to fold their fairy wings (see {@code
 * client/FairyFlightInput}) - empty payload, a pure event. Opening the wings needs no packet of
 * our own: the client sends vanilla's own start-gliding command, which the server runs through
 * {@code Player#tryToStartFallFlying} (see {@code mixin/PlayerMixin}). Vanilla has no matching
 * "stop" command, which is the only reason this exists.
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
                FairyFlightManager.foldWings(player);
            }
        });
        context.setPacketHandled(true);
    }
}
