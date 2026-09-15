package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.limbo.Poltergeist;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when a ghost presses one of the poltergeist key bindings (see {@code
 * client/PoltergeistInput}). The server decides whether they are a poltergeist at all and whether
 * the power is ready - see {@link Poltergeist#useAbility}.
 */
public final class PoltergeistPacket
{
    private final byte ability;

    public PoltergeistPacket(byte ability)
    {
        this.ability = ability;
    }

    public static void encode(PoltergeistPacket packet, FriendlyByteBuf buf)
    {
        buf.writeByte(packet.ability);
    }

    public static PoltergeistPacket decode(FriendlyByteBuf buf)
    {
        return new PoltergeistPacket(buf.readByte());
    }

    public static void handle(PoltergeistPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                Poltergeist.useAbility(player, packet.ability);
            }
        });
        context.setPacketHandled(true);
    }
}
