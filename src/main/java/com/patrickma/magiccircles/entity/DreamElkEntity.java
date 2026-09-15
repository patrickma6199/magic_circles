package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.item.ChalkItem;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A huge-antlered, magical elk, with its own hand-made model and keyframe animations (see {@code
 * client/DreamElkModel} and {@code client/DreamElkAnimation}).
 *
 * <p>Extends {@link Horse} to inherit horse behavior wholesale - taming, saddling, riding,
 * breeding, the lot - which is what was asked for. What it deliberately does not inherit is the
 * horse's voice: every sound {@link AbstractHorse} would emit is suppressed in {@link #playSound},
 * pending sounds of its own.
 *
 * <p>Two behaviors on top of a plain Horse: {@link LeapGoal}, a periodic voluntary long jump (not
 * vanilla's own rider-controlled horse jump, which only ever fires while mounted) - {@value
 * #LEAP_DISTANCE} blocks out, {@value #LEAP_HEIGHT} up, computed from basic projectile physics
 * and tuned to *approximately* land on target once Minecraft's own per-tick drag is accounted for
 * (verified in-code, not by watching it actually jump - that needs a real client). Its own trigger
 * condition explicitly allows starting from water, not just solid ground, per "this includes
 * starting in wellspring water" - and {@link #aiStep()}, which randomly plants a flower in the
 * grass as it walks.
 */
public class DreamElkEntity extends Horse
{
    /** Below this much horizontal movement per tick the elk counts as standing still, and idles rather than walks. */
    private static final double MOVING_THRESHOLD_SQR = 1.0E-6;

    /** How long an elk takes over a pinch of Arcane Dust before the Red Chalk comes out - five seconds. */
    private static final int DIGEST_TICKS = 100;
    private static final int GRUMBLE_INTERVAL_TICKS = 20;
    /** The elk has no voice of its own yet (see {@link #playSound}), so it borrows a few while it digests. */
    private static final SoundEvent[] GRUMBLES = {
            net.minecraft.sounds.SoundEvents.CAMEL_AMBIENT, net.minecraft.sounds.SoundEvents.COW_AMBIENT,
            net.minecraft.sounds.SoundEvents.LLAMA_AMBIENT, net.minecraft.sounds.SoundEvents.GOAT_AMBIENT
    };

    /** Ticks left before the Red Chalk comes out - zero when the elk isn't digesting anything. */
    private int digestTicks;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState eatAnimationState = new AnimationState();
    public final AnimationState deathAnimationState = new AnimationState();

    private static final double LEAP_DISTANCE = 8.0;
    private static final double LEAP_HEIGHT = 4.0;
    // Minecraft's own per-tick gravity/drag isn't a clean instant-impulse projectile - this
    // multiplier compensates for horizontal velocity bleeding off every tick after the leap
    // starts (0.91-ish air drag, applied ~20 times over a jump this size), so the elk actually
    // covers close to LEAP_DISTANCE by the time it lands rather than falling well short of it.
    private static final double HORIZONTAL_DRAG_COMPENSATION = 1.6;
    private static final int LEAP_COOLDOWN_MIN = 100;
    private static final int LEAP_COOLDOWN_RANDOM = 200;

    private static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
            Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP,
            Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
    };
    private static final double FLOWER_CHANCE_PER_TICK = 0.01;

    public DreamElkEntity(EntityType<? extends DreamElkEntity> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return AbstractHorse.createBaseHorseAttributes();
    }

    @Override
    protected void registerGoals()
    {
        super.registerGoals();
        this.goalSelector.addGoal(5, new LeapGoal(this));
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        boolean walking = this.getDeltaMovement().horizontalDistanceSqr() > MOVING_THRESHOLD_SQR;
        if (!this.level().isClientSide && this.onGround() && walking
                && this.getRandom().nextDouble() < FLOWER_CHANCE_PER_TICK)
        {
            plantRandomFlower();
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            updateAnimationStates();
        }
        else
        {
            tickDigestion();
        }
    }

    /**
     * Red Chalk, and the only way there is to get it. Feed an elk a pinch of Arcane Dust and it eats
     * it, stands there grumbling about it for five seconds, and then passes a fresh stick of Red
     * Chalk out behind it. One pinch, one stick - it won't take another until it's done.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.ARCANE_DUST.get()))
        {
            if (digestTicks > 0)
            {
                // Still busy with the last one - taken as handled, so it doesn't fall through to mounting.
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (!this.level().isClientSide)
            {
                digestTicks = DIGEST_TICKS;
                if (!player.getAbilities().instabuild)
                {
                    held.shrink(1);
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.GENERIC_EAT, this.getSoundSource(), 1.0f, 0.8f);
                this.level().broadcastEntityEvent(this, (byte) 18);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** One tick of digesting: standing still, grumbling now and then, and at the end, the chalk. */
    private void tickDigestion()
    {
        if (digestTicks <= 0)
        {
            return;
        }
        digestTicks--;
        this.getNavigation().stop();
        if (digestTicks == 0)
        {
            passRedChalk();
            return;
        }
        if (digestTicks % GRUMBLE_INTERVAL_TICKS == 0)
        {
            SoundEvent grumble = GRUMBLES[this.random.nextInt(GRUMBLES.length)];
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), grumble, this.getSoundSource(),
                    0.9f, 0.6f + this.random.nextFloat() * 0.3f);
        }
    }

    /** Out the back end - dropped just behind the hindquarters, rolling away from them. */
    private void passRedChalk()
    {
        float yaw = this.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        // Facing is (-sin, cos) in Minecraft's own convention, so behind is its reverse.
        double backX = net.minecraft.util.Mth.sin(yaw);
        double backZ = -net.minecraft.util.Mth.cos(yaw);
        double reach = this.getBbWidth() * 0.5 + 0.2;
        double x = this.getX() + backX * reach;
        double y = this.getY() + this.getBbHeight() * 0.45;
        double z = this.getZ() + backZ * reach;

        net.minecraft.world.entity.item.ItemEntity chalk = new net.minecraft.world.entity.item.ItemEntity(
                this.level(), x, y, z, new ItemStack(ModItems.RED_CHALK.get()));
        chalk.setDeltaMovement(backX * 0.15, 0.05, backZ * 0.15);
        chalk.setDefaultPickUpDelay();
        this.level().addFreshEntity(chalk);

        this.level().playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.CHICKEN_EGG, this.getSoundSource(), 1.0f, 0.5f);
        this.level().playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL, this.getSoundSource(), 0.8f, 0.8f);
        if (this.level() instanceof net.minecraft.server.level.ServerLevel server)
        {
            server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(0.85f, 0.3f, 0.28f), 1.0f),
                    x, y, z, 8, 0.12, 0.12, 0.12, 0.0);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("Digesting", digestTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        digestTicks = tag.getInt("Digesting");
    }

    /**
     * Ties each of the author's animations to the behavior it belongs to. Walking isn't here
     * because it's driven straight off limb swing in {@code DreamElkModel#setupAnim} instead, which
     * keeps the legs matched to the actual speed rather than to a state flag.
     */
    private void updateAnimationStates()
    {
        if (this.isDeadOrDying())
        {
            this.deathAnimationState.startIfStopped(this.tickCount);
            this.idleAnimationState.stop();
            this.eatAnimationState.stop();
            return;
        }

        if (this.getDeltaMovement().horizontalDistanceSqr() > MOVING_THRESHOLD_SQR)
        {
            this.idleAnimationState.stop();
        }
        else
        {
            this.idleAnimationState.startIfStopped(this.tickCount);
        }

        if (this.isEating())
        {
            this.eatAnimationState.startIfStopped(this.tickCount);
        }
        else
        {
            this.eatAnimationState.stop();
        }
    }

    // ------------------------------------------------------------------
    // Sound
    // ------------------------------------------------------------------

    /**
     * Silent for now - "make it not use the horse sounds and instead, ill add sounds later on."
     * Done here rather than only by nulling the getters below because {@link AbstractHorse} also
     * plays a few sounds from hardcoded call sites (the saddle click, the gallop, the step) that no
     * getter of ours can reach. Replace this with real sounds when there are some.
     */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch)
    {
        // Deliberately nothing.
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return null;
    }

    @Override
    protected SoundEvent getAngrySound()
    {
        return null;
    }

    @Override
    protected SoundEvent getEatingSound()
    {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        // Silent, same as above - AbstractHorse's own step sounds are horse sounds.
    }

    /**
     * Leaves flowers in its wake. Planted right where it's standing (give or take a block), and
     * only while it's actually walking, so a herd grazing in one spot doesn't carpet that spot.
     * Grass and other replaceable growth is planted straight over - requiring bare air would mean
     * almost never flowering in exactly the meadows it wanders through.
     */
    private void plantRandomFlower()
    {
        RandomSource random = this.getRandom();
        BlockPos pos = this.blockPosition().offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
        BlockState existing = this.level().getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced())
        {
            return;
        }
        if (!this.level().getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.DIRT))
        {
            return;
        }
        BlockState flower = FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState();
        if (flower.canSurvive(this.level(), pos))
        {
            this.level().setBlockAndUpdate(pos, flower);
        }
    }

    /**
     * Ridden, a tamed elk jumps the way it leaps on its own - {@value #LEAP_HEIGHT} blocks up and,
     * pressing forward, {@value #LEAP_DISTANCE} out the way it faces - rather than a horse's hop. The
     * jump bar still decides how much of that it puts in: a full charge is the whole leap.
     */
    @Override
    protected void executeRidersJump(float strength, Vec3 input)
    {
        double height = LEAP_HEIGHT * strength * this.getBlockJumpFactor();
        double vertical = Math.sqrt(2.0 * LeapGoal.GRAVITY * height) + this.getJumpBoostPower();
        double timeToPeak = vertical / LeapGoal.GRAVITY;
        double horizontal = input.z > 0.0 ? (LEAP_DISTANCE * strength / (2.0 * timeToPeak)) * HORIZONTAL_DRAG_COMPENSATION : 0.0;
        double yaw = Math.toRadians(this.getYRot());
        this.setDeltaMovement(-Math.sin(yaw) * horizontal, vertical, Math.cos(yaw) * horizontal);
        this.setIsJumping(true);
        this.hasImpulse = true;
        net.minecraftforge.common.ForgeHooks.onLivingJump(this);
    }

    /**
     * Vanilla's own horse jump (the {@code Attributes.JUMP_STRENGTH} attribute, {@code
     * AbstractHorse#getJumpPower}) only ever triggers from rider input via {@code
     * PlayerRideableJumping} - a wild, unridden horse never voluntarily jumps on its own. This is
     * a completely separate, self-contained leap: a real velocity impulse, not the rideable-jump
     * system, computed once at {@link #start()} from straightforward projectile math (peak height
     * from {@code vy = sqrt(2 * g * h)}, horizontal speed from distance over time-to-peak*2),
     * fired in a random horizontal direction.
     */
    private static final class LeapGoal extends Goal
    {
        private static final double GRAVITY = 0.08D;

        private final DreamElkEntity elk;
        private int cooldown;

        LeapGoal(DreamElkEntity elk)
        {
            this.elk = elk;
            this.cooldown = LEAP_COOLDOWN_MIN + elk.getRandom().nextInt(LEAP_COOLDOWN_RANDOM);
            this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (this.cooldown > 0)
            {
                this.cooldown--;
                return false;
            }
            boolean grounded = this.elk.onGround()
                    || this.elk.isInWater()
                    || this.elk.isInFluidType((type, height) -> type == ModFluidTypes.WELLSPRING_WATER.get(), false);
            return grounded && this.elk.getRandom().nextInt(3) == 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            return false;
        }

        @Override
        public void start()
        {
            this.cooldown = LEAP_COOLDOWN_MIN + this.elk.getRandom().nextInt(LEAP_COOLDOWN_RANDOM);

            double angle = this.elk.getRandom().nextDouble() * 2.0 * Math.PI;
            double verticalVelocity = Math.sqrt(2.0 * GRAVITY * LEAP_HEIGHT);
            double timeToPeak = verticalVelocity / GRAVITY;
            double horizontalSpeed = (LEAP_DISTANCE / (2.0 * timeToPeak)) * HORIZONTAL_DRAG_COMPENSATION;

            Vec3 horizontal = new Vec3(Math.cos(angle), 0.0, Math.sin(angle)).scale(horizontalSpeed);
            this.elk.setDeltaMovement(horizontal.x, verticalVelocity, horizontal.z);
            this.elk.setYRot((float) (Math.toDegrees(Math.atan2(-horizontal.x, horizontal.z))));
            this.elk.hasImpulse = true;
        }
    }
}
