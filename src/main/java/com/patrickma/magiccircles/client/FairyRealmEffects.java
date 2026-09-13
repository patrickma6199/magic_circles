package com.patrickma.magiccircles.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The Fairy Realm's sky - registered under {@code magiccircles:fairy_realm} (see
 * {@link ClientSetup#registerDimensionEffects}), matching the {@code "effects"} value in
 * {@code data/magiccircles/dimension_type/fairy_realm.json}, which is what actually tells
 * vanilla to look this instance up instead of falling back to the plain overworld sky.
 *
 * <p>Deliberately built entirely out of vanilla's own {@link DimensionSpecialEffects.SkyType#NORMAL}
 * rendering (dome + sun/moon/stars) rather than a fully custom sky mesh -
 * {@code DimensionSpecialEffects}/{@code IForgeDimensionSpecialEffects} does expose a hook to
 * replace sky rendering outright ({@code renderSky}, returning {@code true} to suppress
 * vanilla's), but that means hand-writing a new sky dome mesh and vertex buffers with no way to
 * see the result short of an actual client, which is a lot of blind risk. Reusing vanilla's own
 * (thoroughly proven) dome renderer and only supplying a different base color gets a distinct
 * sky with none of that risk - {@code data/magiccircles/worldgen/biome/fairy_realm.json}'s
 * {@code effects.sky_color} is a muted, dusky tone (not the saturated blue an earlier version
 * used) specifically so it reads as a neutral backdrop rather than competing with the actual
 * color story, which now lives entirely in {@link FairyRealmSkyStreaks}' six drifting streaks.
 *
 * <p>An earlier version also overrode {@link #getSunriseColor} to <em>always</em> return a warm
 * gold (since this dimension's {@code fixed_time} locks the sun at noon, vanilla's own version -
 * which only returns non-null near actual sunrise/sunset - would otherwise never fire at all),
 * turning the normal "sunset glow" band into a permanent golden ring under the sky dome. That's
 * gone now: forcing one glow band on permanently was reported as reading like the sky was frozen
 * at a single moment rather than a living one, and {@link FairyRealmSkyStreaks} replaces it with
 * something that actually moves, in six colors instead of one.
 *
 * <p>Kept as a real {@code SkyType.NORMAL} sky (an earlier pass briefly swapped this to {@code
 * SkyType.NONE} to suppress the moon specifically, but that reads as a flat, empty void rather
 * than an actual sky - "the look through the skybox," not through turning the sky off) - the
 * dusk look (a permanently low sun, sunset-glow lighting) instead comes entirely from {@code
 * fixed_time: 13000} (just past sunset, before full night) in the dimension type plus a moderate
 * {@code ambient_light} there. The cloud height is {@link Float#NaN} the same way the Nether has
 * none - {@code LevelRenderer} skips cloud rendering outright whenever the dimension's cloud
 * height isn't a real number.
 */
public class FairyRealmEffects extends DimensionSpecialEffects
{
    public FairyRealmEffects()
    {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float daylight)
    {
        return fogColor.multiply(daylight * 0.94F + 0.06F, daylight * 0.94F + 0.06F, daylight * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int z)
    {
        return false;
    }
}
