package com.patrickma.magiccircles.network;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * This mod's one and only network channel - carries {@link ToggleFairyFlightPacket}, the
 * client->server signal for "I just double-tapped jump while Blessed by the Wellspring" (see
 * {@code client/FairyFlightInput}). Everything else in this mod is either purely visual/client-
 * local, or a one-time world edit the server does on its own - this is the only place a genuine
 * player *input gesture* (something only the client can observe) needs to reach the server at all.
 */
public final class ModNetworking
{
    private static final String PROTOCOL_VERSION = "1";

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
    }
}
