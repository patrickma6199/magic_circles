package com.patrickma.magiccircles.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent server->client to a Blessed player the moment their Shield ward closes round them (see
 * {@code FairyWard#raise}): open your wings and hang still in the middle of it, the way a fairy does.
 * Hovering is the client's own flight state ({@code FairyFlightManager#isHovering}), so the server
 * can only ask for it - see {@code client/WardHoverClient}.
 */
public final class WardHoverPacket
{
    public WardHoverPacket()
    {
    }

    public static void encode(WardHoverPacket packet, FriendlyByteBuf buf)
    {
    }

    public static WardHoverPacket decode(FriendlyByteBuf buf)
    {
        return new WardHoverPacket();
    }

    public static void handle(WardHoverPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.patrickma.magiccircles.client.WardHoverClient.hover()));
        context.setPacketHandled(true);
    }
}
