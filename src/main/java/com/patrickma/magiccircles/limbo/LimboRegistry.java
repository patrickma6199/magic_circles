package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.entity.FerrymanEntity;
import com.patrickma.magiccircles.entity.PlayerCorpseEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every transition into and out of being dead-but-not-gone: becoming a ghost, being ferried back,
 * timing out, or dying for real mid-rite. The state itself lives on the player (see {@link
 * LimboState}); this is the behavior built on top of it.
 *
 * <p>Nobody here ever changes dimension - a ghost stays exactly where they died and a rite caster
 * crosses over without going anywhere, which is the whole point of doing this with invisibility and
 * a shared team ({@link GhostVisibility}) instead of the separate dimension an earlier version of
 * this wrongly used.
 */
public final class LimboRegistry
{
    static final String PET_GHOST_TAG = "MagicCirclesPetGhost";
    /** Whether a ghost could pick things up in life - handed back exactly as it was if it returns. */
    private static final String GHOST_COULD_LOOT_TAG = "MagicCirclesGhostCouldLoot";

    /** Whether this creature is a ghost - a pet, villager or named thing waiting on the other side. */
    public static boolean isCreatureGhost(Entity entity)
    {
        return entity.getPersistentData().getBoolean(PET_GHOST_TAG);
    }

    private LimboRegistry()
    {
    }

    // ------------------------------------------------------------------
    // Becoming and un-becoming a ghost
    // ------------------------------------------------------------------

    /**
     * Strips a player out of the living world in place. {@code riteCaster} distinguishes the two
     * ways of getting here: an ordinary death leaves its owner untouchable (there is nothing left
     * for them to do but wait to be found), while someone who crossed over deliberately stays
     * damageable on purpose - the phantoms hunting them are supposed to be a real threat, which is
     * exactly why they were allowed to bring their gear.
     */
    public static void ghostifyPlayer(ServerPlayer player, boolean riteCaster)
    {
        player.setHealth(Math.max(1.0f, player.getHealth()));

        if (riteCaster)
        {
            // Survival, deliberately: a rite caster has to stay able to swing a weapon at the
            // phantoms hunting them and to be hurt by them, neither of which a spectator can do.
            // They get flight so they can actually move around the veil, and the restrictions that
            // spectator mode would have given for free are enforced by GhostRules instead.
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(false);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        else
        {
            // Genuinely spectator - flight, no-clip, invulnerability, no reach into the world,
            // nothing picked up. All of it vanilla's, none of it imitated. Only possible because
            // mixin/ServerPlayerMixin lifts vanilla's "no non-spectator may see a spectator" rule
            // for anyone else behind the veil.
            player.setGameMode(GameType.SPECTATOR);
        }

        // Not for hiding - mixin/EntityMixin does that far more completely. This is purely so the
        // other side renders as it should: vanilla draws an entity that is invisible but visible
        // *to you* at fifteen percent alpha, which is exactly the translucent look the veil wants,
        // and GhostVisibility's shared team is what makes it visible to you in the first place.
        player.addEffect(GhostVisibility.invisibility());
        GhostVisibility.join(player);
    }

    /** The reverse - called on a rescue, on a timeout, and on login-repair alike. */
    public static void unGhostPlayer(ServerPlayer player)
    {
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.noPhysics = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.removeEffect(MobEffects.INVISIBILITY);
        GhostVisibility.leave(player);
    }

    /** A tamed pet's version - no flight, and it stays solid; it still has to be physically led to the Ferryman. */
    public static void ghostifyPet(Entity pet)
    {
        if (pet instanceof LivingEntity living)
        {
            living.setHealth(Math.max(1.0f, living.getMaxHealth() * 0.1f));
            living.addEffect(GhostVisibility.invisibility());
        }
        pet.setInvulnerable(true);
        // Never in the way of the living - they build straight through it (see VeilIntangibility).
        pet.blocksBuilding = false;
        pet.getPersistentData().putBoolean(PET_GHOST_TAG, true);
        if (pet instanceof net.minecraft.world.entity.Mob mob)
        {
            // Nothing on the other side picks up what the living drop.
            pet.getPersistentData().putBoolean(GHOST_COULD_LOOT_TAG, mob.canPickUpLoot());
            mob.setCanPickUpLoot(false);
            mob.setTarget(null);
        }
        VeilCreatures.releaseClaims(pet);
        GhostVisibility.join(pet);
    }

    /** Brings a creature's ghost back to life where it stands - the Fairy Queen's gift to her fallen (see {@code FairyCourt}). */
    public static void reviveCreature(Entity creature)
    {
        unGhostPet(creature);
    }

    private static void unGhostPet(Entity pet)
    {
        // Back in its own skin, so the body it left has nothing left to mark.
        com.patrickma.magiccircles.entity.CreatureCorpseEntity.removeFor(pet);
        pet.setInvulnerable(false);
        pet.blocksBuilding = pet instanceof LivingEntity;
        pet.getPersistentData().remove(PET_GHOST_TAG);
        if (pet instanceof net.minecraft.world.entity.Mob mob && pet.getPersistentData().contains(GHOST_COULD_LOOT_TAG))
        {
            mob.setCanPickUpLoot(pet.getPersistentData().getBoolean(GHOST_COULD_LOOT_TAG));
            pet.getPersistentData().remove(GHOST_COULD_LOOT_TAG);
        }
        if (pet instanceof LivingEntity living)
        {
            living.removeEffect(MobEffects.INVISIBILITY);
        }
        GhostVisibility.leave(pet);
    }

    // ------------------------------------------------------------------
    // The Ferryman's own right-click handling
    // ------------------------------------------------------------------

    public static void onFerrymanInteract(FerrymanEntity ferryman, Player playerRaw)
    {
        if (!(playerRaw instanceof ServerPlayer player))
        {
            return;
        }

        if (ferryman.isSummoned())
        {
            emptyTheVeil(player, ferryman);
            return;
        }

        if (LimboState.isGhost(player))
        {
            // Anyone's dead can use anyone's Ferryman - being rescued is the point, and the
            // Ferryman stays standing afterwards for the caster who paid for him.
            returnGhostToCorpse(player);
        }
        else if (LimboState.hasActiveRite(player) && player.getUUID().equals(ferryman.getCasterUuid()))
        {
            returnCaster(player, ferryman);
        }
    }

    /**
     * Brings someone back exactly as the Ferryman would have - the {@code /revivify} command's own
     * doing. Works for either way of being across: an ordinary ghost returns to their corpse and
     * everything in it, a rite caster steps back into the body they left. Returns false if the
     * player wasn't behind the veil at all, so the command can say so rather than silently
     * pretending it did something.
     */
    public static boolean revive(ServerPlayer player)
    {
        if (LimboState.isGhost(player))
        {
            returnGhostToCorpse(player);
            return true;
        }
        if (LimboState.hasActiveRite(player))
        {
            UUID ferrymanUuid = LimboState.ferrymanUuid(player);
            FerrymanEntity ferryman = ferrymanUuid != null
                    && player.serverLevel().getEntity(ferrymanUuid) instanceof FerrymanEntity found ? found : null;
            returnCaster(player, ferryman);
            return true;
        }
        return false;
    }

    /**
     * The summoned Ferryman's one and only trade, from {@code curse/SummoningRite}: whoever
     * touches him crosses over in place of everyone else. They die on the spot - a real death,
     * corpse and all, so they are simply the next one waiting - and in exchange every soul still
     * behind the veil, anywhere, is pulled back into their own body at once.
     *
     * <p>The door opens once. When the sacrifice is complete he goes out in a breath of smoke, and
     * the next person who needs him will have to pay for the summoning all over again.
     */
    private static void emptyTheVeil(ServerPlayer toucher, FerrymanEntity ferryman)
    {
        List<ServerPlayer> freed = new ArrayList<>();
        for (ServerPlayer candidate : toucher.getServer().getPlayerList().getPlayers())
        {
            if (!candidate.getUUID().equals(toucher.getUUID()) && LimboState.isInLimbo(candidate))
            {
                freed.add(candidate);
            }
        }

        for (ServerPlayer ghost : freed)
        {
            revive(ghost);
            ghost.sendSystemMessage(Component.translatable("rite.magiccircles.summoning.freed",
                    toucher.getDisplayName()));
        }

        ferryman.level().playSound(null, ferryman.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                ferryman.getSoundSource(), 1.0f, 0.8f);
        if (ferryman.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.SOUL, ferryman.getX(), ferryman.getY() + 1.0, ferryman.getZ(),
                    80, 0.8, 1.0, 0.8, 0.05);
        }

        // Paid last, so the toucher's own death creates its corpse after everyone else is already
        // out - otherwise they would be freed by the very trade they are paying for.
        toucher.hurt(toucher.damageSources().magic(), Float.MAX_VALUE);

        // The price is paid; the door closes. SummoningRite hears him go and releases his summoner
        // from the one-Ferryman rule, so the glimpses can find them again.
        ferryman.vanishInSmoke();
    }

    /** An ordinary death's ghost being ferried back: body reclaimed, everything in it returned. */
    private static void returnGhostToCorpse(ServerPlayer player)
    {
        PlayerCorpseEntity corpse = findCorpse(player);
        unGhostPlayer(player);

        if (corpse != null)
        {
            player.getInventory().clearContent();
            ListTag items = corpse.getInventorySnapshot().getList("Items", 10);
            player.getInventory().load(items);
            player.giveExperiencePoints(corpse.getXpSnapshot());
            player.teleportTo((ServerLevel) corpse.level(), corpse.getX(), corpse.getY(), corpse.getZ(),
                    Set.of(), player.getYRot(), player.getXRot());
            corpse.discard();
        }

        // "they return to their corpse and get all their stuff back (items, armour, experience,
        // half health, half hunger)."
        player.setHealth(player.getMaxHealth() * 0.5f);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0f);
        LimboState.clear(player);
    }

    /**
     * The caster calling it a day - steps back into the body they left, and the Ferryman leaves
     * with any pets they found. {@code ferryman} may be null when {@code /revivify} is used and his
     * entity is gone (unloaded, or lost to an older save); the return still happens, there is just
     * nobody standing nearby to gather pets from.
     */
    private static void returnCaster(ServerPlayer player, @org.jetbrains.annotations.Nullable FerrymanEntity ferryman)
    {
        PlayerCorpseEntity body = findCorpse(player);
        unGhostPlayer(player);

        if (body != null)
        {
            player.teleportTo((ServerLevel) body.level(), body.getX(), body.getY(), body.getZ(),
                    Set.of(), player.getYRot(), player.getXRot());
            body.discard();
        }

        if (ferryman != null)
        {
            // "any animals close to the ferryman within a 15 block radius will return when the
            // caster of the rite does."
            for (Entity nearby : ferryman.level().getEntitiesOfClass(Entity.class,
                    ferryman.getBoundingBox().inflate(FerrymanEntity.RESCUE_RADIUS)))
            {
                if (nearby.getPersistentData().getBoolean(PET_GHOST_TAG))
                {
                    unGhostPet(nearby);
                }
            }
            ferryman.vanishInSmoke();
        }

        LimboState.clear(player);
    }

    private static PlayerCorpseEntity findCorpse(ServerPlayer player)
    {
        UUID corpseUuid = LimboState.corpseUuid(player);
        ResourceKey<Level> dimension = LimboState.corpseDimension(player);
        if (corpseUuid == null || dimension == null || player.getServer() == null)
        {
            return null;
        }
        ServerLevel level = player.getServer().getLevel(dimension);
        if (level == null)
        {
            return null;
        }
        return level.getEntity(corpseUuid) instanceof PlayerCorpseEntity corpse ? corpse : null;
    }

    // ------------------------------------------------------------------
    // The two "it went wrong" paths
    // ------------------------------------------------------------------

    /**
     * A rite caster dying for real - "they lose all their items permanently (like they fell in
     * lava), and the player has a vanilla respawn." The death itself is never intercepted; this
     * only clears the rite and sends the Ferryman away, stranding anyone who was counting on him.
     */
    public static void onRiteVisitorDied(ServerPlayer player)
    {
        UUID ferrymanUuid = LimboState.ferrymanUuid(player);
        if (ferrymanUuid != null && player.serverLevel().getEntity(ferrymanUuid) instanceof FerrymanEntity ferryman)
        {
            ferryman.vanishInSmoke();
        }
        PlayerCorpseEntity body = findCorpse(player);
        if (body != null)
        {
            body.discard();
        }
        unGhostPlayer(player);
        LimboState.clear(player);
    }

    /**
     * "If a dead player remains in the realm of the dead for a minecraft day, they will also have a
     * vanilla respawn and their corpse with all their stuff disappears." Waiting too long is
     * supposed to cost you everything, so the corpse is discarded rather than returned.
     */
    public static void onGhostTimedOut(ServerPlayer player)
    {
        DeathLimboManager.announce(player, DeathLimboManager.rebornMessage(player));
        PlayerCorpseEntity corpse = findCorpse(player);
        if (corpse != null)
        {
            corpse.discard();
        }
        unGhostPlayer(player);
        LimboState.clear(player);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        if (player.getServer() != null)
        {
            player.getServer().getPlayerList().respawn(player, false);
        }
    }
}
