package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModFluids;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration: render types, block entity renderers, and their model layers. */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup
{
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        // Runes have transparent texture pixels around the symbol; the default "solid" render
        // layer ignores alpha entirely (drawing it as black) - cutout is what makes it see-through,
        // the same layer vanilla uses for redstone dust.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.MAGIC_CIRCLE.get(), RenderType.cutout()));
        // Without this, LeavesBlock still renders through the default "solid" layer, which
        // ignores alpha entirely - the same reason MAGIC_CIRCLE needs an explicit layer above.
        // cutoutMipped (not plain cutout) is what vanilla's own leaves use - it's what actually
        // gives leaves their soft, slightly-translucent-at-a-distance look via mipmapping.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.LIVING_WOOD_LEAVES.get(), RenderType.cutoutMipped()));
        // Fluids register their render layer by Fluid, not by Block - a real fluid isn't drawn
        // through the normal block model system at all (RenderShape.INVISIBLE), so setting this
        // on the block itself (as for every other block above) would do nothing.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModFluids.PORTAL_WATER.get(), RenderType.translucent()));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModFluids.PORTAL_WATER_FLOWING.get(), RenderType.translucent()));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModFluids.WELLSPRING_WATER.get(), RenderType.translucent()));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModFluids.WELLSPRING_WATER_FLOWING.get(), RenderType.translucent()));
        // Cross-shaped kelp-style plants, same reasoning as MAGIC_CIRCLE above - cutout (not the
        // default solid layer) is what makes the transparent pixels around the strand see-through.
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLITTER_WEED.get(), RenderType.cutout()));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLITTER_WEED_PLANT.get(), RenderType.cutout()));
    }

    /** The Fairy Realm's custom blue-and-gold sky - see {@link FairyRealmEffects}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event)
    {
        event.register(new ResourceLocation(MagicCircles.MOD_ID, "fairy_realm"), new FairyRealmEffects());
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(HeartCoreModel.LAYER, HeartCoreModel::createBodyLayer);
        event.registerLayerDefinition(ShieldOrbModel.LAYER, ShieldOrbModel::createBodyLayer);
        event.registerLayerDefinition(DreamElkModel.LAYER, DreamElkModel::createBodyLayer);
        // (AncientHeartstoneWisps/AncientHeartstoneRenderer need no layer of their own registered
        // here - the renderer reuses HeartCoreModel.LAYER, already registered above.)
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.HEART_CORE.get(), HeartCoreBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BOOK_OF_THE_FAYE.get(), BookOfTheFayeBlockEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIELD_ORB.get(), ShieldOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.PIXIE.get(), PixieRenderer::new);
        event.registerEntityRenderer(ModEntities.MANA_WYRM.get(), ManaWyrmRenderer::new);
        event.registerEntityRenderer(ModEntities.ANCIENT_HEARTSTONE.get(), AncientHeartstoneRenderer::new);
        event.registerEntityRenderer(ModEntities.DREAM_ELK.get(), DreamElkRenderer::new);
        event.registerEntityRenderer(ModEntities.FERRYMAN.get(), FerrymanRenderer::new);
        event.registerEntityRenderer(ModEntities.PLAYER_CORPSE.get(), PlayerCorpseRenderer::new);
        // Plain vanilla Phantom look - only the AI/targeting differs (see entity/GhostPhantomEntity).
        event.registerEntityRenderer(ModEntities.GHOST_PHANTOM.get(), PhantomRenderer::new);
    }

    /** Adds {@link FairyWingsLayer} to both player skin variants - it renders purely off {@code ModEffects#BLESSED_BY_WELLSPRING}, not any item, so this is the only thing that ever draws Fairy Wings. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event)
    {
        for (String skin : event.getSkins())
        {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = event.getSkin(skin);
            if (renderer != null)
            {
                renderer.addLayer(new FairyWingsLayer<>(renderer, event.getEntityModels()));
            }
        }
    }
}
