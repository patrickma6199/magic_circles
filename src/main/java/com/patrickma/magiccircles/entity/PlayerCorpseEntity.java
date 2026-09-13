package com.patrickma.magiccircles.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A dead player's body, left where they fell - "an entity itself which is a copy of the player's
 * skin and with armor on if the player had armor on and with the same item in hand," lying flat on
 * the ground, explicitly not an ArmorStand (which is what an earlier version of this was, and
 * looked like exactly what it was: a stand wearing someone's gear).
 *
 * <p>The skin comes from {@link #getOwnerUuid}, synced to clients so {@code
 * client/PlayerCorpseRenderer} can look the real skin up; the armor and held item are ordinary
 * {@link Mob} equipment slots, set for looks only. The authoritative copy of what the player
 * actually loses and gets back is {@link #getInventorySnapshot} - the full 41-slot inventory, far
 * more than the six slots on show. {@code limbo/LimboRegistry} is what reads that back onto a
 * returning player; nothing here responds to being right-clicked (see {@link #interact}), because
 * coming back is supposed to go through the Ferryman, not through looting your own body.
 *
 * <p>Also used, with no inventory captured at all, as the empty body a {@code RiteOfPassage} caster
 * leaves behind when they cross over deliberately - they keep everything they're carrying, so
 * there is nothing for that body to hold; it's purely the thing they walked out of.
 */
public class PlayerCorpseEntity extends Mob
{
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(PlayerCorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_SLIM_ARMS =
            SynchedEntityData.defineId(PlayerCorpseEntity.class, EntityDataSerializers.BOOLEAN);

    private CompoundTag inventorySnapshot = new CompoundTag();
    private int xpSnapshot;
    private String ownerName = "";

    public PlayerCorpseEntity(EntityType<? extends PlayerCorpseEntity> type, Level level)
    {
        super(type, level);
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        // A body doesn't settle, slide, or get shoved around - it stays exactly where the death
        // happened, which is also the spot the owner has to be brought back to.
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
        // Intentionally none - a corpse has no behavior of any kind.
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER_UUID, Optional.empty());
        this.entityData.define(DATA_SLIM_ARMS, false);
    }

    /** Takes everything off the player that the corpse needs to both look right and, later, give back. */
    public void captureFrom(Player player, boolean captureInventory)
    {
        this.setOwnerUuid(player.getUUID());
        this.ownerName = player.getName().getString();
        this.setCustomName(Component.literal(ownerName));
        this.setCustomNameVisible(true);

        if (captureInventory)
        {
            this.xpSnapshot = player.totalExperience;
            CompoundTag inventoryTag = new CompoundTag();
            ListTag itemsList = player.getInventory().save(new ListTag());
            inventoryTag.put("Items", itemsList);
            this.inventorySnapshot = inventoryTag;
        }

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack equipped = player.getItemBySlot(slot);
            if (!equipped.isEmpty())
            {
                this.setItemSlot(slot, equipped.copy());
            }
        }
    }

    public void setOwnerUuid(UUID uuid)
    {
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(uuid));
    }

    public UUID getOwnerUuid()
    {
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    public void setSlimArms(boolean slim)
    {
        this.entityData.set(DATA_SLIM_ARMS, slim);
    }

    public boolean hasSlimArms()
    {
        return this.entityData.get(DATA_SLIM_ARMS);
    }

    public CompoundTag getInventorySnapshot()
    {
        return inventorySnapshot;
    }

    public int getXpSnapshot()
    {
        return xpSnapshot;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        // Coming back goes through the Ferryman - a body can't be looted piece by piece out from
        // under whoever is meant to get all of it back at once.
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
    public void push(net.minecraft.world.entity.Entity entity)
    {
        // Nothing - the living shouldn't be able to nudge a body around.
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity vehicle)
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
        tag.put("InventorySnapshot", inventorySnapshot);
        tag.putInt("XpSnapshot", xpSnapshot);
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("SlimArms", hasSlimArms());
        UUID owner = getOwnerUuid();
        if (owner != null)
        {
            tag.putUUID("OwnerUuid", owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        inventorySnapshot = tag.getCompound("InventorySnapshot");
        xpSnapshot = tag.getInt("XpSnapshot");
        ownerName = tag.getString("OwnerName");
        setSlimArms(tag.getBoolean("SlimArms"));
        if (tag.hasUUID("OwnerUuid"))
        {
            setOwnerUuid(tag.getUUID("OwnerUuid"));
        }
    }
}
