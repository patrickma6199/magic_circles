package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.item.AthameItem;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Every curse currently holding, and the blade holding it.
 *
 * <p>A curse outlives the casting, bound twice over: to the athame that laid it and to the ring it
 * was worked in. It ends when that exact blade stops existing as a bloodied thing - washed clean,
 * burned, despawned, destroyed - or when that ring is broken ({@link #onServerTick}). The victim can
 * do neither, which is the point of a curse: the blade is out of their reach, and a prisoner cannot
 * touch the ring holding them. Someone on the outside can.
 *
 * <p>Destruction is caught by watching item entities actually leave the world ({@link
 * #onItemLeaveLevel}) rather than by hunting for the blade every tick. Sweeping every inventory,
 * container and chunk looking for one knife would be both expensive and wrong - a blade sitting in
 * a chest in an unloaded chunk still exists, and a search like that would read it as gone and free
 * the prisoner. Watching for the removal itself only ever fires when the item is genuinely
 * destroyed or despawns.
 *
 * <p><b>Known gap:</b> a blade removed without ever existing as an item entity - {@code /clear},
 * or creative-mode deletion - is invisible to this, and its curses hold until someone finds
 * another way. In-game destruction of every ordinary kind is covered.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseRegistry
{
    private static final List<ActiveCurse> active = new ArrayList<>();
    private static final int RING_CHECK_INTERVAL_TICKS = 20;
    /** How far the ring's runes reach from its centre. */
    private static final int RING_REACH = 2;

    private CurseRegistry()
    {
    }

    public static void register(ActiveCurse curse)
    {
        active.add(curse);
    }

    public static List<ActiveCurse> active()
    {
        return active;
    }

    /** True while {@code victim} is held by a curse of this kind - used by the curses' own per-tick enforcement. */
    public static boolean isCursed(UUID victim, CurseKind kind)
    {
        for (ActiveCurse curse : active)
        {
            if (curse.kind() == kind && curse.victim().equals(victim))
            {
                return true;
            }
        }
        return false;
    }

    public static ActiveCurse find(UUID victim, CurseKind kind)
    {
        for (ActiveCurse curse : active)
        {
            if (curse.kind() == kind && curse.victim().equals(victim))
            {
                return curse;
            }
        }
        return null;
    }

    /**
     * A curse holds only as long as its ring does. Once a second every curse is checked against the
     * ring it was cast in: a rune missing, or the colours no longer making the curse that was worked
     * there, and it comes apart on the spot - wherever the victim happens to be.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || active.isEmpty()
                || event.getServer().getTickCount() % RING_CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<ActiveCurse> iterator = active.iterator();
        while (iterator.hasNext())
        {
            ActiveCurse curse = iterator.next();
            ServerLevel level = curse.level(server);
            if (level == null || ringStillHolds(level, curse))
            {
                continue;
            }
            curse.lift(server);
            iterator.remove();
            BlockPos anchor = curse.anchor();
            level.playSound(null, anchor, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.SOUL, anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5,
                    30, 1.2, 0.4, 1.2, 0.02);
        }
    }

    /**
     * Whether all twelve of the ring's runes are still there and still make this curse. Only the
     * runes themselves are read - not the empty corners or the centre a full ring check also cares
     * about, so setting something down beside the ring doesn't count as breaking it. A ring that
     * isn't fully loaded counts as holding: nobody near it can have broken it, and reading it would
     * load it.
     */
    private static boolean ringStillHolds(ServerLevel level, ActiveCurse curse)
    {
        BlockPos anchor = curse.anchor();
        if (!level.hasChunksAt(anchor.offset(-RING_REACH, 0, -RING_REACH), anchor.offset(RING_REACH, 0, RING_REACH)))
        {
            return true;
        }
        Map<RuneColor, Integer> counts = new EnumMap<>(RuneColor.class);
        for (int[] offset : MagicCircleRitual.RING_OFFSETS)
        {
            BlockState state = level.getBlockState(anchor.offset(offset[0], 0, offset[1]));
            if (!(state.getBlock() instanceof MagicCircleBlock))
            {
                return false;
            }
            counts.merge(state.getValue(MagicCircleBlock.COLOR), 1, Integer::sum);
        }
        return DarkRites.kindFor(counts) == curse.kind();
    }

    /** The blade is clean or gone - everything it was holding shut comes open. */
    public static void onBladeLost(ServerLevel level, UUID bladeId)
    {
        Iterator<ActiveCurse> iterator = active.iterator();
        while (iterator.hasNext())
        {
            ActiveCurse curse = iterator.next();
            if (curse.bladeId().equals(bladeId))
            {
                curse.lift(level.getServer());
                iterator.remove();
            }
        }
    }

    /**
     * Catches a bloodied athame being destroyed or despawning. Deliberately ignores an item merely
     * leaving because its chunk unloaded - that blade still exists, and treating it as lost would
     * quietly free every prisoner whenever someone walked away.
     */
    @SubscribeEvent
    public static void onItemLeaveLevel(EntityLeaveLevelEvent event)
    {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ItemEntity item))
        {
            return;
        }
        Entity.RemovalReason reason = item.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION)
        {
            return;
        }
        if (!item.getItem().is(ModItems.ATHAME.get()))
        {
            return;
        }
        UUID blade = AthameItem.bladeId(item.getItem());
        if (blade != null && event.getLevel() instanceof ServerLevel level)
        {
            onBladeLost(level, blade);
        }
    }
}
