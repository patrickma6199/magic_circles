package com.patrickma.magiccircles.limbo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The vanilla-legal trick behind "the dead need to be invisible" (to ordinary players) while
 * still being genuinely visible to whoever's actually looking for them - a real {@code
 * GameType.SPECTATOR} player is hard-invisible to literally everyone who isn't *also* a
 * spectator ({@code ServerPlayer#broadcastToPlayer} returns {@code false} unconditionally for any
 * non-spectator viewer - confirmed by decompiling it, no override hook exists), which would make
 * a rite caster unable to ever find a ghost at all. This uses the ordinary Invisibility effect
 * instead, plus one shared scoreboard team with {@code setSeeFriendlyInvisibles(true)} - a
 * genuinely invisible entity looks completely normal (full opacity, no swirl, no distortion) to
 * anyone sharing that team, and stays completely invisible to everyone else. Ghosts, tamed-pet
 * ghosts, currently-Marked rite casters, and the phantoms hunting them (see {@code
 * MarkedByTheDarkManager}) are all members - an ordinary uninvolved player sees none of it.
 *
 * <p>{@link #invisibility()} builds the effect instance with both {@code showParticles} and
 * {@code showIcon} off - "I do not want any potion effects visible for anyone," including the
 * small HUD icon that would otherwise show in the ghost's own inventory screen.
 */
public final class GhostVisibility
{
    private static final String TEAM_NAME = "magiccircles_veil";

    private GhostVisibility()
    {
    }

    private static PlayerTeam getOrCreateTeam(Scoreboard scoreboard)
    {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null)
        {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
            team.setSeeFriendlyInvisibles(true);
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        }
        return team;
    }

    /** Adds {@code entity} to the shared "can see the dead" team - works for any entity (a real player or a mob), not just real players. */
    public static void join(Entity entity)
    {
        if (!(entity.level() instanceof ServerLevel level))
        {
            return;
        }
        boolean joining = !isOnVeilTeam(entity);
        Scoreboard scoreboard = level.getScoreboard();
        scoreboard.addPlayerToTeam(entity.getScoreboardName(), getOrCreateTeam(scoreboard));
        if (joining)
        {
            // Crossing over: everyone already watching has to be asked again whether they still can.
            VeilTracking.refreshSoon(entity);
        }
    }

    public static void leave(Entity entity)
    {
        if (!(entity.level() instanceof ServerLevel level))
        {
            return;
        }
        if (level.getScoreboard().removePlayerFromTeam(entity.getScoreboardName()))
        {
            VeilTracking.refreshSoon(entity);
        }
    }

    public static boolean isOnVeilTeam(Entity entity)
    {
        return entity.getTeam() != null && TEAM_NAME.equals(entity.getTeam().getName());
    }

    /**
     * Whether this entity is behind the veil, judged only from what every client is already told:
     * everything over there is both invisible and on the veil team. Deathsight puts the living on
     * the team too, but never makes them invisible, so they still count as this side.
     */
    public static boolean appearsBehindVeil(Entity entity)
    {
        return entity.isInvisible() && isOnVeilTeam(entity);
    }

    /** Permanent, silent - see this class's own doc comment for why both flags are off. */
    public static MobEffectInstance invisibility()
    {
        MobEffectInstance invisibility = new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false);
        // Being dead is not something milk cures.
        invisibility.setCurativeItems(new java.util.ArrayList<>());
        return invisibility;
    }
}
