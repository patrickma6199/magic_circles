package com.patrickma.magiccircles.block;

import com.patrickma.magiccircles.FairyPortalManager;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModBlockTags;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModItems;
import com.patrickma.magiccircles.ritual.HeartSpell;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import com.patrickma.magiccircles.ritual.PortalRitual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * What a magic circle's center rune becomes once a Heartstone is used on it. Not obtainable
 * or placeable any other way - there's deliberately no {@code BlockItem} for it. It has no
 * static model at all ({@link RenderShape#INVISIBLE}); everything you see is drawn by
 * {@link com.patrickma.magiccircles.client.HeartCoreBlockEntityRenderer}, including the real
 * torch-strength light (see {@code Properties.lightLevel} in {@link ModBlocks#HEART_CORE}).
 *
 * <p>The heart floats a block and a half above this block's own position - too high to be
 * part of this block's own hitbox - so a companion {@link HeartCoreTopBlock} is placed one
 * cell above it purely to give the floating heart itself something to click or break, and
 * forwards both actions back to the {@link #interact}/{@link #destroy} helpers here.
 *
 * <p>Right-clicking it with a Fairy Horn casts whichever spell its ring's color composition
 * matches (see {@link #interact}, {@code MagicCircleRitual#detectSpell},
 * {@code HeartSpell}) as long as its ring is complete and it has enough mana.
 *
 * <p>Breaking it doesn't leave air - see {@link #destroy} - it turns back into a magic circle
 * rune (a circle's center is never empty) and drops a Heartstone item carrying whatever mana
 * this one held.
 */
public class HeartCoreBlock extends BaseEntityBlock
{
    private static final VoxelShape SHAPE = box(4, 0, 4, 12, 3, 12);
    private static final int WELLSPRING_SEARCH_RADIUS = 15;

    public HeartCoreBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HeartCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.HEART_CORE.get(), HeartCoreBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.HEART_CORE.get(), HeartCoreBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        return interact(level, pos, player, hand);
    }

    /** Shared by this block and {@link HeartCoreTopBlock}, which forwards here with its own position minus one. */
    static InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        // Empty hand + Blessed by the Wellspring: absorb this heart's own (complete, idle) ring
        // into a commanded Heartstone instead of casting anything - see
        // HeartCoreBlockEntity#absorbRingIntoHeartstone for the actual mechanics/error cases.
        if (stack.isEmpty() && player.hasEffect(com.patrickma.magiccircles.registry.ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            if (level.isClientSide)
            {
                return InteractionResult.SUCCESS;
            }
            if (!(level.getBlockEntity(pos) instanceof HeartCoreBlockEntity absorbHeart))
            {
                return InteractionResult.PASS;
            }
            return absorbHeart.absorbRingIntoHeartstone((ServerLevel) level, player, hand);
        }

        if (!stack.is(ModItems.FAIRY_HORN.get()))
        {
            return InteractionResult.PASS;
        }

        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof HeartCoreBlockEntity heart))
        {
            return InteractionResult.PASS;
        }

        // Forbidden magic (see limbo/RiteOfPassage) and a Heart Core's own magic are mutually
        // exclusive - checked before anything else, including the shift-click mana query, since
        // a Marked player isn't meant to interact with a working heart at all right now.
        if (player.hasEffect(com.patrickma.magiccircles.registry.ModEffects.MARKED_BY_THE_DARK.get()))
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.marked_refusal").withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResult.CONSUME;
        }

        // Checked before anything spell-related (and works even mid-spell) - shift-clicking is
        // just a status check, not an action that should ever be blocked or consume mana.
        if (player.isShiftKeyDown())
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.mana_query",
                    heart.getMana(), HeartstoneItem.MAX_MANA), true);
            return InteractionResult.CONSUME;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // Checked before the ring-shape/color flow below, not after: a completed portal
        // pattern fills this ring's corners, which the rounded 12-ring check specifically
        // requires to be *empty* (see MagicCircleRitual) - so a portal ring would otherwise
        // always fail that check and show "ring broken" before ever getting a chance here.
        // matchesBorder (not matches) is what's checked here specifically because by the time
        // anyone's ready to cast this, the 8 cells matches() requires to be air hold water
        // instead - see PortalRitual#matchesBorder.
        if (PortalRitual.matchesBorder(level, pos))
        {
            if (!FairyPortalManager.hasWaterPit(level, pos))
            {
                player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.portal_needs_water").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (heart.getMana() < HeartCoreBlockEntity.PORTAL_MANA_COST)
            {
                player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.not_enough_mana").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            heart.setMana(heart.getMana() - HeartCoreBlockEntity.PORTAL_MANA_COST);
            heart.openWaterPortal(serverLevel, player);
            return InteractionResult.CONSUME;
        }

        if (!MagicCircleRitual.hasCompleteRing(level, pos))
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.ring_broken").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // Which spell (if any) this ring casts depends entirely on its color composition - a
        // ring with no spell wired to its composition is a real circle shape but not a
        // recognized one, so it gets its own message rather than either silently casting the
        // wrong thing or reusing "ring_broken" for a ring that isn't actually broken.
        HeartSpell spell = MagicCircleRitual.detectSpell(level, pos);
        if (spell == null)
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.spell_not_recognized").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // Only prolonged spells (a duration, their own dedicated state) are gated here - a
        // one-shot spell (Flowers, Heal, XP, Cleansing Rain, ...) fires and finishes instantly
        // regardless of what else is running, so it never needs to check any of this. A heart
        // can run up to 2 prolonged spells at once (recast a *different* color composition
        // while the first is still going - see HeartSpell#isProlonged), but not a third, and
        // not the same one twice over (which would leak the first cast's orbs/state).
        if (spell.isProlonged())
        {
            if (heart.isSpellTypeActive(spell))
            {
                player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.spell_active").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (heart.activeSpellCount() >= 2)
            {
                player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.two_spells_active").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
        }

        // Mana can only ever be recharged near a real Wellspring - see ModBlockTags#WELLSPRING,
        // which no block is tagged with yet (there's no Wellspring block in the mod at all so
        // far), so this spell can't actually be cast yet. That's deliberate, not a bug: it used
        // to regenerate mana "from the air," which no longer exists as a thing this mod does.
        if (spell == HeartSpell.MANA_FONT && !hasNearbyWellspring(level, pos))
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.no_wellspring_nearby").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // Mana Font is the one exception - its whole job is refilling an empty heart, so
        // requiring any mana at all just to start it would make a fully-drained Heart Core a
        // dead end with no way back (nothing else in this mod ever adds mana to one on its own).
        // Its own HeartSpell#manaCost() (10) is left alone rather than zeroed out - that value
        // still matters elsewhere (HeartSpell#minManaCost, which drives the "ran dry" wisp
        // animation) even though it's never actually charged for starting this one spell.
        if (spell != HeartSpell.MANA_FONT && heart.getMana() < spell.manaCost())
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.not_enough_mana").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        // SHIELD, MANA_FONT, and VITAL_SURGE don't deduct their cost upfront - it's just the
        // minimum needed to be worth starting, since their actual cost is an ongoing drain/gain
        // over time (see HeartCoreBlockEntity). Every other spell is a one-time spend.
        if (spell != HeartSpell.SHIELD && spell != HeartSpell.MANA_FONT && spell != HeartSpell.VITAL_SURGE)
        {
            heart.setMana(heart.getMana() - spell.manaCost());
        }
        switch (spell)
        {
            case STORM -> heart.startStormSpell(serverLevel);
            case SHIELD -> heart.startShieldSpell(serverLevel, player);
            case FLOWERS -> heart.castFlowerSpell(serverLevel);
            case HEAL -> heart.castHealSpell(serverLevel);
            case XP -> heart.castXpSpell(serverLevel);
            case CLEANSING_RAIN -> heart.castCleansingRainSpell(serverLevel);
            case TEMPEST_WARD -> heart.startTempestWardSpell(serverLevel, player);
            case MANA_FONT -> heart.startManaFontSpell(serverLevel);
            case BLOOM_OF_LIFE -> heart.castBloomOfLifeSpell(serverLevel);
            case VERDANT_HARVEST -> heart.castVerdantHarvestSpell(serverLevel);
            case VITAL_SURGE -> heart.startVitalSurgeSpell(serverLevel);
        }
        return InteractionResult.CONSUME;
    }

    /** True if any block tagged {@link ModBlockTags#WELLSPRING} is within {@value #WELLSPRING_SEARCH_RADIUS} blocks of {@code center}. */
    private static boolean hasNearbyWellspring(Level level, BlockPos center)
    {
        long radiusSq = (long) WELLSPRING_SEARCH_RADIUS * WELLSPRING_SEARCH_RADIUS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -WELLSPRING_SEARCH_RADIUS; dx <= WELLSPRING_SEARCH_RADIUS; dx++)
        {
            for (int dy = -WELLSPRING_SEARCH_RADIUS; dy <= WELLSPRING_SEARCH_RADIUS; dy++)
            {
                for (int dz = -WELLSPRING_SEARCH_RADIUS; dz <= WELLSPRING_SEARCH_RADIUS; dz++)
                {
                    if ((long) dx * dx + (long) dy * dy + (long) dz * dz > radiusSq)
                    {
                        continue;
                    }
                    cursor.setWithOffset(center, dx, dy, dz);
                    if (level.getBlockState(cursor).is(ModBlockTags.WELLSPRING))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid)
    {
        destroy(level, pos, state, player, willHarvest);
        return false;
    }

    /** Shared by this block and {@link HeartCoreTopBlock}, which forwards here with its own position minus one. */
    static void destroy(Level level, BlockPos pos, BlockState state, Player player, boolean willHarvest)
    {
        if (level.isClientSide)
        {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        int mana = blockEntity instanceof HeartCoreBlockEntity heart ? heart.getMana() : HeartstoneItem.MAX_MANA;
        RuneColor color = blockEntity instanceof HeartCoreBlockEntity heart ? heart.centerColor() : RuneColor.BLUE;

        if (willHarvest)
        {
            ItemStack stack = new ItemStack(ModItems.HEARTSTONE.get());
            HeartstoneItem.setMana(stack, mana);
            popResource(level, pos, stack);
        }

        // A shield mid-drain has orbs out in the world with no block left to belong to - clean
        // them up rather than leaving an orphaned, permanent, unbreakable sphere behind.
        if (level instanceof ServerLevel serverLevel)
        {
            HeartCoreBlockEntity.removeShieldOrbs(serverLevel, pos);
        }

        level.setBlockAndUpdate(pos, ModBlocks.MAGIC_CIRCLE.get().defaultBlockState()
                .setValue(MagicCircleBlock.VARIANT, level.random.nextInt(MagicCircleBlock.VARIANT_COUNT))
                .setValue(MagicCircleBlock.COLOR, color));
        level.levelEvent(player, 2001, pos, Block.getId(state));

        BlockPos topPos = pos.above();
        if (level.getBlockState(topPos).is(ModBlocks.HEART_CORE_TOP.get()))
        {
            level.setBlock(topPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
