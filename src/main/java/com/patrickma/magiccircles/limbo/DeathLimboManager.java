package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.PlayerCorpseEntity;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ordinary death, for both players and tamed pets - not {@code RiteOfPassage}'s own deliberate
 * crossing. "When a player's pet or a player dies, I want them to enter a limbo realm where they
 * are invisible but still on the same dimension as where they had just died. floating right above
 * it like spectator mode. I also want their corpse to remain there."
 *
 * <p>The death is never allowed to actually happen: {@link #onLivingDeath} cancels {@link
 * LivingDeathEvent} outright and ghosts the entity in place. Because vanilla's {@code
 * LivingEntity#die} never runs, nothing is dropped and no experience is scattered, so there is no
 * drops handling here at all - the corpse takes the inventory directly instead. The one thing a
 * canceled death must still do immediately is restore positive health, since {@code hurt()} has
 * already zeroed it and {@code aiStep}'s own zero-health timer would otherwise quietly delete the
 * entity a second later regardless of the cancel; {@link LimboRegistry#ghostifyPlayer} does that
 * first thing.
 *
 * <p>A player already mid-rite dying for real is deliberately not intercepted - that death is
 * supposed to cost them everything (see {@link LimboRegistry#onRiteVisitorDied}).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeathLimboManager
{
    /** "If a dead player remains in the realm of the dead for a minecraft day" - one full day/night cycle. */
    private static final long GHOST_TIMEOUT_TICKS = 24000L;

    private DeathLimboManager()
    {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level))
        {
            return;
        }

        if (entity instanceof ServerPlayer player)
        {
            if (LimboState.hasActiveRite(player))
            {
                LimboRegistry.onRiteVisitorDied(player);
                return;
            }
            if (LimboState.isGhost(player))
            {
                // Already dead and somehow hurt to death again - never stack a second corpse on
                // top of the first, which would strand the original along with everything in it.
                event.setCanceled(true);
                LimboRegistry.ghostifyPlayer(player, false);
                return;
            }

            event.setCanceled(true);

            PlayerCorpseEntity corpse = new PlayerCorpseEntity(ModEntities.PLAYER_CORPSE.get(), level);
            corpse.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
            corpse.captureFrom(player, true);
            level.addFreshEntity(corpse);

            player.getInventory().clearContent();
            player.totalExperience = 0;
            player.experienceLevel = 0;
            player.experienceProgress = 0;

            LimboState.beginGhost(player, corpse.getUUID(), level.dimension(), level.getGameTime());
            LimboRegistry.ghostifyPlayer(player, false);
            player.teleportTo(player.getX(), player.getY() + 2.0, player.getZ());
            return;
        }

        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                && !entity.getPersistentData().getBoolean(LimboRegistry.PET_GHOST_TAG))
        {
            event.setCanceled(true);
            LimboRegistry.ghostifyPet(entity);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        if (LimboState.isGhost(player)
                && player.serverLevel().getGameTime() - LimboState.crossedGameTime(player) >= GHOST_TIMEOUT_TICKS)
        {
            LimboRegistry.onGhostTimedOut(player);
        }
    }

    /**
     * Puts a returning player back the way limbo left them. Their invisibility and their corpse
     * both survive a restart on their own, but flight and no-clip do not, so without this a ghost
     * would come back from a relog invisible and stranded on the ground.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && LimboState.isInLimbo(player))
        {
            LimboRegistry.ghostifyPlayer(player, LimboState.hasActiveRite(player));
        }
    }
}
