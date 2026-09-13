package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.Minecraft;

/**
 * Clear sight underwater for anyone Blessed by the Wellspring - "you can breath wellspring water
 * and see perfectly submerged in it." The breathing half lives in {@code WellspringBlessing}; this
 * is only what the Blessed player's own camera does.
 *
 * <p>Two separate things make submerged water murky, so both have to go: the fluid overlay drawn
 * flat across the screen (suppressed in {@code ModFluidTypes}, which asks {@link #seesClearly}),
 * and the fog the water itself renders with, pushed out past the view distance by {@link
 * #clearFog}. Suppressing only the overlay would still leave the world fading out a few blocks
 * away.
 */
public final class WellspringVision
{
    private WellspringVision()
    {
    }

    /** True when the player whose eyes we are rendering through is Blessed. */
    public static boolean seesClearly(Minecraft mc)
    {
        return mc.player != null && mc.player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get());
    }

    /**
     * Called while the Wellspring's own fog is being set up. Does nothing at all unless the viewer
     * is Blessed, so unblessed players still get the ordinary murk.
     */
    public static void clearFog(float renderDistance)
    {
        if (!seesClearly(Minecraft.getInstance()))
        {
            return;
        }
        RenderSystem.setShaderFogStart(renderDistance * 0.75f);
        RenderSystem.setShaderFogEnd(renderDistance);
    }
}
