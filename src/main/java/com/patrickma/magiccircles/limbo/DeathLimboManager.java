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
    /** How long the dead may linger behind the veil before their body crumbles - ten minutes. */
    private static final long GHOST_TIMEOUT_TICKS = 20L * 60 * 10;

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
                // A real death - vanilla carries on and announces it, in the crucible's words
                // rather than its own (see mixin/ServerPlayerMixin).
                REBORN.add(player.getUUID());
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
            // Cancelling the death also cancelled vanilla's announcement of it, so it is made here -
            // under the same gamerule - while the combat tracker still remembers how it happened.
            announce(player, player.getCombatTracker().getDeathMessage());
            boolean cursed = !com.patrickma.magiccircles.curse.DarkRites.kindsOn(player.getUUID()).isEmpty();

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
            if (cursed)
            {
                // The curse goes over with them.
                Poltergeist.begin(player);
            }
            return;
        }

        if (entity.getPersistentData().getBoolean(LimboRegistry.PET_GHOST_TAG))
        {
            // A ghost dying again is gone for good - and so is the body it left behind.
            com.patrickma.magiccircles.entity.CreatureCorpseEntity.removeFor(entity);
            return;
        }
        if (crossesOver(entity))
        {
            event.setCanceled(true);
            // The body stays where it fell, the same as a player's, until whoever it was comes back.
            com.patrickma.magiccircles.entity.CreatureCorpseEntity corpse =
                    new com.patrickma.magiccircles.entity.CreatureCorpseEntity(ModEntities.CREATURE_CORPSE.get(), level);
            corpse.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.yBodyRot, 0.0f);
            corpse.captureFrom(entity);
            level.addFreshEntity(corpse);
            LimboRegistry.ghostifyPet(entity);
            if (entity instanceof com.patrickma.magiccircles.entity.FairyEntity fairy)
            {
                com.patrickma.magiccircles.FairyCourt.receiveSoul(fairy);
            }
        }
    }

    /**
     * Who goes to the other side instead of simply dying: anyone's pet, any villager, and anything
     * that has been given a name - a name is what makes something someone, and someone is what the
     * veil keeps. Never the veil's own creatures or the corpses, and never a boss, whose death the
     * game itself depends on.
     */
    private static boolean crossesOver(LivingEntity entity)
    {
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                || entity instanceof PlayerCorpseEntity
                || entity instanceof com.patrickma.magiccircles.entity.FerrymanEntity
                || entity instanceof com.patrickma.magiccircles.entity.GhostPhantomEntity
                || entity instanceof com.patrickma.magiccircles.entity.FairyQueenEntity)
        {
            return false;
        }
        return (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null)
                || entity instanceof net.minecraft.world.entity.npc.Villager
                || entity instanceof com.patrickma.magiccircles.entity.FairyEntity
                || entity.hasCustomName();
    }

    /** Rite casters whose real death vanilla is about to announce - see {@link #deathMessageFor}. */
    private static final java.util.Set<java.util.UUID> REBORN = new java.util.HashSet<>();

    /** Sent to everyone, exactly when vanilla would have sent a death message. */
    static void announce(ServerPlayer player, net.minecraft.network.chat.Component message)
    {
        if (player.getServer() != null
                && player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SHOWDEATHMESSAGES))
        {
            player.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    /** Dying on the other side is not an ending, only a return to where every soul comes from. */
    static net.minecraft.network.chat.Component rebornMessage(ServerPlayer player)
    {
        return net.minecraft.network.chat.Component.translatable("death.magiccircles.reborn", player.getDisplayName());
    }

    /** Called from {@code mixin/ServerPlayerMixin} in place of vanilla's own death message. */
    public static net.minecraft.network.chat.Component deathMessageFor(ServerPlayer player, net.minecraft.network.chat.Component vanilla)
    {
        return REBORN.remove(player.getUUID()) ? rebornMessage(player) : vanilla;
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
