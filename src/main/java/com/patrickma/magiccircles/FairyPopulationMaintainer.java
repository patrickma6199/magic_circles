package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.FairyEntity;
import com.patrickma.magiccircles.limbo.LimboRegistry;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps a small people living in the World Tree - fairies are few, not a swarm like the pixies (see
 * {@link PixiePopulationMaintainer}). New ones appear among the branches, never in the open, and only
 * while someone is actually in the Fairy Realm to meet them: with nobody there the tree's chunks
 * unload, the fairies already living there can't be counted, and topping the number up would only
 * crowd the tree with fairies nobody ever sees. Ghosts don't count - they are no longer living there.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyPopulationMaintainer
{
    private static final int TARGET_POPULATION = 20;
    /** Often, so a visitor finds the tree lived in within moments rather than minutes. */
    private static final int CHECK_INTERVAL_TICKS = 20 * 10;
    private static final int MAX_SPAWNS_PER_CHECK = 3;
    /** The first check comes almost as soon as anyone is there to see it. */
    private static final int FIRST_CHECK_TICKS = 20 * 3;
    private static final int PLACEMENT_ATTEMPTS = 30;

    private static int ticksUntilNextCheck = FIRST_CHECK_TICKS;

    private FairyPopulationMaintainer()
    {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || --ticksUntilNextCheck > 0)
        {
            return;
        }
        ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null || fairyRealm.players().isEmpty())
        {
            // Nobody there - check again soon after someone arrives, not a full interval later.
            ticksUntilNextCheck = FIRST_CHECK_TICKS;
            return;
        }

        // The queen is counted separately - she is FairyCourt's to keep.
        int living = fairyRealm.getEntitiesOfClass(FairyEntity.class, WorldTree.populationCheckArea(),
                fairy -> !fairy.isQueen() && !LimboRegistry.isCreatureGhost(fairy)).size();
        int deficit = Math.min(MAX_SPAWNS_PER_CHECK, TARGET_POPULATION - living);
        for (int i = 0; i < deficit; i++)
        {
            BlockPos spot = WorldTree.randomOpenAirInTree(fairyRealm, fairyRealm.random, PLACEMENT_ATTEMPTS);
            if (spot == null)
            {
                continue;
            }
            FairyEntity fairy = new FairyEntity(ModEntities.FAIRY.get(), fairyRealm);
            fairy.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, fairyRealm.random.nextFloat() * 360.0f, 0.0f);
            fairy.finalizeSpawn(fairyRealm, fairyRealm.getCurrentDifficultyAt(spot), MobSpawnType.COMMAND, null, null);
            fairyRealm.addFreshEntity(fairy);
        }
    }
}
