package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.mixin.ChunkMapAccessor;
import com.patrickma.magiccircles.mixin.TrackedEntityInvoker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes crossing the veil take effect for everyone at once.
 *
 * <p>Who can see what is decided by {@code mixin/EntityMixin} (through {@link Veil#visibleTo}), but
 * vanilla only asks that question when an entity or a watcher moves into a new 16-block section.
 * So someone who crossed over stayed visible to everyone already looking at them - an invisible
 * body with its armor on - until one of them walked far enough to be asked again. This asks
 * straight away, both ways round: who should now see this entity, and, if it is a player, what
 * they should now see.
 *
 * <p>The asking waits for the end of the tick, so it always reads the finished state however many
 * steps a crossing takes (game mode, effect, team, limbo record) and in whatever order they happen.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeilTracking
{
    private static final Set<Entity> pending = new LinkedHashSet<>();

    private VeilTracking()
    {
    }

    /** Re-checks visibility for {@code entity} at the end of this tick. Server side only; a no-op on the client. */
    public static void refreshSoon(Entity entity)
    {
        if (!entity.level().isClientSide)
        {
            pending.add(entity);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty())
        {
            return;
        }
        List<Entity> batch = new ArrayList<>(pending);
        pending.clear();
        for (Entity entity : batch)
        {
            if (!entity.isRemoved() && entity.level() instanceof ServerLevel level)
            {
                refresh(level, entity);
            }
        }
    }

    private static void refresh(ServerLevel level, Entity entity)
    {
        Int2ObjectMap<?> trackers = ((ChunkMapAccessor) level.getChunkSource().chunkMap).magiccircles$entityMap();

        Object own = trackers.get(entity.getId());
        if (own != null)
        {
            ((TrackedEntityInvoker) own).magiccircles$updatePlayers(level.players());
        }

        if (entity instanceof ServerPlayer viewer)
        {
            for (Object tracker : trackers.values())
            {
                if (tracker != own)
                {
                    ((TrackedEntityInvoker) tracker).magiccircles$updatePlayer(viewer);
                }
            }
        }
    }
}
