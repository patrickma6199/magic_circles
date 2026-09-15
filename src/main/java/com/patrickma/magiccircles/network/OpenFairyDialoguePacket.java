package com.patrickma.magiccircles.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent server->client when a player right-clicks a fairy (see {@code FairyTrades#openDialogue}) -
 * the server decides what the conversation is, which is the only way the dead can open one at all:
 * a spectator's client never runs the interaction itself, only sends it.
 */
public final class OpenFairyDialoguePacket
{
    private final int entityId;
    private final boolean queen;
    private final boolean bargain;
    /** The one thing this fairy sells - an index into {@code FairyTrades#ALL}. */
    private final int trade;

    public OpenFairyDialoguePacket(int entityId, boolean queen, boolean bargain, int trade)
    {
        this.entityId = entityId;
        this.queen = queen;
        this.bargain = bargain;
        this.trade = trade;
    }

    public static void encode(OpenFairyDialoguePacket packet, FriendlyByteBuf buf)
    {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.queen);
        buf.writeBoolean(packet.bargain);
        buf.writeVarInt(packet.trade);
    }

    public static OpenFairyDialoguePacket decode(FriendlyByteBuf buf)
    {
        return new OpenFairyDialoguePacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(OpenFairyDialoguePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.patrickma.magiccircles.client.FairyDialogueOpener.open(packet.entityId, packet.queen, packet.bargain, packet.trade)));
        context.setPacketHandled(true);
    }
}
