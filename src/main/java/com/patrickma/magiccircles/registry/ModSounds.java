package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Every sound the mod adds. The files themselves are synthesised by {@code tools/gen_sounds.py};
 * {@code assets/magiccircles/sounds.json} maps each event here to its variants and its subtitle.
 */
public class ModSounds
{
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MagicCircles.MOD_ID);

    /** A fairy babbling to itself, half singing, now and then giggling - its ambient sound. */
    public static final RegistryObject<SoundEvent> FAIRY_CHATTER = register("fairy.chatter");
    /** The same voice, asking - when someone opens a conversation with it ({@code FairyTrades#openDialogue}). */
    public static final RegistryObject<SoundEvent> FAIRY_GREET = register("fairy.greet");
    public static final RegistryObject<SoundEvent> FAIRY_HURT = register("fairy.hurt");
    public static final RegistryObject<SoundEvent> FAIRY_DEATH = register("fairy.death");
    /** Beating wings, looped for as long as a fairy is in the air - see {@code client/FairyFlutterSound}. */
    public static final RegistryObject<SoundEvent> FAIRY_FLUTTER = register("fairy.flutter");
    /** A spell leaving a fairy's heartstone. */
    public static final RegistryObject<SoundEvent> FAIRY_CAST = register("fairy.cast");
    /** A trade struck. */
    public static final RegistryObject<SoundEvent> FAIRY_TRADE = register("fairy.trade");
    public static final RegistryObject<SoundEvent> FAIRY_WARD_RAISE = register("fairy.ward_raise");
    public static final RegistryObject<SoundEvent> FAIRY_WARD_BREAK = register("fairy.ward_break");
    /** Ten wisps loosed at once - see {@code entity/WispMissileEntity}. */
    public static final RegistryObject<SoundEvent> FAIRY_VOLLEY = register("fairy.volley");

    /** A pixie's twitter - little glass whistles, quicker and higher than any fairy's voice. */
    public static final RegistryObject<SoundEvent> PIXIE_CHIRP = register("pixie.chirp");
    public static final RegistryObject<SoundEvent> PIXIE_HURT = register("pixie.hurt");
    public static final RegistryObject<SoundEvent> PIXIE_DEATH = register("pixie.death");

    /** The Ferryman's presence: a quiet, low rumble. Never from a haunting - see {@code entity/FerrymanEntity#getAmbientSound}. */
    public static final RegistryObject<SoundEvent> FERRYMAN_RUMBLE = register("ferryman.rumble");

    /**
     * Everything one of the mod's waters says (see {@code block/WaterLikeFluidSounds} for the body in
     * it and {@code client/WaterLikeAmbientSounds} for the head under it): a splash going in, a
     * sound coming out, swim strokes, the head going under and coming back up, a loop for as long
     * as it stays under, and rare additions on top of the loop.
     */
    public record FluidVoice(RegistryObject<SoundEvent> enter, RegistryObject<SoundEvent> exit,
                             RegistryObject<SoundEvent> swim, RegistryObject<SoundEvent> submerge,
                             RegistryObject<SoundEvent> surface, RegistryObject<SoundEvent> loop,
                             RegistryObject<SoundEvent> addition)
    {
        private static FluidVoice of(String fluid)
        {
            return new FluidVoice(register("fluid." + fluid + ".enter"), register("fluid." + fluid + ".exit"),
                    register("fluid." + fluid + ".swim"), register("fluid." + fluid + ".submerge"),
                    register("fluid." + fluid + ".surface"), register("fluid." + fluid + ".loop"),
                    register("fluid." + fluid + ".addition"));
        }
    }

    /** The Wellspring's: water with glass chimes in it, and a warm chord breathing underneath. */
    public static final FluidVoice WELLSPRING_WATER = FluidVoice.of("wellspring");
    /** The portal fluid's: darker, swirling, with a deep throb and a vortex droning under the surface. */
    public static final FluidVoice PORTAL_WATER = FluidVoice.of("portal");

    /** Which voice a water-like fluid speaks with - null for a fluid that isn't one of the mod's. */
    @Nullable
    public static FluidVoice voiceOf(FluidType fluidType)
    {
        if (fluidType == ModFluidTypes.WELLSPRING_WATER.get())
        {
            return WELLSPRING_WATER;
        }
        if (fluidType == ModFluidTypes.PORTAL_WATER.get())
        {
            return PORTAL_WATER;
        }
        return null;
    }

    private static RegistryObject<SoundEvent> register(String name)
    {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MagicCircles.MOD_ID, name)));
    }
}
