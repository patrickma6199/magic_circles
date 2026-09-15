package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.FairyWard;
import com.patrickma.magiccircles.limbo.Veil;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;

/**
 * One purple wisp of a fairy's volley: a real projectile, flown like an arrow or a ghast's shot but
 * with nothing to explode. A volley of {@link #VOLLEY_SIZE} bursts out from the caster in every
 * direction, slows as it fans out, then each wisp turns and hunts its target down. Half a heart per
 * wisp that lands.
 *
 * <p>Walls stop them - they fizzle against the first block they meet - and so does a shield: a
 * player's ward is made of shield orbs, which are solid to projectiles, so a wisp striking one is
 * spent on it (and drains that shield the way any blow does). They pass through fairies, never
 * harm them, and ignore the caster's own ward. Nothing about them is saved; a volley that outlives
 * its moment simply isn't there after a reload.
 */
public class WispMissileEntity extends Projectile
{
    private static final EntityDataAccessor<Integer> DATA_TARGET =
            SynchedEntityData.defineId(WispMissileEntity.class, EntityDataSerializers.INT);

    public static final int VOLLEY_SIZE = 10;
    /** The burst: how long they fan outward before turning on the target. */
    private static final int OUTWARD_TICKS = 8;
    private static final int LIFETIME_TICKS = 120;
    private static final double BURST_SPEED = 0.45;
    private static final double CRUISE_SPEED = 0.7;
    /** Fraction of the way each tick a wisp swings toward its target - tight enough to hunt, loose enough to dodge. */
    private static final double TURN_RATE = 0.14;
    private static final float DAMAGE = 1.0f;
    private static final Vector3f PURPLE = new Vector3f(0.72f, 0.45f, 1.0f);

    public WispMissileEntity(EntityType<? extends WispMissileEntity> type, Level level)
    {
        super(type, level);
    }

    /** Fires a whole volley from {@code caster} at {@code target}. */
    public static void volley(ServerLevel level, LivingEntity caster, LivingEntity target)
    {
        Vec3 origin = caster.getEyePosition().subtract(0.0, 0.3, 0.0);
        for (int i = 0; i < VOLLEY_SIZE; i++)
        {
            WispMissileEntity wisp = new WispMissileEntity(ModEntities.WISP_MISSILE.get(), level);
            wisp.setOwner(caster);
            wisp.entityData.set(DATA_TARGET, target.getId());
            wisp.setPos(origin.x, origin.y, origin.z);
            double yaw = level.random.nextDouble() * Math.PI * 2.0;
            double pitch = (level.random.nextDouble() - 0.3) * Math.PI * 0.6;
            Vec3 direction = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            wisp.setDeltaMovement(direction.scale(BURST_SPEED));
            level.addFreshEntity(wisp);
        }
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.FAIRY_VOLLEY.get(),
                SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_TARGET, -1);
    }

    @Override
    public void tick()
    {
        super.tick();
        Vec3 motion = this.getDeltaMovement();
        if (this.tickCount > OUTWARD_TICKS)
        {
            // Both sides steer the same way off the same synced target, so the client's flight
            // matches the server's between position updates.
            Entity target = this.level().getEntity(this.entityData.get(DATA_TARGET));
            if (target != null && target.isAlive())
            {
                Vec3 aim = target.getBoundingBox().getCenter().subtract(this.position());
                if (aim.lengthSqr() > 1.0E-6)
                {
                    motion = motion.add(aim.normalize().scale(CRUISE_SPEED).subtract(motion).scale(TURN_RATE));
                }
            }
        }
        else
        {
            motion = motion.scale(0.9);
        }
        this.setDeltaMovement(motion);

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit))
        {
            this.onHit(hit);
        }
        if (this.isRemoved())
        {
            return;
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5f);

        if (this.level().isClientSide)
        {
            for (int i = 0; i < 2; i++)
            {
                double t = i / 2.0;
                this.level().addParticle(new DustParticleOptions(PURPLE, 0.9f),
                        this.getX() - motion.x * t, this.getY() - motion.y * t, this.getZ() - motion.z * t, 0.0, 0.0, 0.0);
            }
        }
        else if (this.tickCount > LIFETIME_TICKS)
        {
            fizzle();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity)
    {
        return super.canHitEntity(entity) && !(entity instanceof FairyEntity) && !(entity instanceof WispMissileEntity)
                && !Veil.isBehindVeil(entity) && !isCastersOwnWard(entity);
    }

    /** The caster's own ward keeps others out, not its own shots in. */
    private boolean isCastersOwnWard(Entity entity)
    {
        Entity owner = this.getOwner();
        return owner != null && entity instanceof ShieldOrbEntity
                && entity.getPersistentData().hasUUID(FairyWard.ORB_TAG)
                && entity.getPersistentData().getUUID(FairyWard.ORB_TAG).equals(owner.getUUID());
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        if (!this.level().isClientSide)
        {
            // A shield orb takes the blow the same way - which is exactly what blocking it means.
            result.getEntity().hurt(this.damageSources().indirectMagic(this, this.getOwner()), DAMAGE);
            fizzle();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);
        if (!this.level().isClientSide)
        {
            fizzle();
        }
    }

    private void fizzle()
    {
        if (this.level() instanceof ServerLevel level)
        {
            level.sendParticles(new DustParticleOptions(PURPLE, 1.0f), this.getX(), this.getY(), this.getZ(),
                    4, 0.1, 0.1, 0.1, 0.0);
        }
        this.discard();
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }
}
