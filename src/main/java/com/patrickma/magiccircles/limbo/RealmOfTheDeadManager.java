package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.GhostPhantomEntity;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hunt behind the veil: "occasionally hunted by phantoms while in the other side and the
 * frequency of them spawning is proportional to how long the person's been in that realm... only
 * hunting me there in increasing numbers proportional to my time there if I got into that plane of
 * existence through using the rite."
 *
 * <p>So this hunts rite casters only, and only while they are still across. An ordinary ghost is a
 * spectator and cannot be touched by anything, which is the point - the dead are waiting to be
 * found, not fighting. Someone who crossed deliberately brought a weapon and a living body with
 * them, and the hunt is what those are for.
 *
 * <p>Phantoms never follow anyone home: {@code GhostPhantomEntity} dismisses itself the moment its
 * victim is no longer behind the veil, so leaving ends the hunt rather than dragging it into the
 * living world.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RealmOfTheDeadManager
{
    private static final int PHANTOM_CHECK_INTERVAL_TICKS = 20 * 15;
    /** Below this, nothing hunts you - a quick errand across the veil isn't punished. */
    private static final int PHANTOM_GRACE_TICKS = 20 * 30;
    /** How many checks past the grace period it takes for the hunt to reach full intensity. */
    private static final double PHANTOM_RAMP_CHECKS = 40.0;
    private static final double PHANTOM_CHANCE_CAP = 0.6;
    /** "In increasing numbers" - how many can be circling at the very end of the ramp. */
    private static final int MAX_SIMULTANEOUS_PHANTOMS = 5;

    private static final Map<UUID, Integer> phantomCheckCountdown = new HashMap<>();

    private RealmOfTheDeadManager()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        if (!LimboState.hasActiveRite(player) || !(player.level() instanceof ServerLevel level))
        {
            phantomCheckCountdown.remove(player.getUUID());
            return;
        }
        tickPhantomHunt(level, player, level.getGameTime() - LimboState.crossedGameTime(player));
    }

    private static void tickPhantomHunt(ServerLevel level, ServerPlayer player, long elapsedTicks)
    {
        if (elapsedTicks < PHANTOM_GRACE_TICKS)
        {
            return;
        }
        int countdown = phantomCheckCountdown.merge(player.getUUID(), -1, Integer::sum);
        if (countdown > 0)
        {
            return;
        }
        phantomCheckCountdown.put(player.getUUID(), PHANTOM_CHECK_INTERVAL_TICKS);

        double progress = Math.min(1.0, (elapsedTicks - PHANTOM_GRACE_TICKS)
                / (double) PHANTOM_CHECK_INTERVAL_TICKS / PHANTOM_RAMP_CHECKS);
        if (level.random.nextDouble() >= progress * PHANTOM_CHANCE_CAP)
        {
            return;
        }

        // The longer they linger, the more of them are allowed to be circling at once.
        int allowed = 1 + (int) Math.round(progress * (MAX_SIMULTANEOUS_PHANTOMS - 1));
        long current = level.getEntitiesOfClass(GhostPhantomEntity.class,
                player.getBoundingBox().inflate(64.0),
                phantom -> player.getUUID().equals(phantom.getVictimUuid())).size();
        if (current >= allowed)
        {
            return;
        }

        spawnHuntingPhantom(level, player);
    }

    /**
     * Spawns a phantom locked onto {@code victim}. It is behind the veil by its very type (see
     * {@code Veil}), so the living are never sent it at all - no body, no wing trail, no glowing
     * eyes. The invisibility and shared team here are only about how it looks to those who *can*
     * see it: translucent, like everything else on the other side.
     */
    static void spawnHuntingPhantom(ServerLevel level, LivingEntity victim)
    {
        GhostPhantomEntity phantom = ModEntities.GHOST_PHANTOM.get().create(level);
        if (phantom == null)
        {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double radius = 20.0 + level.random.nextDouble() * 10.0;
        double x = victim.getX() + Math.cos(angle) * radius;
        double z = victim.getZ() + Math.sin(angle) * radius;
        double y = victim.getY() + 10.0 + level.random.nextDouble() * 10.0;
        phantom.moveTo(x, y, z, level.random.nextFloat() * 360.0f, 0.0f);
        phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(phantom.blockPosition()), MobSpawnType.EVENT, null, null);
        GhostVisibility.join(phantom);
        phantom.addEffect(GhostVisibility.invisibility());
        phantom.setVictim(victim);
        level.addFreshEntity(phantom);
    }
}
