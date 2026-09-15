package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.FairyRealmShield;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.network.RealmShieldStrikePacket;
import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets you strike the Fairy Realm's boundary. It is pure geometry - there is nothing there for a
 * swing to land on - so without this a punch at it simply went nowhere and nothing happened. Here,
 * any attack whose reach crosses the boundary before it meets anything else is caught: the arm still
 * swings, but instead of reaching whatever lies beyond, the server is told, and the boundary ripples
 * where it was struck (see {@link FairyRealmShield#strikeFrom}).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RealmShieldInput
{
    /** Survival block reach - how far a swing at empty air can be taken to go. */
    private static final double STRIKE_REACH = 4.5;
    /** Holding the attack button re-fires every tick; the boundary is struck at a punch's pace, not that. */
    private static final int STRIKE_INTERVAL_TICKS = 5;

    private static int lastStrikeTick = Integer.MIN_VALUE / 2;

    private RealmShieldInput()
    {
    }

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (!event.isAttack())
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }

        Vec3 eye = player.getEyePosition();
        // As far as the swing would actually go: up to whatever the crosshair is on, or full reach at empty air.
        double reach = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS
                ? mc.hitResult.getLocation().distanceTo(eye)
                : STRIKE_REACH;
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        if (FairyRealmShield.firstCrossing(eye, end) == null)
        {
            return;
        }

        // Whatever lay beyond is out of reach - the swing lands on the boundary instead.
        event.setCanceled(true);
        event.setSwingHand(true);
        if (player.tickCount - lastStrikeTick >= STRIKE_INTERVAL_TICKS)
        {
            lastStrikeTick = player.tickCount;
            ModNetworking.CHANNEL.sendToServer(new RealmShieldStrikePacket());
        }
    }
}
