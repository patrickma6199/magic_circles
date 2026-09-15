package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.FairyRealmShield;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client->server when a player swings at the Fairy Realm's boundary (see {@code
 * client/RealmShieldInput}). Empty on purpose: the server works out for itself, from the player's
 * own eye and look, whether the swing really reached the boundary and where - see {@link
 * FairyRealmShield#strikeFrom}.
 */
public final class RealmShieldStrikePacket
{
    public static void encode(RealmShieldStrikePacket packet, FriendlyByteBuf buf)
    {
    }

    public static RealmShieldStrikePacket decode(FriendlyByteBuf buf)
    {
        return new RealmShieldStrikePacket();
    }

    public static void handle(RealmShieldStrikePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                FairyRealmShield.strikeFrom(player);
            }
        });
        context.setPacketHandled(true);
    }
}
