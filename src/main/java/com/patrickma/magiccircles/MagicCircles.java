package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModCreativeTabs;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import com.patrickma.magiccircles.registry.ModFluids;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.registry.ModItems;
import com.patrickma.magiccircles.registry.ModPlacementModifiers;
import com.patrickma.magiccircles.registry.ModRecipeSerializers;
import com.patrickma.magiccircles.registry.ModSounds;
import com.patrickma.magiccircles.registry.ModTreeDecorators;
import com.patrickma.magiccircles.registry.ModWorldgen;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Mod entry point. Forge instantiates this class once and hands it the mod event bus,
 * which is where every {@code DeferredRegister} below needs to attach itself.
 */
@Mod(MagicCircles.MOD_ID)
public class MagicCircles
{
    public static final String MOD_ID = "magiccircles";

    public MagicCircles(FMLJavaModLoadingContext context)
    {
        var modEventBus = context.getModEventBus();

        ModEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModFluidTypes.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModWorldgen.CHUNK_GENERATORS.register(modEventBus);
        ModTreeDecorators.TREE_DECORATOR_TYPES.register(modEventBus);
        ModPlacementModifiers.PLACEMENT_MODIFIER_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModNetworking.register();

        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::commonSetup);
    }

    /** {@code Mob}s need an attribute map registered before they can ever actually spawn - {@link com.patrickma.magiccircles.entity.PixieEntity}/{@link com.patrickma.magiccircles.entity.ManaWyrmEntity} both reuse their vanilla base class's own set unchanged. */
    private void registerAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.PIXIE.get(), com.patrickma.magiccircles.entity.PixieEntity.createAttributes().build());
        event.put(ModEntities.FAIRY.get(), com.patrickma.magiccircles.entity.FairyEntity.createAttributes().build());
        event.put(ModEntities.FAIRY_QUEEN.get(), com.patrickma.magiccircles.entity.FairyQueenEntity.createAttributes().build());
        event.put(ModEntities.MANA_WYRM.get(), com.patrickma.magiccircles.entity.ManaWyrmEntity.createAttributes().build());
        event.put(ModEntities.DREAM_ELK.get(), com.patrickma.magiccircles.entity.DreamElkEntity.createAttributes().build());
        event.put(ModEntities.FERRYMAN.get(), com.patrickma.magiccircles.entity.FerrymanEntity.createAttributes().build());
        event.put(ModEntities.GHOST_PHANTOM.get(), com.patrickma.magiccircles.entity.GhostPhantomEntity.createAttributes().build());
        event.put(ModEntities.PLAYER_CORPSE.get(), com.patrickma.magiccircles.entity.PlayerCorpseEntity.createAttributes().build());
        event.put(ModEntities.CREATURE_CORPSE.get(), com.patrickma.magiccircles.entity.CreatureCorpseEntity.createAttributes().build());
    }

    /**
     * Without an explicit {@link SpawnPlacements#register} call, {@code NaturalSpawner} silently
     * never attempts to spawn an entity type at all - vanilla registers this per-{@code
     * EntityType} *instance*, not by Java class, so {@code PixieEntity} extending {@code Allay}
     * never inherited Allay's own (nonexistent, since Allays don't naturally spawn either) rule.
     * This is the actual, well-known reason a brand new mob with a biome spawner entry never
     * spawns: the entry alone isn't enough. {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} +
     * {@code Mob::checkMobSpawnRules} is the same standard rule most flying/ground creatures use.
     */
    private void commonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> SpawnPlacements.register(ModEntities.PIXIE.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules));
        // IN_WATER, and only once the wyrms have scattered out of the Wellspring's sea - and then
        // only a few, in the open waters far from the Ancient Heartstone (see WyrmScattering).
        event.enqueueWork(() -> SpawnPlacements.register(ModEntities.MANA_WYRM.get(),
                SpawnPlacements.Type.IN_WATER, Heightmap.Types.OCEAN_FLOOR_WG, WyrmScattering::canSpawn));
        // Ordinary land spawning, for the surface population (see data/magiccircles/worldgen/biome/fairy_realm.json's
        // own "creature" entry) - the *guaranteed* population that can start submerged in the
        // Wellspring's own underground ocean is a direct world-init placement instead (see
        // WellspringOcean#spawnDreamElks): a heightmap-based natural-spawn predicate structurally
        // can never fire inside a closed underground cavity no heightmap column ever reaches, no
        // matter what the predicate itself checks for.
        event.enqueueWork(() -> SpawnPlacements.register(ModEntities.DREAM_ELK.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules));
    }
}
