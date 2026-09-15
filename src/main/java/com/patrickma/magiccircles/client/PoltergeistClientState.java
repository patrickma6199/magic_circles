package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client's copy of its own poltergeist status, as last told by {@code
 * network/PoltergeistStatusPacket}. Cooldowns count down locally between updates from the moment
 * they arrived, so the HUD ticks smoothly rather than jumping twice a second.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PoltergeistClientState
{
    private static boolean active;
    private static int rainTicks;
    private static int skeletonTicks;
    private static long receivedAt;

    private PoltergeistClientState()
    {
    }

    public static void update(boolean poltergeist, int rain, int skeleton)
    {
        active = poltergeist;
        rainTicks = rain;
        skeletonTicks = skeleton;
        receivedAt = gameTime();
    }

    public static boolean isActive()
    {
        return active;
    }

    /** Ticks until Call the Rain is ready again - 0 when it is. */
    public static float rainRemaining(float partialTick)
    {
        return remaining(rainTicks, partialTick);
    }

    /** Ticks until Raise a Skeleton is ready again - 0 when it is. */
    public static float skeletonRemaining(float partialTick)
    {
        return remaining(skeletonTicks, partialTick);
    }

    private static float remaining(int ticksWhenReceived, float partialTick)
    {
        return Math.max(0.0f, ticksWhenReceived - (gameTime() - receivedAt) - partialTick);
    }

    private static long gameTime()
    {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    /** Nothing carries over into the next server or world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        active = false;
        rainTicks = 0;
        skeletonTicks = 0;
    }
}
