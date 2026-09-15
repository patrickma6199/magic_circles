package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.FairyTrades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when a ghost accepts the Fairy Queen's bargain. The server checks that they
 * really are dead and really are before her - see {@link FairyTrades#acceptBargain}.
 */
public final class QueenBargainPacket
{
    private final int entityId;

    public QueenBargainPacket(int entityId)
    {
        this.entityId = entityId;
    }

    public static void encode(QueenBargainPacket packet, FriendlyByteBuf buf)
    {
        buf.writeVarInt(packet.entityId);
    }

    public static QueenBargainPacket decode(FriendlyByteBuf buf)
    {
        return new QueenBargainPacket(buf.readVarInt());
    }

    public static void handle(QueenBargainPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                FairyTrades.acceptBargain(player, packet.entityId);
            }
        });
        context.setPacketHandled(true);
    }
}
