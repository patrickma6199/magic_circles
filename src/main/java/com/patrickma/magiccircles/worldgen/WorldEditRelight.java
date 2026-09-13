package com.patrickma.magiccircles.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * One-line fix for a real, reproducible symptom: chunks the Fairy Realm's one-time world edits
 * ({@link WorldTree}, {@link FairyPortalRuins}, {@code FairyRealmShield}) force-load already go
 * through the normal chunk-generation light pass (as part of reaching {@code ChunkStatus.LIGHT})
 * *before* any of these classes ever touch a single block - that initial light computation is
 * for whatever {@link FairyRealmChunkGenerator} put there on its own (mostly empty air above a
 * flat/mountain surface). Every block these classes place immediately afterward - the World
 * Tree's ~487,000 blocks, the portal ruins' hill and chamber, the boundary shield's entities
 * (no light impact, but the orbs' own chunks still got force-loaded early) - changes that same
 * area completely, but nothing re-derives the light for it: {@code Level#setBlock} only ever
 * queues a light *update* around the one block that changed, it never redoes the chunk's own
 * initial light sourcing from scratch. Queuing ~487,000 individual updates for content this
 * dense is exactly what "the lighting looks glitchy" (dark patches, blocks that stay unlit until
 * something nearby is broken/placed) actually looks like in practice.
 *
 * <p>{@link #relight} re-derives each touched chunk's own light sources outright, immediately
 * after a placement finishes - rather than leaving that many queued per-block updates to work
 * themselves out only once a player happens to wander near enough to force it.
 */
public final class WorldEditRelight
{
    private WorldEditRelight()
    {
    }

    public static void relight(ServerLevel level, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ)
    {
        for (int cx = minChunkX; cx <= maxChunkX; cx++)
        {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            {
                level.getChunkSource().getLightEngine().propagateLightSources(new ChunkPos(cx, cz));
            }
        }
    }
}
