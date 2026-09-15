package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.WaterLikeFluidSounds;
import com.patrickma.magiccircles.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.common.Mod;

/**
 * The rest of "the same sound effects as normal water" that splash/swim
 * ({@code block/WaterLikeFluidSounds}) didn't cover: the enter/exit whoosh, the continuous
 * underwater hum, and the occasional random bubble/gurgle "addition" sounds vanilla plays while
 * your eyes are submerged - for every water-like fluid this mod has (see
 * {@link WaterLikeFluidSounds#WATER_LIKE_FLUID_TYPES}, shared with the splash/swim sounds so
 * there's exactly one list of "these fluids act like water" to keep in sync, not two). All of it
 * lives in {@code LocalPlayer} (`updateIsUnderwater`, `UnderwaterAmbientSoundHandler`,
 * `UnderwaterAmbientSoundInstances`, confirmed by decompiling them) and is gated by
 * {@code LocalPlayer#isUnderWater()} - which, like the splash/swim sounds, is hardcoded to
 * vanilla water specifically and never true for a custom fluid no matter how water-like it is.
 * Rather than patch vanilla's classes (their relevant constructors are
 * {@code protected}/package-private, so subclassing isn't even an option), this reproduces the
 * same three pieces from scratch, gated by {@code Entity#isEyeInFluidType} instead - the generic,
 * per-{@code FluidType} check that already correctly drives breathing/drowning for these fluids.
 *
 * <p>Originally this only ever checked one hardcoded fluid ({@code ModFluidTypes#PORTAL_WATER})
 * and was named accordingly ({@code PortalAmbientSounds}) - renamed and generalized once
 * {@code ModFluidTypes#WELLSPRING_WATER} needed the exact same treatment.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterLikeAmbientSounds
{
    // Matches UnderwaterAmbientSoundHandler's own three rarity tiers exactly.
    private static final float ADDITION_CHANCE = 0.01F;
    private static final float ADDITION_RARE_CHANCE = 0.001F;
    private static final float ADDITION_ULTRA_RARE_CHANCE = 1.0E-4F;

    // Null when not submerged in any of these fluids - otherwise exactly which one, since a
    // player can only ever have their eyes in a single fluid at once. Reference equality is
    // enough to detect "still the same fluid" vs. "left/switched" - every FluidType singleton
    // here comes from a RegistryObject#get(), which always returns the same instance.
    private static FluidType currentFluid;

    private WaterLikeAmbientSounds()
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
        if (player == null || mc.level == null)
        {
            currentFluid = null;
            return;
        }

        // Each water has its own world under the surface - see ModSounds.FluidVoice.
        FluidType found = findEyeFluid(player);
        ModSounds.FluidVoice voice = found != null ? ModSounds.voiceOf(found) : null;
        if (found != null && found != currentFluid && voice != null)
        {
            mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    voice.submerge().get(), SoundSource.AMBIENT, 0.8F, 1.0F, false);
            mc.getSoundManager().play(new LoopSound(player, found, voice.loop().get()));
        }
        else if (found == null && currentFluid != null)
        {
            ModSounds.FluidVoice leftVoice = ModSounds.voiceOf(currentFluid);
            if (leftVoice != null)
            {
                mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                        leftVoice.surface().get(), SoundSource.AMBIENT, 0.8F, 1.0F, false);
            }
        }
        currentFluid = found;

        if (found == null || voice == null)
        {
            return;
        }
        // Something drifting past, now and then - rarer, and quieter, the rarer the roll.
        float roll = player.level().random.nextFloat();
        float volume;
        if (roll < ADDITION_ULTRA_RARE_CHANCE)
        {
            volume = 1.0F;
        }
        else if (roll < ADDITION_RARE_CHANCE)
        {
            volume = 0.7F;
        }
        else if (roll < ADDITION_CHANCE)
        {
            volume = 0.4F;
        }
        else
        {
            return;
        }
        mc.getSoundManager().play(new SubSound(player, found, voice.addition().get(), volume));
    }

    /** The first of {@link WaterLikeFluidSounds#WATER_LIKE_FLUID_TYPES} the player's eyes are currently in, or {@code null}. */
    private static FluidType findEyeFluid(LocalPlayer player)
    {
        for (net.minecraftforge.registries.RegistryObject<FluidType> registryObject : WaterLikeFluidSounds.WATER_LIKE_FLUID_TYPES)
        {
            FluidType fluidType = registryObject.get();
            if (player.isEyeInFluidType(fluidType))
            {
                return fluidType;
            }
        }
        return null;
    }

    /** Mirrors vanilla's own (inaccessible) {@code UnderwaterAmbientSoundInstance}: loops forever, fading in/out as the player crosses in and out of the fluid, and stops itself once fully faded rather than needing to be told to. */
    private static final class LoopSound extends AbstractTickableSoundInstance
    {
        private static final int FADE_DURATION = 40;
        private final LocalPlayer player;
        private final FluidType fluidType;
        private int fade;

        LoopSound(LocalPlayer player, FluidType fluidType, SoundEvent loop)
        {
            super(loop, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.player = player;
            this.fluidType = fluidType;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.relative = true;
        }

        @Override
        public void tick()
        {
            if (player.isRemoved() || fade < 0)
            {
                stop();
                return;
            }
            if (player.isEyeInFluidType(fluidType))
            {
                fade++;
            }
            else
            {
                fade -= 2;
            }
            fade = Math.min(fade, FADE_DURATION);
            volume = Math.max(0.0F, Math.min((float) fade / FADE_DURATION, 1.0F));
        }
    }

    /** Mirrors vanilla's own (inaccessible) {@code SubSound}: a single one-shot addition, cut short if the player surfaces before it would finish on its own. */
    private static final class SubSound extends AbstractTickableSoundInstance
    {
        private final LocalPlayer player;
        private final FluidType fluidType;

        SubSound(LocalPlayer player, FluidType fluidType, SoundEvent sound, float volume)
        {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.player = player;
            this.fluidType = fluidType;
            this.looping = false;
            this.delay = 0;
            this.volume = volume;
            this.relative = true;
        }

        @Override
        public void tick()
        {
            if (player.isRemoved() || !player.isEyeInFluidType(fluidType))
            {
                stop();
            }
        }
    }
}
