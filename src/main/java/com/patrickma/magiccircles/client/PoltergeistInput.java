package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.limbo.Poltergeist;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.network.PoltergeistPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sends a ghost's poltergeist key presses to the server, which decides whether they are a
 * poltergeist and whether the power is ready. Presses while alive are simply swallowed.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PoltergeistInput
{
    private PoltergeistInput()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean ghost = player != null && mc.getConnection() != null && GhostHud.isGhost(player);
        while (GhostHud.CALL_RAIN.consumeClick())
        {
            if (ghost)
            {
                ModNetworking.CHANNEL.sendToServer(new PoltergeistPacket(Poltergeist.CALL_RAIN));
            }
        }
        while (GhostHud.RAISE_SKELETON.consumeClick())
        {
            if (ghost)
            {
                ModNetworking.CHANNEL.sendToServer(new PoltergeistPacket(Poltergeist.RAISE_SKELETON));
            }
        }
    }
}
