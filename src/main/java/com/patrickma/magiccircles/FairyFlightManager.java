package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fairy flight for anyone Blessed by the Wellspring: an elytra glide, flown on fairy terms.
 *
 * <p>The wings open when you double-tap jump, or on their own once you have fallen
 * five blocks (see {@code client/FairyFlightInput}); jumping again while gliding folds them. The
 * pose and animation are vanilla's own gliding ones - see {@code mixin/PlayerMixin} and {@code
 * mixin/LivingEntityMixin} for how an elytra-less player is allowed into that state at all.
 *
 * <p>The flight itself is {@link #steer}: you travel wherever you are looking, straight up as
 * readily and as fast as straight down, with no gravity pulling at you. Forward speeds up, back
 * brakes to a hover, and with neither held you cruise. No fall and no crash into a wall ever hurts
 * a Blessed player.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyFlightManager
{
    /** Blocks per tick with nothing held - a steady glide, never the slow drift of creative flight. */
    private static final double CRUISE_SPEED = 0.55;
    /** Blocks per tick holding forward - about the speed of a well-flown elytra. */
    private static final double BOOST_SPEED = 1.3;
    private static final double STRAFE_SPEED = 0.45;
    /** How quickly velocity swings round to where you are looking - lower feels heavier and more like gliding. */
    private static final double RESPONSIVENESS = 0.18;
    /** Braking is quicker than turning - a tap of back, and you have stopped. */
    private static final double BRAKE_RESPONSIVENESS = 0.35;
    private static final double STOPPED_SQR = 1.0E-4;

    /**
     * Client-side, the local player's own: whether they have braked to a standstill and are hanging
     * in the air on beating wings, as a fairy does. Holding back stops you and starts the hover;
     * it lasts - hands off the keys, looking wherever you like - until forward or a strafe sets
     * you moving again, or the wings fold.
     */
    private static boolean hovering;

    private FairyFlightManager()
    {
    }

    /** Whether this player may glide on fairy wings at all. The same answer on client and server. */
    public static boolean canGlide(Player player)
    {
        return player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get())
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger();
    }

    /** Whether a glide can start or keep going right now - airborne, dry, and not being lifted by something else. */
    public static boolean canKeepGliding(Player player)
    {
        return canGlide(player)
                && !player.onGround()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.LEVITATION);
    }

    /**
     * One tick of fairy flight, replacing vanilla's elytra physics. {@code input} is the movement
     * vector vanilla hands to {@code travel}: x is strafe (positive is left), z is forward.
     */
    public static void steer(Player player, Vec3 input)
    {
        if (input.z < 0.0)
        {
            hovering = true;
        }
        else if (input.z > 0.0 || input.x != 0.0)
        {
            hovering = false;
        }
        if (hovering)
        {
            Vec3 slowed = player.getDeltaMovement().scale(1.0 - BRAKE_RESPONSIVENESS);
            if (slowed.lengthSqr() < STOPPED_SQR)
            {
                slowed = Vec3.ZERO;
            }
            player.setDeltaMovement(slowed);
            player.move(MoverType.SELF, slowed);
            player.resetFallDistance();
            return;
        }

        Vec3 look = player.getLookAngle();

        double speed = input.z > 0.0 ? BOOST_SPEED : CRUISE_SPEED;
        Vec3 target = look.scale(speed);

        if (input.x != 0.0)
        {
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            if (right.lengthSqr() > 1.0E-4)
            {
                target = target.add(right.normalize().scale(-input.x * STRAFE_SPEED));
            }
        }

        Vec3 current = player.getDeltaMovement();
        Vec3 next = current.add(target.subtract(current).scale(RESPONSIVENESS));
        player.setDeltaMovement(next);
        player.move(MoverType.SELF, next);
        player.resetFallDistance();
    }

    public static boolean isHovering()
    {
        return hovering;
    }

    /** The wings have folded - the next flight starts on the move. */
    public static void endHover()
    {
        hovering = false;
    }

    /** Straight into a hover - the Shield ward lifting a Blessed caster (see {@code client/WardHoverClient}). */
    public static void beginHover()
    {
        hovering = true;
    }

    /** Folding the wings - from {@code network/ToggleFairyFlightPacket}. Always allowed; you simply drop. */
    public static void foldWings(ServerPlayer player)
    {
        if (player.isFallFlying())
        {
            player.stopFallFlying();
        }
    }

    /**
     * Fairy flight no longer uses creative-style flight at all, so any a survival player still
     * carries - saved mid-flight by the version of this mod that did - is taken away. The dead are
     * left alone: a rite caster behind the veil is given real flight on purpose.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide)
        {
            return;
        }
        Player player = event.player;
        if (player.isCreative() || player.isSpectator()
                || com.patrickma.magiccircles.limbo.LimboState.isInLimbo(player))
        {
            return;
        }
        if (player.getAbilities().mayfly || player.getAbilities().flying)
        {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    /** No fall ever hurts the Blessed, gliding or not - folding your wings to dive included. */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event)
    {
        if (event.getEntity() instanceof Player player && player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            event.setCanceled(true);
        }
    }

    /** Nor does gliding into a wall, which vanilla treats as its own separate kind of damage. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        if (event.getEntity() instanceof Player player
                && event.getSource().is(DamageTypes.FLY_INTO_WALL)
                && player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            event.setCanceled(true);
        }
    }
}
