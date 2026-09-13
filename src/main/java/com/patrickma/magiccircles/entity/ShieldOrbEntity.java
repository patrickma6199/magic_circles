package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.CommandedSpellCasting;
import com.patrickma.magiccircles.ShieldFlashEffects;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One node of a shield spell's boundary (see {@code HeartCoreBlockEntity#startShieldSpell}) -
 * many of these, arranged either in a Fibonacci-sphere spread or along a custom outer wall
 * shape, together approximate the "unbreakable portal blocks" the shield is made of. They're
 * entities rather than real blocks specifically because the shield is meant to *not* replace
 * whatever terrain happens to be there (so breaking, say, the dirt underneath one leaves the
 * shield exactly as it was) - blocks can't coexist with other blocks at the same position, but
 * entities and blocks coexist freely.
 *
 * <p>Used by a player-cast Shield/Tempest Ward and a hand-cast Shield from a commanded
 * Heartstone - <b>not</b> by {@code FairyRealmShield}'s own permanent dimension boundary anymore,
 * which stopped using orbs entirely (see that class's own doc comment) in favor of pure
 * teleport-back containment plus a geometric flash triggered by detecting the boundary crossing
 * itself, rather than an entity being hit.
 *
 * <p>Every orb here is a real, solid obstacle ({@link #canBeCollidedWith()} unconditionally
 * {@code true}) - an earlier version left them non-collidable on the theory that a player-cast
 * shield needed to let its own caster pass through, but vanilla's entity collision check
 * (`Entity#canCollideWith`) is queried *on the mover*, not on this orb, with no clean way to
 * exempt just the caster anyway - so this just treats every shield as the "unbreakable ward" its
 * own book lore already calls it, caster included. {@code HeartCoreBlockEntity#tickShieldBoundary}'s
 * teleport-back push still runs regardless, purely as a failsafe for whatever a real but sparse
 * orb spread can't physically block on its own.
 *
 * <p>Being hit (an arrow, a punch, an explosion) triggers {@link ShieldFlashEffects#onOrbHit}'s
 * brief gold flash and outward ripple to every other nearby orb, and reports the hit's own damage
 * to the owning {@code HeartCoreBlockEntity#drainShieldMana} - the shield's real cost (1 mana per
 * hit point, shared across every orb this heart owns). It never removes the orb itself - there's
 * no health here to reduce, the orb never breaks - only the mana behind it runs out.
 *
 * <p>{@link #ownerPos} remembers which Heart Core spawned this node, purely so that block can find
 * and remove all of its own nodes later without needing to track individual entity references.
 */
public class ShieldOrbEntity extends Entity
{
    private BlockPos ownerPos = BlockPos.ZERO;
    // Set instead of ownerPos for a Heartstone-commanded cast (see CommandedSpellCasting) - there's
    // no Heart Core block behind this shield at all, just a player holding a stone, so #hurt drains
    // that player's own commanded Heartstone directly rather than looking up a block entity.
    @Nullable
    private UUID ownerPlayerUuid;

    public ShieldOrbEntity(EntityType<? extends ShieldOrbEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setOwnerPos(BlockPos pos)
    {
        this.ownerPos = pos.immutable();
    }

    public BlockPos getOwnerPos()
    {
        return ownerPos;
    }

    public void setOwnerPlayerUuid(UUID uuid)
    {
        this.ownerPlayerUuid = uuid;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return true;
    }

    @Override
    public boolean isPickable()
    {
        return true;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.level() instanceof ServerLevel serverLevel)
        {
            ShieldFlashEffects.onOrbHit(serverLevel, this);
            // 1 mana per hit point, drained from whichever Heart Core owns this orb - see
            // HeartCoreBlockEntity#drainShieldMana's own doc comment. Every orb belonging to the
            // same heart shares this one call/pool, so multiple orbs hit in the same tick (a
            // creeper exploding against several at once) each independently subtract their own
            // damage from the same mana total rather than each having their own separate budget.
            if (ownerPlayerUuid != null)
            {
                CommandedSpellCasting.drainShieldMana(serverLevel, ownerPlayerUuid, amount);
            }
            else if (serverLevel.getBlockEntity(this.ownerPos) instanceof HeartCoreBlockEntity heart)
            {
                heart.drainShieldMana(serverLevel, amount);
            }
        }
        return true;
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        ownerPos = new BlockPos(tag.getInt("OwnerX"), tag.getInt("OwnerY"), tag.getInt("OwnerZ"));
        if (tag.hasUUID("OwnerPlayerUuid"))
        {
            ownerPlayerUuid = tag.getUUID("OwnerPlayerUuid");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putInt("OwnerX", ownerPos.getX());
        tag.putInt("OwnerY", ownerPos.getY());
        tag.putInt("OwnerZ", ownerPos.getZ());
        if (ownerPlayerUuid != null)
        {
            tag.putUUID("OwnerPlayerUuid", ownerPlayerUuid);
        }
    }
}
