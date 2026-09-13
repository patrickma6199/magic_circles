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

    /** Set when the elk has been given seeds or a flower, spent when chalk is pressed to its antlers. */
    private boolean hasGift;

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
    }

    /**
     * Offerings and chalk. Feed an elk seeds or a flower and it carries the gift; chalk pressed
     * into its antlers afterwards comes away Red. The gift is spent in the exchange, so each piece
     * of chalk costs its own offering, and the chalk keeps whatever it had left - the same rule
     * every other way of tinting chalk follows (see {@code recipe/ChalkTintRecipe}).
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        ItemStack held = player.getItemInHand(hand);

        if (!hasGift && isOffering(held))
        {
            if (!this.level().isClientSide)
            {
                hasGift = true;
                if (!player.getAbilities().instabuild)
                {
                    held.shrink(1);
                }
                this.level().broadcastEntityEvent(this, (byte) 18);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (hasGift && held.getItem() instanceof ChalkItem && held.getItem() == ModItems.CHALK.get())
        {
            if (!this.level().isClientSide)
            {
                ItemStack red = new ItemStack(ModItems.RED_CHALK.get());
                red.setDamageValue(held.getDamageValue());
                held.shrink(1);
                if (!player.getInventory().add(red))
                {
                    player.drop(red, false);
                }
                hasGift = false;
                this.level().broadcastEntityEvent(this, (byte) 18);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    /** Grass seeds or any flower - what an elk will actually take from your hand. */
    private static boolean isOffering(ItemStack stack)
    {
        return stack.is(Items.WHEAT_SEEDS) || stack.is(ItemTags.FLOWERS);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HasGift", hasGift);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        hasGift = tag.getBoolean("HasGift");
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
