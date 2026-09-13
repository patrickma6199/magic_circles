package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Splash and swim sounds for every water-like fluid this mod has ({@link ModFluidTypes#PORTAL_WATER},
 * {@link ModFluidTypes#WELLSPRING_WATER}) - the one part of "acts like water" that isn't free for
 * a custom {@code FluidType} (see either fluid block's own doc comment for the full story: both
 * {@code Entity#isInWater}/{@code #updateFluidHeightAndDoFluidPushing} are hardcoded to Forge's
 * own vanilla water singleton specifically, bypassing the generic {@code FluidType} system this
 * mod otherwise relies on for everything else). Originally this lived directly on {@code
 * FairyPortalWaterBlock} itself, hardcoded to just {@link ModFluidTypes#PORTAL_WATER} - pulled
 * out into its own class, checking every entry in {@link #WATER_LIKE_FLUID_TYPES}, once
 * {@link com.patrickma.magiccircles.registry.ModFluidTypes#WELLSPRING_WATER} needed the exact
 * same treatment rather than duplicating this whole class a second time.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterLikeFluidSounds
{
    /**
     * Every fluid this reproduces splash/swim sounds for - add a new entry here, nothing else,
     * to cover another one. {@code RegistryObject}s themselves, not their resolved {@code
     * FluidType}s - calling {@code .get()} this early (a static field initializer runs the
     * moment this class is first loaded, which happens well before the registry event that
     * actually populates these) throws {@code NullPointerException: Registry Object not present}
     * - confirmed the hard way by a failed boot, not assumed. {@code RegistryObject} itself is
     * safe to hold onto immediately; only resolving it via {@code .get()} has to wait, which is
     * why every use below calls {@code .get()} at the point of use instead of once up front.
     */
    public static final List<RegistryObject<FluidType>> WATER_LIKE_FLUID_TYPES =
            List.of(ModFluidTypes.PORTAL_WATER, ModFluidTypes.WELLSPRING_WATER);

    private static final long SWIM_SOUND_INTERVAL_TICKS = 5;
    private static final double SWIM_SOUND_MIN_SPEED = 0.02;

    // Keyed by entity rather than by position: what matters for "just entered" is a gap since
    // this *entity* was last seen touching any of these fluids, not which block cell it happened
    // to touch.  Weak so a despawned/unloaded entity's entry doesn't linger forever.
    private static final Map<LivingEntity, Long> LAST_TOUCHED_TICK = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LAST_SWIM_SOUND_TICK = new WeakHashMap<>();

    private WaterLikeFluidSounds()
    {
    }

    private static boolean isInAnyWaterLikeFluid(LivingEntity entity)
    {
        for (RegistryObject<FluidType> fluidType : WATER_LIKE_FLUID_TYPES)
        {
            if (entity.isInFluidType(fluidType.get()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Fires for every living entity, every tick, on both logical sides - filtered down here to
     * "server-side and actually touching one of these fluids right now" via
     * {@code Entity#isInFluidType}, the same generic check {@code ForgeHooks#onLivingBreathe}
     * uses for drowning, so detection here can't drift out of sync with what breathing already
     * (correctly) considers "in the water."
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isInAnyWaterLikeFluid(entity))
        {
            return;
        }

        Level level = entity.level();
        long now = level.getGameTime();
        long lastTouched = LAST_TOUCHED_TICK.getOrDefault(entity, Long.MIN_VALUE);
        // A gap of more than 1 tick since this entity was last seen touching one of these fluids
        // is what actually means "just entered" - not "still touching from a moment ago."
        boolean justEntered = now - lastTouched > 1;
        LAST_TOUCHED_TICK.put(entity, now);

        if (justEntered)
        {
            playSplash(level, entity);
        }
        else
        {
            maybePlaySwim(level, entity, now);
        }
    }

    private static void playSplash(Level level, LivingEntity entity)
    {
        // Same speed-based pick as vanilla's own Entity#doWaterSplashEffect: a gentle entry gets
        // the normal splash, a fast one (diving, falling in from height, ...) gets the louder
        // high-speed variant - though only Player actually distinguishes the two in vanilla
        // (Entity#getSwimHighSpeedSplashSound defaults to the same GENERIC_SPLASH as the normal
        // one for every other entity - there's no generic "high speed" splash sound at all).
        Vec3 motion = entity.getDeltaMovement();
        float speedFactor = Math.min(1.0F, (float) Math.sqrt(motion.x * motion.x * 0.2 + motion.y * motion.y + motion.z * motion.z * 0.2) * 0.9F);
        boolean highSpeed = speedFactor >= 0.25F;
        SoundEvent splashSound = entity instanceof Player
                ? (highSpeed ? SoundEvents.PLAYER_SPLASH_HIGH_SPEED : SoundEvents.PLAYER_SPLASH)
                : SoundEvents.GENERIC_SPLASH;
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                splashSound, entity.getSoundSource(), Math.max(0.35F, speedFactor),
                1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);

        double width = entity.getBbWidth();
        double floorY = Math.floor(entity.getY()) + 1.0;
        for (int i = 0; i < 1 + width * 20.0; i++)
        {
            double dx = (level.random.nextDouble() * 2.0 - 1.0) * width;
            double dz = (level.random.nextDouble() * 2.0 - 1.0) * width;
            level.addParticle(ParticleTypes.BUBBLE, entity.getX() + dx, floorY, entity.getZ() + dz, 0.0, -level.random.nextDouble() * 0.2, 0.0);
            level.addParticle(ParticleTypes.SPLASH, entity.getX() + dx, floorY, entity.getZ() + dz, 0.0, 0.0, 0.0);
        }
    }

    private static void maybePlaySwim(Level level, LivingEntity entity, long now)
    {
        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.horizontalDistance();
        if (speed < SWIM_SOUND_MIN_SPEED)
        {
            return;
        }
        long lastSwimSound = LAST_SWIM_SOUND_TICK.getOrDefault(entity, Long.MIN_VALUE);
        if (now - lastSwimSound < SWIM_SOUND_INTERVAL_TICKS)
        {
            return;
        }
        LAST_SWIM_SOUND_TICK.put(entity, now);
        float volume = Math.min(1.0F, (float) speed * 3.0F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_SWIM, entity.getSoundSource(), volume,
                1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
    }
}
