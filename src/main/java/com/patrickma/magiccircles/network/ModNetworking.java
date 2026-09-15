package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * This mod's one and only network channel. It carries {@link CloseBookPacket} - a reader putting a
 * placed book down, so everyone watching sees it close - and {@link ToggleFairyFlightPacket}, the
 * client->server signal for "I just double-tapped jump while Blessed by the Wellspring" (see
 * {@code client/FairyFlightInput}). Everything else in this mod is either purely visual/client-
 * local, or a one-time world edit the server does on its own - this is the only place a genuine
 * player *input gesture* (something only the client can observe) needs to reach the server at all.
 */
public final class ModNetworking
{
    private static final String PROTOCOL_VERSION = "8";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MagicCircles.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private ModNetworking()
    {
    }

    public static void register()
    {
        CHANNEL.registerMessage(nextId++, ToggleFairyFlightPacket.class,
                ToggleFairyFlightPacket::encode, ToggleFairyFlightPacket::decode, ToggleFairyFlightPacket::handle);
        CHANNEL.registerMessage(nextId++, CloseBookPacket.class,
                CloseBookPacket::encode, CloseBookPacket::decode, CloseBookPacket::handle);
        CHANNEL.registerMessage(nextId++, PoltergeistPacket.class,
                PoltergeistPacket::encode, PoltergeistPacket::decode, PoltergeistPacket::handle);
        CHANNEL.registerMessage(nextId++, PoltergeistStatusPacket.class,
                PoltergeistStatusPacket::encode, PoltergeistStatusPacket::decode, PoltergeistStatusPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, RealmShieldStrikePacket.class,
                RealmShieldStrikePacket::encode, RealmShieldStrikePacket::decode, RealmShieldStrikePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, OpenFairyDialoguePacket.class,
                OpenFairyDialoguePacket::encode, OpenFairyDialoguePacket::decode, OpenFairyDialoguePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, FairyTradePacket.class,
                FairyTradePacket::encode, FairyTradePacket::decode, FairyTradePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, QueenBargainPacket.class,
                QueenBargainPacket::encode, QueenBargainPacket::decode, QueenBargainPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, WardHoverPacket.class,
                WardHoverPacket::encode, WardHoverPacket::decode, WardHoverPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }
}
