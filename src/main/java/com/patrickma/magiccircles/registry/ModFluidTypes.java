package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/**
 * The {@link FluidType} behind the portal spell's water (see {@link ModFluids}) - a real,
 * distinct fluid rather than reusing vanilla water, specifically so it can have its own swirly
 * texture. This doesn't cost it any of water's actual *behavior*: {@code canSwim}/{@code canDrown}
 * (both true, matching the defaults) are read generically by Forge's own breathing code
 * ({@code ForgeHooks#onLivingBreathe}, confirmed by decompiling it) via
 * {@code Entity#getEyeInFluidType()}/{@code #canDrownInFluidType} - nothing here is hardcoded to
 * vanilla water specifically the way the old, pre-Forge vanilla water-breathing check was (that
 * whole vanilla code path is dead under Forge - see the `if (false)` guarding it in
 * {@code LivingEntity#baseTick}). Being a real fluid type is what gives this working swim
 * physics and the vanilla air-bubble HUD for free, which the block-only version this replaced
 * couldn't provide on its own.
 */
public class ModFluidTypes
{
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MagicCircles.MOD_ID);

    private static final ResourceLocation TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "block/fairy_portal_water");
    // Rendered full-screen over the camera while the player's eyes are inside this fluid - a
    // lighter-blue counterpart to vanilla's own textures/misc/underwater.png (see
    // tools/gen_portal_underwater_overlay.py for how it was generated).
    private static final ResourceLocation UNDERWATER_OVERLAY = new ResourceLocation(MagicCircles.MOD_ID, "textures/misc/portal_underwater.png");

    public static final RegistryObject<FluidType> PORTAL_WATER = FLUID_TYPES.register("portal_water",
            () -> new FluidType(FluidType.Properties.create()
                    .canSwim(true)
                    .canDrown(true)
                    .canPushEntity(true)
                    .density(1000)
                    .viscosity(1000)
                    .lightLevel(6))
            {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer)
                {
                    consumer.accept(new IClientFluidTypeExtensions()
                    {
                        @Override
                        public ResourceLocation getStillTexture()
                        {
                            return TEXTURE;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture()
                        {
                            return TEXTURE;
                        }

                        @Override
                        public ResourceLocation getRenderOverlayTexture(net.minecraft.client.Minecraft mc)
                        {
                            return UNDERWATER_OVERLAY;
                        }

                        // Overridden directly rather than relying on the default renderOverlay
                        // (which calls this via ScreenEffectRenderer#renderFluid) - that vanilla
                        // method hardcodes its own alpha multiplier to 0.1F regardless of this
                        // texture's own alpha, which is why three earlier attempts at a more
                        // visible tint all read as negligible - see
                        // client/ModFluidOverlayRenderer's own doc comment for the full story.
                        // This bypasses that cap entirely, so the texture's own alpha (see
                        // tools/gen_portal_underwater_overlay.py) is finally the only multiplier.
                        @Override
                        public void renderOverlay(net.minecraft.client.Minecraft mc, com.mojang.blaze3d.vertex.PoseStack poseStack)
                        {
                            com.patrickma.magiccircles.client.ModFluidOverlayRenderer.renderFluidOverlay(mc, poseStack, UNDERWATER_OVERLAY, 1.0F);
                        }
                    });
                }
            });

    private static final ResourceLocation WELLSPRING_TEXTURE = new ResourceLocation(MagicCircles.MOD_ID, "block/wellspring_water");
    // The Wellspring's own underwater tint, deliberately a *different* texture from
    // UNDERWATER_OVERLAY - the portal fluid's own filter is explicitly left untouched, while this
    // one cycles through a full rainbow band-per-column (see
    // tools/gen_wellspring_underwater_overlay.py) to read as "this water holds mana."
    private static final ResourceLocation WELLSPRING_UNDERWATER_OVERLAY = new ResourceLocation(MagicCircles.MOD_ID, "textures/misc/wellspring_underwater.png");

    /**
     * The Wellspring's own water - see {@code worldgen/WorldTree.java}'s well. Same
     * water-like physics as {@link #PORTAL_WATER} (swim/drown read generically, not hardcoded to
     * vanilla water), but its own rainbow-like underwater screen tint (see
     * {@link #WELLSPRING_UNDERWATER_OVERLAY}) rather than sharing {@link #PORTAL_WATER}'s pale
     * blue one - the portal fluid's own filter stays exactly as it was.
     * The still/flowing texture is the other interesting part, cycling through every rune color
     * plus white (see {@code tools/gen_wellspring_water_texture.py}) to read as "this water holds
     * mana." Splash/swim/ambient sounds are handled generically too - see
     * {@code client/WaterLikeFluidSounds}/{@code client/WaterLikeAmbientSounds}, which both check
     * against every water-like {@link FluidType} this mod has rather than just one.
     */
    public static final RegistryObject<FluidType> WELLSPRING_WATER = FLUID_TYPES.register("wellspring_water",
            () -> new FluidType(FluidType.Properties.create()
                    .canSwim(true)
                    .canDrown(true)
                    .canPushEntity(true)
                    .density(1000)
                    .viscosity(1000)
                    .lightLevel(10))
            {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer)
                {
                    consumer.accept(new IClientFluidTypeExtensions()
                    {
                        @Override
                        public ResourceLocation getStillTexture()
                        {
                            return WELLSPRING_TEXTURE;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture()
                        {
                            return WELLSPRING_TEXTURE;
                        }

                        @Override
                        public ResourceLocation getRenderOverlayTexture(net.minecraft.client.Minecraft mc)
                        {
                            return WELLSPRING_UNDERWATER_OVERLAY;
                        }

                        @Override
                        public void renderOverlay(net.minecraft.client.Minecraft mc, com.mojang.blaze3d.vertex.PoseStack poseStack)
                        {
                            // "see perfectly submerged in it" - the Blessed look right through the
                            // Wellspring, so the murk over the camera simply isn't drawn for them.
                            if (com.patrickma.magiccircles.client.WellspringVision.seesClearly(mc))
                            {
                                return;
                            }
                            com.patrickma.magiccircles.client.ModFluidOverlayRenderer.renderFluidOverlay(mc, poseStack, WELLSPRING_UNDERWATER_OVERLAY, 1.0F);
                        }

                        @Override
                        public void modifyFogRender(net.minecraft.client.Camera camera,
                                                    net.minecraft.client.renderer.FogRenderer.FogMode mode,
                                                    float renderDistance, float partialTick,
                                                    float nearDistance, float farDistance,
                                                    com.mojang.blaze3d.shaders.FogShape shape)
                        {
                            com.patrickma.magiccircles.client.WellspringVision.clearFog(renderDistance);
                        }
                    });
                }
            });

    private ModFluidTypes()
    {
    }
}
