package com.patrickma.magiccircles.ritual;

/**
 * Where a circle's wisps go while a spell is actively channeling through them, instead of
 * their normal resting behavior (wandering the circle, or orbiting a Heart Core). A spell
 * sets this on its {@code HeartCoreBlockEntity} when it starts and resets it to {@link #ORBIT}
 * when it ends; {@code ClientCircleWisps} reads it (synced from server to client - see
 * {@code HeartCoreBlockEntity#setWispChannel}) to decide where each wisp actually flies.
 *
 * <p>Every value is wired to at least one spell now - see {@code HeartCoreBlockEntity} for
 * which spell sets which channel.
 */
public enum WispChannel
{
    /** Normal resting behavior - orbit a Heart Core, or wander the circle if there isn't one. */
    ORBIT,
    /** Shoot skyward, as if carrying the spell up to the clouds. Used by the storm spell. */
    UP,
    /** Sink into the ground. */
    DOWN,
    /** Fly toward and vanish into the casting player. */
    TO_PLAYER,
    /** Burst outward from the circle and dissipate. */
    OUTWARD,
    /**
     * Each wisp flies to a specific entity instead of a shared destination - see
     * {@code HeartCoreBlockEntity#castHealSpell}, the only spell that uses this so far. The
     * entity each wisp is assigned to is synced separately (an int array of entity ids), not
     * carried by this enum value itself.
     */
    TO_TARGETS,
    /**
     * Gathered at, and pulsing outward from, the heart's own floating position rather than each
     * wisp's home rune - used by Tempest Ward (and Vital Surge), both of which used to reuse
     * {@link #UP} (shooting the wisps ~40 blocks straight up, out of view entirely - appropriate
     * for Storm, which {@link #UP} is actually meant for, but not for a spell with nothing
     * happening in the sky). Reverting to {@link #ORBIT} afterward is already a smooth transition
     * either way, since {@code ClientCircleWisps#orbitTarget} also orbits the heart itself rather
     * than each wisp's own rune.
     */
    FROM_HEART,
    /**
     * Shield only: each of the 12 rune wisps flies from its own home rune out to, and then orbits
     * along, the shield's own real boundary loop ({@code client/ShieldBoundaryPath} - a plain
     * circle at {@code HeartCoreBlockEntity#shieldRadius()}, or the actual outer wall footprint
     * when one was drawn) - "the wisps from the runes fly out and be the wisps that trace the
     * boundary," replacing an earlier version that both sent the rune wisps uselessly skyward
     * ({@link #FROM_HEART}/{@link #UP}) *and* conjured an entirely separate set of new boundary
     * wisps from nothing. {@code client/ShieldRingWisps} still adds a few genuinely *new*
     * supplementary wisps on top of these 12, but only once the loop itself is long enough
     * (40+ blocks of perimeter) that 12 alone would look sparse.
     */
    TRACE_SHIELD
}
