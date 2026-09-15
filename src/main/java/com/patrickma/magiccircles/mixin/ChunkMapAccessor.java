package com.patrickma.magiccircles.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the server's per-entity trackers, so {@code limbo/VeilTracking} can make them look again
 * at who should see an entity the moment it crosses the veil - see that class for why.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor
{
    /** Values are {@code ChunkMap.TrackedEntity}, which is not public - cast them to {@link TrackedEntityInvoker}. */
    @Accessor("entityMap")
    Int2ObjectMap<?> magiccircles$entityMap();
}
