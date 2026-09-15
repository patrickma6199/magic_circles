package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.registry.ModBlockTags;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.registry.ModSounds;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A fairy's own less-sentient companion - "dogs to people," the same relationship an Allay
 * already has to its own model/animations/most of its AI, which is why this is a direct {@link
 * Allay} subclass rather than a fresh mob built from scratch: note-block-seeking, duplication,
 * item-carrying, all of it comes along for free just by extending the class and never touching
 * those systems (confirmed by reading {@code Allay}'s own source - none of that is hard-coded to
 * only work for the exact {@code EntityType} named "allay").
 *
 * <p>What's actually different from a plain Allay:
 * <ul>
 *     <li>{@link #customServerAiStep()} - needs the Fairy Realm's own Wellspring to survive.
 *     While in that dimension and within {@link #WELLSPRING_SEARCH_RADIUS} of a block tagged
 *     {@link ModBlockTags#WELLSPRING}, {@link #manaTicks} refills to {@link #MAX_MANA_TICKS};
 *     otherwise it drains, faster still in any *other* dimension (leaving the Fairy Realm cuts a
 *     pixie off from its own source of mana entirely, not just makes it harder to reach) - and at
 *     zero, the pixie dies of it.</li>
 *     <li>{@link PixieFlyNearTreeGoal}/{@link PixieStayNearKinGoal} - the two likes this was asked
 *     for ("enjoy flying close to the World Tree," "enjoy being around... other pixies").</li>
 *     <li>{@link FollowQueenGoal} - they like the Fairy Queen's company too, but only a few at a
 *     time (see {@link #MAX_QUEEN_FOLLOWERS}), so she is never mobbed.</li>
 *     <li>Purely passive: struck, a pixie - and any startled nearby - simply flies away
 *     ({@link FleeGoal}). Nothing here ever fights back.</li>
 *     <li>Its own voice (see {@code tools/gen_sounds.py}): a twitter of little glass whistles.</li>
 * </ul>
 */
public class PixieEntity extends Allay
{
    private static final int WELLSPRING_SEARCH_RADIUS = 24;
    private static final int WELLSPRING_CHECK_INTERVAL_TICKS = 20;
    private static final int MAX_MANA_TICKS = 20 * 60 * 10;
    // Outside the Fairy Realm entirely, mana drains an order of magnitude faster - "cut off from
    // its own source of mana" is meant to read as urgent, not just a slower version of the same
    // countdown a pixie already has to manage at home.
    private static final int MANA_DRAIN_OUTSIDE_FAIRY_REALM = 10;
    private static final int KIN_SEARCH_RADIUS = 16;
    private static final int FLEE_TICKS = 100;
    private static final double STARTLE_RADIUS = 8.0;
    /** How many pixies keep the queen company at once - "two or three," never the whole swarm. */
    private static final int MAX_QUEEN_FOLLOWERS = 3;

    /** The pixies currently following the queen, across the whole server - weak, so an unloaded one drops out on its own. */
    private static final Set<PixieEntity> QUEEN_FOLLOWERS = Collections.newSetFromMap(new WeakHashMap<>());

    private int manaTicks = MAX_MANA_TICKS;
    private int wellspringCheckCooldown;

    /** Who to get away from, and for how much longer - see {@link FleeGoal}. */
    @Nullable
    private UUID fleeFrom;
    private int fleeTicks;

    public PixieEntity(EntityType<? extends Allay> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Allay.createAttributes();
    }

    @Override
    protected void registerGoals()
    {
        super.registerGoals();
        this.goalSelector.addGoal(1, new FleeGoal(this));
        this.goalSelector.addGoal(4, new FollowQueenGoal(this));
        this.goalSelector.addGoal(6, new PixieFlyNearTreeGoal(this));
        this.goalSelector.addGoal(7, new PixieStayNearKinGoal(this));
    }

    @Override
    public void customServerAiStep()
    {
        super.customServerAiStep();
        if (--this.wellspringCheckCooldown <= 0)
        {
            this.wellspringCheckCooldown = WELLSPRING_CHECK_INTERVAL_TICKS;
            tickMana();
        }
        if (this.fleeTicks > 0)
        {
            this.fleeTicks--;
        }
    }

    private void tickMana()
    {
        boolean inFairyRealm = this.level().dimension().equals(ModDimensions.FAIRY_REALM);
        if (inFairyRealm && isNearWellspring())
        {
            this.manaTicks = MAX_MANA_TICKS;
            return;
        }

        int drain = (inFairyRealm ? 1 : MANA_DRAIN_OUTSIDE_FAIRY_REALM) * WELLSPRING_CHECK_INTERVAL_TICKS;
        this.manaTicks -= drain;
        if (this.manaTicks <= 0)
        {
            this.hurt(this.damageSources().starve(), Float.MAX_VALUE);
        }
    }

    private boolean isNearWellspring()
    {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos center = this.blockPosition();
        int r = WELLSPRING_SEARCH_RADIUS;
        for (int dx = -r; dx <= r; dx += 4)
        {
            for (int dy = -r; dy <= r; dy += 4)
            {
                for (int dz = -r; dz <= r; dz += 4)
                {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (this.level().getBlockState(cursor).is(ModBlockTags.WELLSPRING))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether {@code other} is the kind of company a pixie enjoys - other pixies, and the fairies they belong with. */
    public static boolean isKin(Entity other)
    {
        return other instanceof PixieEntity || other instanceof FairyEntity;
    }

    /** Struck, it flies - and startles every pixie nearby into flying with it. Nothing fights back. */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && this.level() instanceof ServerLevel serverLevel && source.getEntity() instanceof LivingEntity attacker)
        {
            startle(attacker);
            for (PixieEntity other : serverLevel.getEntitiesOfClass(PixieEntity.class, this.getBoundingBox().inflate(STARTLE_RADIUS)))
            {
                other.startle(attacker);
            }
        }
        return hurt;
    }

    private void startle(LivingEntity attacker)
    {
        this.fleeFrom = attacker.getUUID();
        this.fleeTicks = FLEE_TICKS;
    }

    // --- Voice ---

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.PIXIE_CHIRP.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.PIXIE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.PIXIE_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("PixieManaTicks", this.manaTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.manaTicks = tag.contains("PixieManaTicks") ? tag.getInt("PixieManaTicks") : MAX_MANA_TICKS;
    }

    /** Away from whoever struck it - up and off, re-aimed every few ticks as they move - for as long as the fright lasts. */
    private static final class FleeGoal extends Goal
    {
        private static final double FLEE_DISTANCE = 12.0;
        private static final double FLEE_RISE = 5.0;
        private static final int RE_AIM_TICKS = 10;

        private final PixieEntity pixie;
        private int aimTicks;

        FleeGoal(PixieEntity pixie)
        {
            this.pixie = pixie;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            return this.pixie.fleeTicks > 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.pixie.fleeTicks > 0;
        }

        @Override
        public void start()
        {
            this.aimTicks = 0;
            aim();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            if (++this.aimTicks >= RE_AIM_TICKS)
            {
                this.aimTicks = 0;
                aim();
            }
        }

        private void aim()
        {
            Vec3 away = Vec3.ZERO;
            if (this.pixie.fleeFrom != null && this.pixie.level() instanceof ServerLevel level
                    && level.getEntity(this.pixie.fleeFrom) instanceof LivingEntity foe)
            {
                away = this.pixie.position().subtract(foe.position());
            }
            away = new Vec3(away.x, 0.0, away.z);
            if (away.lengthSqr() < 1.0E-4)
            {
                double angle = this.pixie.getRandom().nextDouble() * Math.PI * 2.0;
                away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            }
            Vec3 target = this.pixie.position().add(away.normalize().scale(FLEE_DISTANCE)).add(0.0, FLEE_RISE, 0.0);
            this.pixie.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.6);
        }

        @Override
        public void stop()
        {
            this.pixie.fleeFrom = null;
        }
    }

    /**
     * Keeping the Fairy Queen company: now and then a pixie that spots her takes to drifting along
     * beside her for a minute or two, weaving loosely round her as she goes - but only while fewer
     * than {@link #MAX_QUEEN_FOLLOWERS} others are already doing so, and never for long, so she has
     * two or three about her rather than the whole swarm.
     */
    private static final class FollowQueenGoal extends Goal
    {
        private static final double NOTICE_RADIUS = 40.0;
        private static final double LOSE_RADIUS = 64.0;
        private static final int MIN_COOLDOWN = 20 * 20;
        private static final int MAX_COOLDOWN = 20 * 90;
        private static final int MIN_FOLLOW = 20 * 40;
        private static final int MAX_FOLLOW = 20 * 120;
        private static final int RE_AIM_TICKS = 8;
        private static final double ORBIT_RADIUS = 2.5;

        private final PixieEntity pixie;
        private int cooldown = 20 * 5;
        private int followTicks;
        private int aimTicks;
        @Nullable
        private FairyQueenEntity queen;

        FollowQueenGoal(PixieEntity pixie)
        {
            this.pixie = pixie;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (--this.cooldown > 0 || this.pixie.fleeTicks > 0
                    || !this.pixie.level().dimension().equals(ModDimensions.FAIRY_REALM))
            {
                return false;
            }
            this.cooldown = MIN_COOLDOWN + this.pixie.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
            QUEEN_FOLLOWERS.removeIf(other -> other.isRemoved() || !other.isAlive());
            if (QUEEN_FOLLOWERS.size() >= MAX_QUEEN_FOLLOWERS)
            {
                return false;
            }
            List<FairyQueenEntity> queens = this.pixie.level().getEntitiesOfClass(FairyQueenEntity.class,
                    this.pixie.getBoundingBox().inflate(NOTICE_RADIUS), FairyQueenEntity::isAlive);
            if (queens.isEmpty())
            {
                return false;
            }
            this.queen = queens.get(0);
            return true;
        }

        @Override
        public void start()
        {
            QUEEN_FOLLOWERS.add(this.pixie);
            this.followTicks = MIN_FOLLOW + this.pixie.getRandom().nextInt(MAX_FOLLOW - MIN_FOLLOW);
            this.aimTicks = 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            return --this.followTicks > 0 && this.pixie.fleeTicks == 0 && this.queen != null && this.queen.isAlive()
                    && this.pixie.distanceToSqr(this.queen) < LOSE_RADIUS * LOSE_RADIUS;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            if (++this.aimTicks < RE_AIM_TICKS || this.queen == null)
            {
                return;
            }
            this.aimTicks = 0;
            // A loose weave about her, each pixie on its own phase so they never bunch.
            double angle = (this.pixie.tickCount + this.pixie.getId() * 37) * 0.05;
            double x = this.queen.getX() + Math.cos(angle) * ORBIT_RADIUS;
            double z = this.queen.getZ() + Math.sin(angle) * ORBIT_RADIUS;
            double y = this.queen.getY() + 1.2 + Math.sin(angle * 1.7) * 0.8;
            this.pixie.getMoveControl().setWantedPosition(x, y, z, 1.1);
        }

        @Override
        public void stop()
        {
            QUEEN_FOLLOWERS.remove(this.pixie);
            this.queen = null;
        }
    }

    /** Low priority - eases off toward the World Tree every so often while in the Fairy Realm, rather than actively pathing there constantly (a pixie has its own business; it just likes the neighborhood). */
    private static final class PixieFlyNearTreeGoal extends Goal
    {
        private static final double TRIGGER_DISTANCE = 40.0;
        private static final int MIN_COOLDOWN = 200;
        private static final int MAX_COOLDOWN = 600;

        private final PixieEntity pixie;
        private int cooldown;

        PixieFlyNearTreeGoal(PixieEntity pixie)
        {
            this.pixie = pixie;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (!this.pixie.level().dimension().equals(ModDimensions.FAIRY_REALM))
            {
                return false;
            }
            if (this.cooldown-- > 0)
            {
                return false;
            }
            this.cooldown = MIN_COOLDOWN + this.pixie.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
            Vec3 pos = this.pixie.position();
            double dx = pos.x - WorldTree.CENTER_X;
            double dz = pos.z - WorldTree.CENTER_Z;
            return Math.sqrt(dx * dx + dz * dz) > TRIGGER_DISTANCE;
        }

        @Override
        public boolean canContinueToUse()
        {
            return false;
        }

        @Override
        public void start()
        {
            double angle = this.pixie.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 10.0 + this.pixie.getRandom().nextDouble() * 20.0;
            double targetX = WorldTree.CENTER_X + Math.cos(angle) * radius;
            double targetZ = WorldTree.CENTER_Z + Math.sin(angle) * radius;
            double targetY = WorldTree.groundY() + 10 + this.pixie.getRandom().nextInt(30);
            this.pixie.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 1.0);
        }
    }

    /** Low priority - occasionally drifts toward the nearest other pixie, the "enjoys being around its own kind" half of the ask. */
    private static final class PixieStayNearKinGoal extends Goal
    {
        private static final double TRIGGER_DISTANCE = 12.0;
        private static final int MIN_COOLDOWN = 100;
        private static final int MAX_COOLDOWN = 300;

        private final PixieEntity pixie;
        private int cooldown;

        PixieStayNearKinGoal(PixieEntity pixie)
        {
            this.pixie = pixie;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (this.cooldown-- > 0)
            {
                return false;
            }
            this.cooldown = MIN_COOLDOWN + this.pixie.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
            return findNearestKin() != null;
        }

        @Override
        public boolean canContinueToUse()
        {
            return false;
        }

        @Override
        public void start()
        {
            LivingEntity kin = findNearestKin();
            if (kin == null)
            {
                return;
            }
            Vec3 pos = kin.position();
            this.pixie.getMoveControl().setWantedPosition(pos.x, pos.y + 1.0, pos.z, 1.0);
        }

        @Nullable
        private LivingEntity findNearestKin()
        {
            List<LivingEntity> nearby = this.pixie.level().getEntitiesOfClass(LivingEntity.class,
                    this.pixie.getBoundingBox().inflate(KIN_SEARCH_RADIUS), other -> other != this.pixie && isKin(other));
            LivingEntity nearest = null;
            double nearestDistSq = TRIGGER_DISTANCE * TRIGGER_DISTANCE;
            for (LivingEntity other : nearby)
            {
                double distSq = this.pixie.distanceToSqr(other);
                if (distSq < nearestDistSq)
                {
                    nearest = other;
                    nearestDistSq = distSq;
                }
            }
            return nearest;
        }
    }
}
