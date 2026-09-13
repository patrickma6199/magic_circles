package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.limbo.Veil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A phantom summoned to hunt one specific person - either a ghost lingering too long, or a
 * Marked rite caster ("you are occasionally hunted by phantoms... the frequency... proportional
 * to how long the persons been in that realm," and separately "Phantoms will also occasionally
 * spawn and attack them like they havent slept" for a Marked caster). "They must also only exist
 * in the realm of the dead so they must also be invisible to living players but can still damage
 * the dead (only targets those that entered using the black chalk rite). I am okay with the real
 * dead players seeing the phantom as well its just that the phantom wont attack them" - visibility
 * itself is handled the ordinary way (see {@code limbo/GhostVisibility} - this is just given the
 * Invisibility effect and joined to the shared veil team like any other ghost); what this subclass
 * actually adds is a hard-locked {@link #victimUuid} so vanilla's own phantom AI, which would
 * otherwise happily retarget any nearby sleepless player it can see, never attacks anyone else.
 *
 * <p>Vanilla {@code Phantom}'s own targeting goal ({@code PhantomAttackPlayerTargetGoal}, a plain
 * {@code NearestAttackableTargetGoal}) is package-private and can't be removed from here - instead
 * this locks out its effect at the source: {@link #setTarget} silently refuses any target other
 * than {@link #victimUuid}, so the goal keeps running (harmlessly finding nothing it's allowed to
 * set) while the attack/circling goals it never touches keep working normally off whatever target
 * {@link #setVictim} already locked in.
 */
public class GhostPhantomEntity extends Phantom
{
    private UUID victimUuid;

    public GhostPhantomEntity(EntityType<? extends Phantom> type, Level level)
    {
        super(type, level);
    }

    /** Vanilla's own Phantom attribute values - Phantom itself only ever exposes these via {@code DefaultAttributes}, not a public static method of its own. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.7)
                .add(Attributes.MOVEMENT_SPEED, 0.7)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    /**
     * "These phantoms cannot burn in the daytime." Vanilla's own daylight burn works by setting a
     * phantom alight from inside its tick, so refusing to catch fire at all is what actually stops
     * it - and it stops every other ignition source with it, which is fitting for something that
     * only half exists in this world anyway.
     */
    @Override
    public void setSecondsOnFire(int seconds)
    {
        // Deliberately nothing.
    }

    @Override
    public boolean fireImmune()
    {
        return true;
    }

    public void setVictim(LivingEntity victim)
    {
        this.victimUuid = victim.getUUID();
        this.setTarget(victim);
    }

    public UUID getVictimUuid()
    {
        return victimUuid;
    }

    /**
     * "They should not be able to leave the realm of the dead... it should no longer target me once
     * I leave." A phantom exists solely to hunt one person while that person is across the veil, so
     * the moment they are not - rescued, returned, or cured - it simply stops being, rather than
     * following them back into the living world where nobody could even see it.
     */
    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide || this.tickCount % 20 != 0)
        {
            return;
        }
        if (victimUuid == null)
        {
            this.discard();
            return;
        }
        Entity victim = ((ServerLevel) this.level()).getEntity(victimUuid);
        if (victim == null || !Veil.isBehindVeil(victim))
        {
            this.setTarget(null);
            this.discard();
        }
    }

    @Override
    public boolean canAttack(LivingEntity target)
    {
        return victimUuid != null && victimUuid.equals(target.getUUID()) && super.canAttack(target);
    }

    @Override
    public void setTarget(LivingEntity target)
    {
        // Something else in vanilla's own Mob/aiStep plumbing (not the removed targeting goal)
        // occasionally clears/reassigns target too - keep it locked to the one victim regardless.
        if (target == null || (victimUuid != null && !victimUuid.equals(target.getUUID())))
        {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (victimUuid != null)
        {
            tag.putUUID("VictimUuid", victimUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("VictimUuid"))
        {
            victimUuid = tag.getUUID("VictimUuid");
        }
    }
}
