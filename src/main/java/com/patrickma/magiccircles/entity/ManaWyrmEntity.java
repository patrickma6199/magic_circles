package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * A white, glowing, snake-like fish that only lives in Wellspring Water (see {@code
 * data/minecraft/tags/fluids/water.json} - tagging the Wellspring's own fluid as real
 * {@code minecraft:water} is what makes every bit of {@link AbstractFish}'s vanilla behavior
 * (swim AI, "suffocates and flops on land," bucket-catching) apply to it for free without this
 * class needing to reimplement any of it). Extending {@code AbstractFish} directly rather than
 * {@code AbstractSchoolingFish} (what Cod/Salmon use) - schooling wasn't asked for, and a mana
 * wyrm reads more like a solitary creature drifting through its own ocean than a shoal fish.
 *
 * <p><b>The fluid tag alone turned out not to be enough for real swimming.</b> Confirmed by
 * decompiling Forge's own patched {@code Entity} class: the classic vanilla
 * {@code updateFluidHeightAndDoFluidPushing(TagKey<Fluid>, double)} overload - what {@code
 * wasTouchingWater}/{@link #isInWater()} is actually built on - was rewritten to check fluid
 * *type identity* against {@code ForgeMod.WATER_TYPE} specifically when given {@code
 * FluidTags.WATER}, not the tag itself:
 * {@code if (tag == FluidTags.WATER) return this.isInFluidType(ForgeMod.WATER_TYPE.get());}. A
 * custom fluid with its own {@link ModFluidTypes#WELLSPRING_WATER} - tagged {@code minecraft:water}
 * or not - never matches that identity check, so {@link #isInWater()} silently stayed {@code
 * false} the entire time a wyrm sat in the Wellspring. {@link AbstractFish#travel} gates its own
 * *actual* swim physics on exactly that flag, falling back to a generic (barely-moving) travel
 * otherwise - which is exactly the "plays the swim animation, never actually goes anywhere, only
 * moves when knocked back" symptom this was. Players never showed this bug because player
 * swim/breathing already goes through the newer {@code isInFluidType}/{@code canSwimInFluidType}
 * hooks directly, never through this legacy flag at all.
 *
 * <p>{@link #isInWater()} and {@link #isEyeInFluid} are overridden below to also recognize {@link
 * ModFluidTypes#WELLSPRING_WATER} directly - the actual fix, applied once at the query-method
 * level rather than patched into every individual vanilla call site (travel, the flop-out-of-water
 * check, {@code FishMoveControl}'s own buoyancy nudge, ...) that reads {@link #isInWater()}.
 *
 * <p>"Slithers like a snake" is the one thing this doesn't attempt from scratch - vanilla's own
 * fish swim animation (a side-to-side undulating glide) already reads reasonably snake-like at a
 * glance, and building a true multi-segment, snake-body IK rig would be a much larger undertaking
 * than reskinning an existing fish model; {@code ManaWyrmRenderer} reuses vanilla's own {@code
 * SalmonModel} (the most elongated vanilla fish) rather than a new one.
 */
public class ManaWyrmEntity extends AbstractFish
{
    public ManaWyrmEntity(EntityType<? extends ManaWyrmEntity> type, Level level)
    {
        super(type, level);
    }

    /**
     * {@link AbstractFish#registerGoals} only adds {@code PanicGoal}(0), {@code
     * AvoidEntityGoal<Player>}(2), and a generic {@code FishSwimGoal}(4) - this slots {@link
     * HideInKelpGoal} in at 3, between "flee a nearby player" and "swim aimlessly," so a wyrm
     * with no more pressing reason to move heads for the nearest Glitter Weed patch instead of
     * just drifting - "likes to hide in the kelp forest," per what was asked for.
     */
    @Override
    protected void registerGoals()
    {
        super.registerGoals();
        this.goalSelector.addGoal(3, new HideInKelpGoal(this));
    }

    /**
     * Wyrms are placed exactly once, when the Wellspring ocean is generated, and there is no
     * natural spawning that could ever replace one. Left as an ordinary {@code WATER_AMBIENT} fish
     * they quietly despawned like vanilla cod whenever nobody was nearby, so the sixty placed at
     * worldgen drained away to none over time. They stay put instead.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public boolean isInWater()
    {
        return super.isInWater() || this.isInFluidType((type, height) -> type == ModFluidTypes.WELLSPRING_WATER.get(), false);
    }

    @Override
    public boolean isEyeInFluid(TagKey<Fluid> fluidTag)
    {
        if (fluidTag == FluidTags.WATER && this.getEyeInFluidType() == ModFluidTypes.WELLSPRING_WATER.get())
        {
            return true;
        }
        return super.isEyeInFluid(fluidTag);
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes()
    {
        return AbstractFish.createAttributes();
    }

    @Override
    public ItemStack getBucketItemStack()
    {
        // No dedicated "bucket of mana wyrm" item was asked for - catching one just hands back a
        // plain water bucket (the wyrm itself is lost, same as vanilla's own "fish evaporates"
        // shorthand would otherwise require its own item to avoid).
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.FISH_SWIM;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getFlopSound()
    {
        return SoundEvents.COD_FLOP;
    }

    /**
     * Seeks out the nearest Glitter Weed (tip or plant segment - see {@code
     * block/GlitterWeedBlock}) and lingers there a while, exactly like vanilla's own {@code
     * MoveToBlockGoal} subclasses (e.g. a fox eating berries) already do for their own target
     * block - reused as-is rather than reimplemented, since a wandering wyrm settling near a kelp
     * patch is exactly the "MoveToBlockGoal" shape (find nearest matching block, swim to it, stay
     * a long while, eventually move on) with nothing about it needing customization beyond {@link
     * #isValidTarget}. Vertical search range is generous (kelp now grows up to 40 tall - see
     * {@code worldgen/WellspringOcean}) since a wyrm anywhere near a patch's own height should be
     * able to find it, not just one swimming level with the seabed.
     */
    private static class HideInKelpGoal extends MoveToBlockGoal
    {
        private static final int SEARCH_RANGE = 16;
        private static final int VERTICAL_SEARCH_RANGE = 20;

        HideInKelpGoal(ManaWyrmEntity wyrm)
        {
            super(wyrm, 1.0D, SEARCH_RANGE, VERTICAL_SEARCH_RANGE);
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos)
        {
            BlockState state = level.getBlockState(pos);
            return state.is(ModBlocks.GLITTER_WEED.get()) || state.is(ModBlocks.GLITTER_WEED_PLANT.get());
        }
    }
}
