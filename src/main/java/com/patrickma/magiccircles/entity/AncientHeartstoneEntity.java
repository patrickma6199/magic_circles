package com.patrickma.magiccircles.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A permanent, gold, unbreakable heart hovering above the Wellspring - see {@code
 * worldgen/WorldTree#carveWell} for the well itself and {@code
 * worldgen/AncientHeartstonePlacement} for where exactly this one gets placed (once, ever, like
 * the World Tree/portal ruins). Book lore ({@code BookOfTheFaye}) calls it what's said to actually
 * hold up {@code FairyRealmShield}'s own boundary around the whole realm - it doesn't really drive
 * that shield in code (the shield is unconditional and permanent regardless of this entity), but
 * nothing here contradicts the story either: it's simply never able to be damaged or removed by
 * anything short of admin commands, the same as the shield itself.
 *
 * <p>Purely decorative otherwise - no spellcasting, no interaction of its own. {@link
 * com.patrickma.magiccircles.client.WellspringWisps}' own ambient wisp cloud already orbits
 * roughly this same spot (its {@code baseHeight} range already spans 1-4.5 blocks above the well),
 * so placing this entity in the middle of that existing cloud is what actually makes it read as
 * "all the wisps by default orbit this, like a normal heart" - nothing about the wisp system
 * itself needed to change.
 */
public class AncientHeartstoneEntity extends Entity
{
    public AncientHeartstoneEntity(EntityType<? extends AncientHeartstoneEntity> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
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
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return false;
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }
}
