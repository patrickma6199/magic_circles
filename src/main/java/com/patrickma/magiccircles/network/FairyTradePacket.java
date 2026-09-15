package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.FairyTrades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when a player picks a trade in a fairy's dialogue. The server checks
 * everything again before anything changes hands - see {@link FairyTrades#trade}.
 */
public final class FairyTradePacket
{
    private final int entityId;
    private final int index;

    public FairyTradePacket(int entityId, int index)
    {
        this.entityId = entityId;
        this.index = index;
    }

    public static void encode(FairyTradePacket packet, FriendlyByteBuf buf)
    {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.index);
    }

    public static FairyTradePacket decode(FriendlyByteBuf buf)
    {
        return new FairyTradePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FairyTradePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                FairyTrades.trade(player, packet.entityId, packet.index);
            }
        });
        context.setPacketHandled(true);
    }
}
