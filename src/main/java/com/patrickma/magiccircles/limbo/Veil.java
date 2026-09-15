package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.entity.FerrymanEntity;
import com.patrickma.magiccircles.entity.GhostPhantomEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The single answer to "is this thing on the other side?" - the one predicate the whole veil is
 * built on, and the only thing {@code mixin/EntityMixin} and friends ask about.
 *
 * <p>The veil is a plane of existence laid over the ordinary dimensions rather than a place you
 * travel to. Nothing here moves anywhere; what changes is who can perceive whom. The dead, the
 * Ferryman, the phantoms that hunt behind it, and ghosted pets are all on the far side of it. The
 * living see none of them - not their bodies, not their particles, not a phantom's wing trail or
 * its glowing eyes - because {@code mixin/EntityMixin} stops those entities from ever being sent to
 * a living player's client at all. An entity the client was never told about cannot render
 * anything, which is why this is done at the tracking layer instead of by piling up cosmetic
 * fixes.
 *
 * <p>A player's corpse is deliberately NOT behind the veil: the living are supposed to find it.
 */
public final class Veil
{
    private Veil()
    {
    }

    public static boolean isBehindVeil(Entity entity)
    {
        if (entity instanceof Player player)
        {
            return LimboState.isInLimbo(player);
        }
        if (entity instanceof GhostPhantomEntity phantom)
        {
            // Not every phantom is a veiled one - a curse's witness hunts in plain sight, so that
            // everyone can see what the curser has following them.
            return phantom.isVeiled();
        }
        if (entity instanceof FerrymanEntity ferryman)
        {
            // Nor every Ferryman: the one the summoning rite drags into the living world stands
            // where anybody can walk up to him.
            return ferryman.isVeiled();
        }
        return entity.getPersistentData().getBoolean(LimboRegistry.PET_GHOST_TAG);
    }

    /**
     * Whether {@code viewer} should be told {@code self} exists at all. Only meaningful when
     * {@code self} is behind the veil - the living follow vanilla's own rules untouched.
     *
     * <p>Returning {@code true} for the veil-to-veil case is the important half: vanilla's {@code
     * ServerPlayer#broadcastToPlayer} hides a spectator from every non-spectator unconditionally,
     * with no hook to opt out. That rule is exactly what would stop a living-bodied rite caster
     * from ever finding the dead person they crossed over to rescue, so the mixin overrides it.
     */
    public static boolean visibleTo(Entity self, Player viewer)
    {
        // A haunting belongs to exactly one person. Nobody else is ever told it is there, which is
        // what makes it read as something only the Marked can see rather than a mob that happens
        // to be standing around - see MarkedByTheDarkManager.
        if (self instanceof FerrymanEntity ferryman && ferryman.getHauntTarget() != null)
        {
            return viewer.getUUID().equals(ferryman.getHauntTarget());
        }
        // Deathsight lets the living look across without stepping over - see curse/Deathsight.
        return isBehindVeil(viewer) || com.patrickma.magiccircles.curse.Deathsight.has(viewer);
    }
}
