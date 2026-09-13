package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The steering half of Blessed-by-the-Wellspring flight - {@link
 * com.patrickma.magiccircles.FairyFlightManager} (server-side) grants the local player real free
 * flight ({@code abilities.flying}) once toggled on by double-tapping jump (see {@code
 * FairyFlightInput}), which by itself only gives vanilla's own Creative-style controls (WASD
 * relative to yaw only, jump/sneak for up/down - looking up or down doesn't steer vertical
 * movement at all). This overrides that every client tick instead: "fly in the direction you aim
 * at," per what was asked for, the same way swimming lets you swim toward wherever the camera
 * points rather than only horizontally.
 *
 * <p>Client-only and local-player-only, by necessity - only the client actually knows which keys
 * are currently held. Directly setting {@link Player#setDeltaMovement} here, every tick, is what
 * actually overrides vanilla's own creative-flight steering: {@code TickEvent.ClientTickEvent}'s
 * {@code Phase.END} fires after the local player's own vanilla movement tick (creative-fly input
 * included) has already run for this tick, so this always gets the final say on what velocity
 * actually carries into next tick's position update - vanilla's own flat, yaw-only contribution
 * for this tick is simply overwritten before it can compound.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FairyFlightControl
{
    private static final double FLIGHT_SPEED = 0.6;
    private static final double SMOOTHING = 0.35;

    private FairyFlightControl()
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
        if (player == null || mc.level == null)
        {
            return;
        }
        if (!player.getAbilities().flying || !player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            return;
        }

        // The same swim-pose forcing FairyFlightManager already does server-side, mirrored here
        // for the local player specifically - Player#updateSwimming (vanilla) unconditionally
        // forces isSwimming() back to false whenever abilities.flying is true, and it runs BOTH
        // server-side (which our own server-side override already out-races, tick for tick) AND
        // client-side, as part of the local player's own predicted simulation - a real death,
        // confirmed by this being reported as "still just looks like creative flying": the
        // server's own copy was already correct the whole time, but the flying player's own
        // camera renders their *local* prediction of themselves, which vanilla's own client-side
        // tick was resetting right back to false every single frame, completely untouched by
        // anything server-side. Client tick's own Phase.END runs after that same local
        // updateSwimming() call, so this wins the same way the server-side override does.
        boolean shouldSwim = !player.onGround() && !player.isInWater();
        if (player.isSwimming() != shouldSwim)
        {
            player.setSwimming(shouldSwim);
        }

        double forwardInput = (mc.options.keyUp.isDown() ? 1.0 : 0.0) - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
        double strafeInput = (mc.options.keyRight.isDown() ? 1.0 : 0.0) - (mc.options.keyLeft.isDown() ? 1.0 : 0.0);

        Vec3 target;
        if (forwardInput == 0.0 && strafeInput == 0.0)
        {
            target = Vec3.ZERO;
        }
        else
        {
            Vec3 forward = player.getLookAngle();
            Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
            if (right.lengthSqr() < 1.0E-4)
            {
                right = Vec3.ZERO;
            }
            else
            {
                right = right.normalize();
            }
            Vec3 direction = forward.scale(forwardInput).add(right.scale(strafeInput));
            target = direction.lengthSqr() > 1.0E-6 ? direction.normalize().scale(FLIGHT_SPEED) : Vec3.ZERO;
        }

        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.add(target.subtract(current).scale(SMOOTHING)));
        player.resetFallDistance();
    }
}
