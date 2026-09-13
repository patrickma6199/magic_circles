package com.patrickma.magiccircles;

import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stops lightning from reaching anyone standing under an active Shield/Tempest Ward - see {@link
 * HeartCoreBlockEntity#isPositionShielded} for why this needed its own explicit check: a
 * {@code LightningBolt} damages everything in an area around itself directly, with no collision
 * against the shield's own {@code ShieldOrbEntity} wall involved at all, so the wall that stops
 * literally everything else was never actually positioned to block it. "If lightning strikes the
 * shield, it can get through" was a real bug, not a rare edge case - Storm's own lightning, or
 * ordinary weather lightning, would land its damage on a shielded player exactly the same as an
 * unshielded one.
 *
 * <p>Cancels {@link EntityStruckByLightningEvent} outright (no damage, no fire, nothing) for
 * anyone the check finds shielded - "perfectly safe," per what was asked for, not just reduced
 * damage.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldLightningProtection
{
    // How far out from the struck entity's own chunk to look for a Heart Core that might be
    // shielding it - generous relative to both SHIELD_RADIUS and a typical drawn wall shape,
    // cheap since this only runs on the rare occasion something is actually about to be struck.
    private static final int SEARCH_CHUNK_RADIUS = 3;

    private ShieldLightningProtection()
    {
    }

    @SubscribeEvent
    public static void onEntityStruckByLightning(EntityStruckByLightningEvent event)
    {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level))
        {
            return;
        }
        if (isPositionUnderAnyShield(level, entity.position()))
        {
            event.setCanceled(true);
        }
    }

    private static boolean isPositionUnderAnyShield(ServerLevel level, net.minecraft.world.phys.Vec3 pos)
    {
        int centerChunkX = ((int) pos.x) >> 4;
        int centerChunkZ = ((int) pos.z) >> 4;
        for (int cx = centerChunkX - SEARCH_CHUNK_RADIUS; cx <= centerChunkX + SEARCH_CHUNK_RADIUS; cx++)
        {
            for (int cz = centerChunkZ - SEARCH_CHUNK_RADIUS; cz <= centerChunkZ + SEARCH_CHUNK_RADIUS; cz++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null)
                {
                    continue;
                }
                for (java.util.Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet())
                {
                    if (entry.getValue() instanceof HeartCoreBlockEntity heart && heart.isPositionShielded(pos))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
