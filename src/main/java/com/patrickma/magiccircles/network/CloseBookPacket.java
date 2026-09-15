package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.block.entity.ReadableBookBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when a reader closes a placed book's screen, so the book closes for everyone
 * else watching too - see {@link ReadableBookBlockEntity}. Opening needs no packet of its own: the
 * block's right-click already reaches the server.
 */
public final class CloseBookPacket
{
    private final BlockPos pos;

    public CloseBookPacket(BlockPos pos)
    {
        this.pos = pos;
    }

    public static void encode(CloseBookPacket packet, FriendlyByteBuf buf)
    {
        buf.writeBlockPos(packet.pos);
    }

    public static CloseBookPacket decode(FriendlyByteBuf buf)
    {
        return new CloseBookPacket(buf.readBlockPos());
    }

    public static void handle(CloseBookPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            // isLoaded first, so a packet can never make the server load a chunk.
            if (player != null && player.level().isLoaded(packet.pos)
                    && player.level().getBlockEntity(packet.pos) instanceof ReadableBookBlockEntity book)
            {
                book.stopReading(player);
            }
        });
        context.setPacketHandled(true);
    }
}
