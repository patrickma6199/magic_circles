package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.FairyRealmChunkGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * A one-time, whole-dimension sweep that discards every dropped item ({@link ItemEntity}) lying
 * around in the Fairy Realm - cleanup for whatever the various one-time world edits ({@code
 * WorldTree}, {@code FairyPortalRuins}, {@code FairyRealmShield}) leave on the ground while they
 * carve/build (or from earlier testing on a world that predates one of them).
 *
 * <p>Hooked to the very first server tick rather than {@code ServerStartedEvent} directly, since
 * that's the one point guaranteed to run after *every* other class's own {@code
 * ServerStartedEvent} handler has already finished - those all run synchronously to completion
 * during server startup, before the tick loop ever begins, but Forge doesn't guarantee any
 * particular order *between* independent {@code @SubscribeEvent} handlers in different classes.
 * Waiting for the first tick sidesteps that entirely instead of needing this class to know about
 * (and explicitly sequence after) every other one-time placement.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyRealmCleanup
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // A little past the island's own edge, so anything that rolled or was pushed just past the
    // true shoreline before the boundary shield existed still gets swept up.
    private static final int SWEEP_MARGIN = 20;

    private static boolean pending = true;

    private FairyRealmCleanup()
    {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !pending)
        {
            return;
        }
        pending = false;

        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm != null)
        {
            sweepIfNeeded(fairyRealm);
        }
    }

    /**
     * A much lighter companion to {@link #sweepIfNeeded}: every time any player actually steps
     * into the Fairy Realm, discard whatever dropped items are sitting in the chunks already
     * loaded around them - no force-loading the whole island (that's the one-time sweep's job),
     * just whatever's already there. Cheap enough to run on every single entry rather than only
     * the very first one, so the realm never has a chance to accumulate clutter between visits.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (!event.getTo().equals(ModDimensions.FAIRY_REALM))
        {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel fairyRealm))
        {
            return;
        }

        int removed = 0;
        for (Entity entity : fairyRealm.getAllEntities())
        {
            if (entity instanceof ItemEntity)
            {
                entity.discard();
                removed++;
            }
        }
        if (removed > 0)
        {
            LOGGER.info("Swept {} dropped item(s) from the Fairy Realm on player entry.", removed);
        }
    }

    private static synchronized void sweepIfNeeded(ServerLevel fairyRealm)
    {
        CleanupSavedData saved = fairyRealm.getDataStorage().computeIfAbsent(CleanupSavedData::load, CleanupSavedData::new, "magiccircles_dropped_item_sweep");
        if (saved.done)
        {
            return;
        }

        int radius = FairyRealmChunkGenerator.islandRadius() + SWEEP_MARGIN;
        int chunkRadius = (radius >> 4) + 1;
        LOGGER.info("Sweeping dropped items across the Fairy Realm (radius {}) - this happens once, ever.", radius);

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++)
        {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++)
            {
                double dist = Math.sqrt((double) cx * cx + cz * cz) * 16.0;
                if (dist > radius + 16.0)
                {
                    continue;
                }
                fairyRealm.getChunk(cx, cz);
            }
        }

        int removed = 0;
        for (Entity entity : fairyRealm.getAllEntities())
        {
            if (entity instanceof ItemEntity)
            {
                entity.discard();
                removed++;
            }
        }

        saved.done = true;
        saved.setDirty();
        LOGGER.info("Fairy Realm item sweep complete - removed {} dropped item(s).", removed);
    }

    public static final class CleanupSavedData extends SavedData
    {
        public boolean done = false;

        public static CleanupSavedData load(CompoundTag tag)
        {
            CleanupSavedData data = new CleanupSavedData();
            data.done = tag.getBoolean("done");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("done", done);
            return tag;
        }
    }
}
