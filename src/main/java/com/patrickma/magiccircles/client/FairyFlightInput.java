package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.network.ToggleFairyFlightPacket;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detects the double-tap-jump gesture that toggles fairy-flight - the exact same physical input
 * Creative mode's own flight toggle already uses, reimplemented here rather than reused because
 * vanilla's own version is hardcoded to Creative/Spectator only. Only the client sees raw key
 * timing, so this is what actually has to notice the gesture; the real toggle happens server-side
 * (see {@code FairyFlightManager#toggleFairyFlight}) once {@code
 * network/ToggleFairyFlightPacket} reports it.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FairyFlightInput
{
    // Matches vanilla's own double-tap window for the real Creative flight toggle.
    private static final int DOUBLE_TAP_WINDOW_TICKS = 7;

    private static boolean wasJumpDown;
    private static int ticksSinceLastPress = Integer.MAX_VALUE;

    private FairyFlightInput()
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
        Player player = mc.player;
        if (player == null)
        {
            return;
        }

        if (ticksSinceLastPress < Integer.MAX_VALUE)
        {
            ticksSinceLastPress++;
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        if (jumpDown && !wasJumpDown)
        {
            if (ticksSinceLastPress <= DOUBLE_TAP_WINDOW_TICKS
                    && !player.isCreative() && !player.isSpectator()
                    && player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
            {
                ModNetworking.CHANNEL.sendToServer(new ToggleFairyFlightPacket());
                ticksSinceLastPress = Integer.MAX_VALUE;
            }
            else
            {
                ticksSinceLastPress = 0;
            }
        }
        wasJumpDown = jumpDown;
    }
}
