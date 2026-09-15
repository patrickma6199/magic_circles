package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.PixieEntity;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Keeps the Fairy Realm topped up to a target Pixie population on an ongoing basis, rather than
 * relying on vanilla's own {@code NaturalSpawner}/biome spawn-list cycle - confirmed, by actually
 * playing the mod, to never produce a single pixie in practice even with a proper {@code
 * SpawnPlacements} registration and a generous biome spawner weight (see {@code
 * MagicCircles#commonSetup} and {@code data/magiccircles/worldgen/biome/fairy_realm.json}'s own
 * pixie entry - both are left in place as a secondary path, but this is what actually guarantees
 * pixies exist). Whatever specifically suppresses vanilla natural spawning here - a spawn-cap
 * already satisfied by mooshrooms, a heightmap/placement quirk specific to this custom {@code
 * ChunkGenerator}, or something else entirely - a periodic direct top-up sidesteps it completely
 * without needing to be diagnosed.
 *
 * <p>Checked every {@link #CHECK_INTERVAL_TICKS} (30 seconds), topping up one pixie at a time (via
 * {@code WorldTree#spawnOnePixie}, the same validated-open-air-pocket placement the world-init
 * population uses - see that method's own doc comment for why placement validation matters at
 * all) until {@link #TARGET_POPULATION} is reached again.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PixiePopulationMaintainer
{
    private static final int TARGET_POPULATION = 20;
    private static final int CHECK_INTERVAL_TICKS = 20 * 30;
    private static final int MAX_SPAWNS_PER_CHECK = 6;

    private static final Random RANDOM = new Random();
    private static int ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

    private PixiePopulationMaintainer()
    {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (--ticksUntilNextCheck > 0)
        {
            return;
        }
        ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }

        int currentPopulation = fairyRealm.getEntitiesOfClass(PixieEntity.class, WorldTree.populationCheckArea()).size();
        int deficit = Math.min(MAX_SPAWNS_PER_CHECK, TARGET_POPULATION - currentPopulation);
        for (int i = 0; i < deficit; i++)
        {
            WorldTree.spawnOnePixie(fairyRealm, RANDOM);
        }
    }
}
