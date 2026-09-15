package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.FairyFlightManager;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.network.ToggleFairyFlightPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * When the fairy wings open and close. Only the client sees key presses and its own fall as they
 * happen, so this is where those moments are noticed:
 *
 * <ul>
 *   <li>Double-tapping jump - two presses within {@value #DOUBLE_TAP_TICKS} ticks, creative
 *       flight's own rhythm - opens them once you are off the ground.</li>
 *   <li>Falling {@value #AUTO_OPEN_FALL} blocks opens them on their own, so stepping off a ledge
 *       catches you rather than dropping you.</li>
 *   <li>Holding back brakes you to a standstill, and you hover there on beating wings until you
 *       push forward again (see {@code FairyFlightManager#isHovering}).</li>
 *   <li>Jumping while gliding folds them, and you drop.</li>
 *   <li>Touching down on anything you could stand on folds them too - that is landing.</li>
 * </ul>
 *
 * <p>Opening sends vanilla's own start-gliding command, exactly as a real elytra does; the server
 * agrees because of {@code mixin/PlayerMixin}. Folding has no vanilla command to borrow, so it uses
 * {@link ToggleFairyFlightPacket}. Both happen locally at once as well, so the wings respond on the
 * very press rather than a round trip later.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FairyFlightInput
{
    /** "Falling off of a block down 5 or more blocks will open the elytra fairy wings." */
    private static final float AUTO_OPEN_FALL = 5.0f;
    /** How far below your feet counts as ground underneath you. */
    private static final double LANDING_PROBE = 0.1;
    /** Climbing faster than this is taking off, not landing, even with ground just below. */
    private static final double LANDING_MAX_RISE = 0.05;

    private static final int DOUBLE_TAP_TICKS = 7;
    private static final int NO_TAP = Integer.MIN_VALUE / 2;

    private static boolean wasJumpDown;
    private static int lastJumpPressTick = NO_TAP;

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
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null)
        {
            return;
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !wasJumpDown;
        wasJumpDown = jumpDown;

        boolean doubleTapped = false;
        if (jumpPressed)
        {
            doubleTapped = player.tickCount - lastJumpPressTick <= DOUBLE_TAP_TICKS;
            // A finished double-tap is used up, so a third press starts a new pair.
            lastJumpPressTick = doubleTapped ? NO_TAP : player.tickCount;
        }

        if (player.isFallFlying())
        {
            if (FairyFlightManager.canGlide(player) && (jumpPressed || touchingDown(player)))
            {
                foldWings(player);
            }
            return;
        }

        FairyFlightManager.endHover();
        if (player.onClimbable() || !FairyFlightManager.canKeepGliding(player))
        {
            return;
        }
        if (doubleTapped || player.fallDistance >= AUTO_OPEN_FALL)
        {
            openWings(mc, player);
        }
    }

    /**
     * Whether a glider has come down onto something they could stand on. Fairy flight has no
     * gravity, so gliding in level with the ground never registers as landing on it by itself -
     * the wings would stay open with your feet already on the grass.
     */
    private static boolean touchingDown(LocalPlayer player)
    {
        return player.getDeltaMovement().y <= LANDING_MAX_RISE
                && !player.level().noCollision(player, player.getBoundingBox().move(0.0, -LANDING_PROBE, 0.0));
    }

    private static void foldWings(LocalPlayer player)
    {
        // The press that folded them never counts toward reopening them.
        lastJumpPressTick = NO_TAP;
        player.stopFallFlying();
        ModNetworking.CHANNEL.sendToServer(new ToggleFairyFlightPacket());
    }

    private static void openWings(Minecraft mc, LocalPlayer player)
    {
        if (player.tryToStartFallFlying())
        {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }
}
