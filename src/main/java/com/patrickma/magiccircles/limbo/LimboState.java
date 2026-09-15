package com.patrickma.magiccircles.limbo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Whether a given player is currently dead-but-not-gone, stored on the player themselves.
 *
 * <p>This used to be a pair of static {@code HashMap}s on {@code LimboRegistry}, which was an
 * outright bug rather than just a limitation: a ghost's own invisibility effect and their corpse
 * entity both persist to disk, so after any server restart the player came back still invisible,
 * still flying, corpse still standing there - and the only record tying the two together was gone.
 * The Ferryman would then refuse to do anything, because as far as it could tell the person
 * clicking it had never died. Keeping this in the player's own persistent NBT means limbo survives
 * exactly as long as the things it describes do.
 *
 * <p>{@link Player#PERSISTED_NBT_TAG} specifically (rather than the root of {@code
 * getPersistentData()}) because that sub-tag is the one vanilla copies across a respawn - relevant
 * on the timeout path, which respawns the player and needs the tag it just cleared to stay cleared.
 */
public final class LimboState
{
    private static final String ROOT = "MagicCirclesLimbo";
    private static final String GHOST = "Ghost";
    private static final String RITE = "Rite";
    private static final String CORPSE_UUID = "CorpseUuid";
    private static final String CORPSE_DIM = "CorpseDim";
    private static final String DEATH_TIME = "DeathTime";
    private static final String FERRYMAN_UUID = "FerrymanUuid";
    private static final String POLTERGEIST = "Poltergeist";

    private LimboState()
    {
    }

    /**
     * Read-only view. {@code getCompound} hands back a detached empty tag when the key is missing,
     * so this never writes anything - which matters because {@link #isInLimbo} is now on hot paths
     * (every entity-tracking update, via {@code Veil}), and the old shared accessor created the
     * sub-tags as a side effect of merely asking.
     */
    private static CompoundTag root(Player player)
    {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
    }

    /** The writable counterpart - only called from the handful of places that actually change state. */
    private static CompoundTag mutableRoot(Player player)
    {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!data.contains(Player.PERSISTED_NBT_TAG))
        {
            data.put(Player.PERSISTED_NBT_TAG, persisted);
        }
        CompoundTag limbo = persisted.getCompound(ROOT);
        if (!persisted.contains(ROOT))
        {
            persisted.put(ROOT, limbo);
        }
        return limbo;
    }

    /** True for an ordinary death's ghost - invisible, invulnerable, inventory left behind in a corpse. */
    public static boolean isGhost(Player player)
    {
        return root(player).getBoolean(GHOST);
    }

    /** True while a {@code RiteOfPassage} of this player's own is still unresolved - they crossed over deliberately and their Ferryman still stands. */
    public static boolean hasActiveRite(Player player)
    {
        return root(player).getBoolean(RITE);
    }

    /** Either kind of crossing - everything that makes someone not properly among the living. */
    public static boolean isInLimbo(Player player)
    {
        return isGhost(player) || hasActiveRite(player);
    }

    public static void beginGhost(Player player, UUID corpseUuid, ResourceKey<Level> corpseDimension, long deathGameTime)
    {
        CompoundTag tag = mutableRoot(player);
        tag.putBoolean(GHOST, true);
        tag.putUUID(CORPSE_UUID, corpseUuid);
        tag.putString(CORPSE_DIM, corpseDimension.location().toString());
        tag.putLong(DEATH_TIME, deathGameTime);
    }

    public static void beginRite(Player player, UUID ferrymanUuid, UUID bodyUuid,
                                 ResourceKey<Level> bodyDimension, long crossedGameTime)
    {
        CompoundTag tag = mutableRoot(player);
        tag.putBoolean(RITE, true);
        tag.putUUID(FERRYMAN_UUID, ferrymanUuid);
        tag.putUUID(CORPSE_UUID, bodyUuid);
        tag.putString(CORPSE_DIM, bodyDimension.location().toString());
        tag.putLong(DEATH_TIME, crossedGameTime);
    }

    /** Wipes every trace - called once someone has genuinely stopped being dead, whichever way that happened. */
    /** A ghost who died carrying a curse - see {@link Poltergeist}. Cleared with everything else on return. */
    public static boolean isPoltergeist(Player player)
    {
        return isGhost(player) && root(player).getBoolean(POLTERGEIST);
    }

    public static void makePoltergeist(Player player)
    {
        mutableRoot(player).putBoolean(POLTERGEIST, true);
    }

    public static void clear(Player player)
    {
        player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(ROOT);
    }

    public static UUID corpseUuid(Player player)
    {
        CompoundTag tag = root(player);
        return tag.hasUUID(CORPSE_UUID) ? tag.getUUID(CORPSE_UUID) : null;
    }

    public static UUID ferrymanUuid(Player player)
    {
        CompoundTag tag = root(player);
        return tag.hasUUID(FERRYMAN_UUID) ? tag.getUUID(FERRYMAN_UUID) : null;
    }

    public static ResourceKey<Level> corpseDimension(Player player)
    {
        String id = root(player).getString(CORPSE_DIM);
        return id.isEmpty() ? null : ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id));
    }

    /** When this player crossed over - the moment of death for a ghost, the moment of the rite for a caster. */
    public static long crossedGameTime(Player player)
    {
        return root(player).getLong(DEATH_TIME);
    }
}
