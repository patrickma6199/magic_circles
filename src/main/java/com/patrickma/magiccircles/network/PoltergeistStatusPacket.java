package com.patrickma.magiccircles.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent server->client: whether this player is a poltergeist, and how many ticks each of its powers
 * still needs before it can be used again - everything {@code client/GhostHud} needs to draw the
 * ability slots. See {@code limbo/Poltergeist#sync}.
 */
public final class PoltergeistStatusPacket
{
    private final boolean poltergeist;
    private final int rainTicks;
    private final int skeletonTicks;

    public PoltergeistStatusPacket(boolean poltergeist, int rainTicks, int skeletonTicks)
    {
        this.poltergeist = poltergeist;
        this.rainTicks = rainTicks;
        this.skeletonTicks = skeletonTicks;
    }

    public static void encode(PoltergeistStatusPacket packet, FriendlyByteBuf buf)
    {
        buf.writeBoolean(packet.poltergeist);
        buf.writeVarInt(packet.rainTicks);
        buf.writeVarInt(packet.skeletonTicks);
    }

    public static PoltergeistStatusPacket decode(FriendlyByteBuf buf)
    {
        return new PoltergeistStatusPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PoltergeistStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.patrickma.magiccircles.client.PoltergeistClientState.update(
                        packet.poltergeist, packet.rainTicks, packet.skeletonTicks)));
        context.setPacketHandled(true);
    }
}
