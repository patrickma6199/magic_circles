package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.limbo.LimboRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a creature leaves behind when it crosses over - a pet, a villager, anything with a name
 * (see {@code limbo/DeathLimboManager#crossesOver}). The {@link PlayerCorpseEntity}'s counterpart,
 * minus the inventory: it holds nothing, it only marks where they fell, looking just as they did.
 *
 * <p>What it looks like is a snapshot of the creature's own save data with everything that isn't
 * appearance stripped out, synced to clients, where {@code client/CreatureCorpseRenderer} builds a
 * stand-in of that creature from it and lays it on its side. The creature remembers its corpse (see
 * {@link #CORPSE_TAG}), and the corpse goes when the creature is brought back or truly dies - or, if
 * that happened somewhere the corpse couldn't be reached, the next time the corpse sees the creature
 * alive and no longer a ghost.
 */
public class CreatureCorpseEntity extends Mob
{
    /** On the creature's own persistent data: the UUID of the body it left. */
    public static final String CORPSE_TAG = "MagicCirclesCorpse";
    private static final int BODY_CHECK_INTERVAL_TICKS = 100;
    /** Everything in a creature's save data that is about what it is doing or carrying rather than how it looks. */
    private static final String[] NOT_APPEARANCE = {
            "Pos", "Motion", "Rotation", "UUID", "Passengers", "Leash", "Brain", "Offers", "Gossips",
            "Inventory", "Items", "ActiveEffects", "Attributes", "CustomName", "CustomNameVisible",
            "Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "Air", "FallDistance",
            "OnGround", "PortalCooldown", "Invulnerable", "NoAI", "Sitting", "ForgeData", "ForgeCaps"
    };

    private static final EntityDataAccessor<String> DATA_CREATURE_TYPE =
            SynchedEntityData.defineId(CreatureCorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> DATA_APPEARANCE =
            SynchedEntityData.defineId(CreatureCorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);

    @Nullable
    private UUID creatureUuid;

    public CreatureCorpseEntity(EntityType<? extends CreatureCorpseEntity> type, Level level)
    {
        super(type, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        // Stays exactly where the death happened, the same as a player's body.
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void registerGoals()
    {
        // None - a corpse does nothing.
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_CREATURE_TYPE, "");
        this.entityData.define(DATA_APPEARANCE, new CompoundTag());
    }

    /** Takes the creature's appearance, and ties the two of them together. Call before it becomes a ghost. */
    public void captureFrom(LivingEntity creature)
    {
        CompoundTag appearance = new CompoundTag();
        creature.saveWithoutId(appearance);
        for (String key : NOT_APPEARANCE)
        {
            appearance.remove(key);
        }
        this.entityData.set(DATA_CREATURE_TYPE, EntityType.getKey(creature.getType()).toString());
        this.entityData.set(DATA_APPEARANCE, appearance);
        this.creatureUuid = creature.getUUID();
        creature.getPersistentData().putUUID(CORPSE_TAG, this.getUUID());
        if (creature.hasCustomName())
        {
            this.setCustomName(creature.getCustomName());
            this.setCustomNameVisible(true);
        }
    }

    public String creatureType()
    {
        return this.entityData.get(DATA_CREATURE_TYPE);
    }

    public CompoundTag appearance()
    {
        return this.entityData.get(DATA_APPEARANCE);
    }

    /** Takes away the body a creature left, if it can be found - when the creature comes back, or is gone for good. */
    public static void removeFor(Entity creature)
    {
        CompoundTag data = creature.getPersistentData();
        if (!data.hasUUID(CORPSE_TAG) || !(creature.level() instanceof ServerLevel level))
        {
            return;
        }
        if (level.getEntity(data.getUUID(CORPSE_TAG)) instanceof CreatureCorpseEntity corpse)
        {
            corpse.discard();
        }
        data.remove(CORPSE_TAG);
    }

    @Override
    public void tick()
    {
        super.tick();
        // Lies the way it fell - a mob's body otherwise slowly turns to follow a head that never moves.
        float facing = this.getYRot();
        this.yBodyRot = facing;
        this.yBodyRotO = facing;
        this.yHeadRot = facing;
        this.yHeadRotO = facing;

        if (!this.level().isClientSide && creatureUuid != null && this.tickCount % BODY_CHECK_INTERVAL_TICKS == 0
                && this.level() instanceof ServerLevel level)
        {
            // Brought back somewhere this body couldn't be reached from at the time - it goes now.
            Entity creature = level.getEntity(creatureUuid);
            if (creature != null && creature.isAlive() && !LimboRegistry.isCreatureGhost(creature))
            {
                this.discard();
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(Entity entity)
    {
        // Nothing - a body can't be nudged around.
    }

    @Override
    protected boolean canRide(Entity vehicle)
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString("CreatureType", creatureType());
        tag.put("Appearance", appearance());
        if (creatureUuid != null)
        {
            tag.putUUID("CreatureUuid", creatureUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_CREATURE_TYPE, tag.getString("CreatureType"));
        this.entityData.set(DATA_APPEARANCE, tag.getCompound("Appearance"));
        creatureUuid = tag.hasUUID("CreatureUuid") ? tag.getUUID("CreatureUuid") : null;
    }
}
