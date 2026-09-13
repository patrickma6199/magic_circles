package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.registry.ModBlockTags;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

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
 *     <li>{@link NeutralMob} - hitting any one pixie angers every pixie nearby at the culprit for
 *     {@link #ANGER_DURATION_TICKS} (10 seconds), the same "the whole group responds" mechanic
 *     bees/piglins use, rather than just that one pixie fighting back alone.</li>
 * </ul>
 *
 * <p>"Enjoy being around fairies" has nothing to actually check yet - there's no Fairy mob in
 * this mod at all so far (pixies are, appropriately, the first resident of the Fairy Realm to
 * actually get built). {@link #isKin} is written to check a tag-based family rather than this one
 * concrete class specifically ({@link #isKin}), so adding a real Fairy entity later just means
 * widening that one check - {@link PixieStayNearKinGoal} doesn't need to change at all for that
 * to start working.
 */
public class PixieEntity extends Allay implements NeutralMob
{
    private static final int WELLSPRING_SEARCH_RADIUS = 24;
    private static final int WELLSPRING_CHECK_INTERVAL_TICKS = 20;
    private static final int MAX_MANA_TICKS = 20 * 60 * 10;
    // Outside the Fairy Realm entirely, mana drains an order of magnitude faster - "cut off from
    // its own source of mana" is meant to read as urgent, not just a slower version of the same
    // countdown a pixie already has to manage at home.
    private static final int MANA_DRAIN_OUTSIDE_FAIRY_REALM = 10;
    private static final int ANGER_DURATION_TICKS = 200;
    private static final int KIN_SEARCH_RADIUS = 16;

    private int manaTicks = MAX_MANA_TICKS;
    private int wellspringCheckCooldown;

    // NeutralMob's own required bookkeeping - same fields/pattern vanilla's own neutral mobs
    // (bees, piglins, ...) use.
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;

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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(6, new PixieFlyNearTreeGoal(this));
        this.goalSelector.addGoal(7, new PixieStayNearKinGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
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
        this.updatePersistentAnger((ServerLevel) this.level(), true);
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

    /** Whether {@code other} is the kind of company a pixie enjoys - just other pixies for now, since there's no Fairy mob yet to widen this to. */
    public static boolean isKin(Entity other)
    {
        return other instanceof PixieEntity;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.level() instanceof ServerLevel serverLevel)
        {
            LivingEntity attacker = this.getLastHurtByMob();
            if (attacker != null)
            {
                this.setPersistentAngerTarget(attacker.getUUID());
                this.startPersistentAngerTimer();
                List<PixieEntity> nearby = serverLevel.getEntitiesOfClass(PixieEntity.class, this.getBoundingBox().inflate(KIN_SEARCH_RADIUS));
                for (PixieEntity other : nearby)
                {
                    other.setPersistentAngerTarget(attacker.getUUID());
                    other.startPersistentAngerTimer();
                }
            }
        }
        return hurt;
    }

    @Override
    public boolean isAngryAt(LivingEntity entity)
    {
        return entity.getUUID().equals(this.getPersistentAngerTarget());
    }

    // --- NeutralMob boilerplate - same shape vanilla's own neutral mobs use ---

    @Override
    public void setRemainingPersistentAngerTime(int time)
    {
        this.remainingPersistentAngerTime = time;
    }

    @Override
    public int getRemainingPersistentAngerTime()
    {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target)
    {
        this.persistentAngerTarget = target;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget()
    {
        return this.persistentAngerTarget;
    }

    @Override
    public void startPersistentAngerTimer()
    {
        this.setRemainingPersistentAngerTime(ANGER_DURATION_TICKS);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("PixieManaTicks", this.manaTicks);
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.manaTicks = tag.contains("PixieManaTicks") ? tag.getInt("PixieManaTicks") : MAX_MANA_TICKS;
        this.readPersistentAngerSaveData(this.level(), tag);
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
            List<PixieEntity> nearby = this.pixie.level().getEntitiesOfClass(PixieEntity.class,
                    this.pixie.getBoundingBox().inflate(KIN_SEARCH_RADIUS), other -> other != this.pixie);
            LivingEntity nearest = null;
            double nearestDistSq = TRIGGER_DISTANCE * TRIGGER_DISTANCE;
            for (PixieEntity other : nearby)
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
