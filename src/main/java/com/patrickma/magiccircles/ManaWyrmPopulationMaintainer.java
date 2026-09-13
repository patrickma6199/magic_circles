package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.ManaWyrmEntity;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModFluids;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Keeps Mana Wyrms in the Wellspring.
 *
 * <p>They were only ever placed once, when the ocean was carved, and nothing spawns them
 * naturally - so once the original population was gone it was gone for good. {@code
 * ManaWyrmEntity#removeWhenFarAway} now stops them draining away in the first place, but a world
 * that already lost them would otherwise stay empty forever, which is what this is for. Same shape
 * as {@code PixiePopulationMaintainer}: check occasionally, top up gently, never all at once.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ManaWyrmPopulationMaintainer
{
    private static final int CHECK_INTERVAL_TICKS = 20 * 15;
    private static final int TARGET_POPULATION = 110;
    private static final int MAX_SPAWNS_PER_CHECK = 12;
    private static final int PLACEMENT_ATTEMPTS = 60;
    private static final int SEARCH_RADIUS = 220;

    private static final Random RANDOM = new Random();
    private static int ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

    private ManaWyrmPopulationMaintainer()
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
        if (fairyRealm == null)
        {
            return;
        }

        AABB ocean = oceanSearchArea(fairyRealm);
        int current = fairyRealm.getEntitiesOfClass(ManaWyrmEntity.class, ocean).size();
        int deficit = Math.min(MAX_SPAWNS_PER_CHECK, TARGET_POPULATION - current);
        for (int i = 0; i < deficit; i++)
        {
            spawnOneWyrm(fairyRealm);
        }
    }

    private static AABB oceanSearchArea(ServerLevel level)
    {
        return new AABB(
                WorldTree.CENTER_X - SEARCH_RADIUS, level.getMinBuildHeight(), WorldTree.CENTER_Z - SEARCH_RADIUS,
                WorldTree.CENTER_X + SEARCH_RADIUS, WorldTree.groundY(), WorldTree.CENTER_Z + SEARCH_RADIUS);
    }

    /** Finds any Wellspring Water with room to swim and puts one wyrm in it. */
    private static void spawnOneWyrm(ServerLevel level)
    {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++)
        {
            int x = WorldTree.CENTER_X + RANDOM.nextInt(2 * SEARCH_RADIUS + 1) - SEARCH_RADIUS;
            int z = WorldTree.CENTER_Z + RANDOM.nextInt(2 * SEARCH_RADIUS + 1) - SEARCH_RADIUS;
            int y = level.getMinBuildHeight() + 1
                    + RANDOM.nextInt(Math.max(1, WorldTree.groundY() - level.getMinBuildHeight() - 1));
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos) || !isWellspring(level, pos) || !isWellspring(level, pos.above()))
            {
                continue;
            }
            ManaWyrmEntity wyrm = new ManaWyrmEntity(ModEntities.MANA_WYRM.get(), level);
            wyrm.moveTo(x + 0.5, y + 0.5, z + 0.5, RANDOM.nextFloat() * 360.0f, 0.0f);
            wyrm.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
            level.addFreshEntity(wyrm);
            return;
        }
    }

    private static boolean isWellspring(ServerLevel level, BlockPos pos)
    {
        var fluid = level.getBlockState(pos).getFluidState().getType();
        return fluid == ModFluids.WELLSPRING_WATER.get() || fluid == ModFluids.WELLSPRING_WATER_FLOWING.get();
    }
}
