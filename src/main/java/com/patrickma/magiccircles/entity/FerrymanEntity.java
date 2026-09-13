package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.limbo.LimboRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Escorts a {@code limbo/RiteOfPassage} caster into the Realm of the Dead, then stands idle
 * exactly where it delivered them - see {@link #casterUuid}. Right-clicking it (by anyone, not
 * only the caster) resolves through {@link LimboRegistry}: the caster returning is what actually
 * despawns this ferryman (in a puff of smoke, dragging along any tamed animal within {@link
 * #RESCUE_RADIUS} blocks); anyone *else* right-clicking it - a dead player being rescued, since
 * normal death routes here too - just returns that one player to their own corpse and leaves the
 * ferryman standing for the caster to use again. Deliberately not hostile and barely mobile - it
 * has no reason to do anything but wait.
 */
public class FerrymanEntity extends PathfinderMob implements GeoEntity
{
    public static final double RESCUE_RADIUS = 15.0;

    /** Matches the one animation in {@code assets/magiccircles/animations/ferryman.animation.json}. */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("Idle");

    /** How many of the deep dark's floating skulls circle him at once, and how they drift. */
    private static final int SKULL_COUNT = 3;
    private static final double SKULL_ORBIT_RADIUS = 1.1;
    private static final double SKULL_ORBIT_SPEED = 0.04;
    private static final int SKULL_SPAWN_INTERVAL = 6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID casterUuid;

    public FerrymanEntity(EntityType<? extends FerrymanEntity> type, Level level)
    {
        super(type, level);
        // He is someone's only way back. Left as an ordinary mob he despawned as soon as the
        // caster wandered out of his chunk, stranding them across the veil permanently.
        this.setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        // Idle is the only animation he has, and the only one he needs - he never moves and
        // cannot die.
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return cache;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide && this.tickCount % SKULL_SPAWN_INTERVAL == 0)
        {
            spawnSkullWisps();
        }
    }

    /**
     * The deep dark's own floating skulls, kept in a loose orbit around him. Each is spawned at a
     * point on a slowly turning ring with its radius and height jittered, so the ring reads as a
     * drift rather than as a clean circle - the particle itself always rises on its own once it
     * exists, which is what gives them their wisp-like wander.
     */
    private void spawnSkullWisps()
    {
        double baseAngle = this.tickCount * SKULL_ORBIT_SPEED;
        for (int i = 0; i < SKULL_COUNT; i++)
        {
            double angle = baseAngle + (Math.PI * 2.0 / SKULL_COUNT) * i
                    + (this.random.nextDouble() - 0.5) * 0.6;
            double radius = SKULL_ORBIT_RADIUS + (this.random.nextDouble() - 0.5) * 0.5;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + 0.6 + this.random.nextDouble() * 1.2;
            this.level().addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0.0, 0.02, 0.0);
        }
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals()
    {
        // No wandering, no movement goal at all - "stands idle where he had brought you."
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));
    }

    public void setCasterUuid(UUID uuid)
    {
        this.casterUuid = uuid;
    }

    public UUID getCasterUuid()
    {
        return casterUuid;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        LimboRegistry.onFerrymanInteract(this, player);
        return InteractionResult.CONSUME;
    }

    /** Called by {@link LimboRegistry} once the caster themselves has returned - the puff of smoke and the ride home for anything tamed standing nearby. */
    public void vanishInSmoke()
    {
        if (this.level() instanceof ServerLevel serverLevel)
        {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.4, 0.6, 0.4, 0.02);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, this.getSoundSource(), 1.0F, 0.6F);
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (casterUuid != null)
        {
            tag.putUUID("CasterUuid", casterUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("CasterUuid"))
        {
            casterUuid = tag.getUUID("CasterUuid");
        }
    }
}
