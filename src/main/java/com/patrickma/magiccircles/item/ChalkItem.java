package com.patrickma.magiccircles.item;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModBlocks;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A {@link BlockItem} that draws its block ({@code magic_circle}) instead of placing a
 * "real" block-item. Unlike a normal block item, drawing a circle costs 1 point of the
 * chalk's own durability rather than consuming an item off the stack - a full stick of
 * chalk (see {@code Properties.durability(...)} in {@link com.patrickma.magiccircles.registry.ModItems})
 * lets you draw that many circles before it wears down to nothing.
 */
public class ChalkItem extends BlockItem
{
    public ChalkItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    /** Overridden by each colored chalk subclass - the only difference between them. */
    protected RuneColor getColor()
    {
        return RuneColor.BLUE;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context)
    {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(MagicCircleBlock.COLOR, getColor());
    }

    /**
     * {@link BlockItem#getDescriptionId()} defers to the placed *block's* name ("Magic
     * Circle") rather than this item's own registry name, since normally a block item and its
     * block share one name - here they deliberately don't, so this restores the plain
     * {@link net.minecraft.world.item.Item} behavior (name based on this item's own id) that
     * {@code BlockItem} overrides away, which is what actually makes the "Chalk"/"Gold
     * Chalk"/etc. lang entries show up instead of "Magic Circle".
     */
    @Override
    public String getDescriptionId()
    {
        return getOrCreateDescriptionId();
    }

    /**
     * Right-clicking an existing rune with a *different*-colored chalk recolors it in place
     * instantly, rather than falling through to the normal placement pipeline - which, since a
     * rune isn't a replaceable block, would otherwise just try (and fail, or place somewhere
     * unrelated) to draw a brand new rune next to this one. Right-clicking with the *same* color
     * is treated as a no-op ({@link InteractionResult#PASS}) rather than doing anything at all -
     * there's nothing useful "instantly replace this rune with an identical one" would do.
     */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState existing = level.getBlockState(pos);
        if (!existing.is(ModBlocks.MAGIC_CIRCLE.get()))
        {
            return super.useOn(context);
        }

        RuneColor newColor = getColor();
        if (existing.getValue(MagicCircleBlock.COLOR) == newColor)
        {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide)
        {
            level.setBlock(pos, existing.setValue(MagicCircleBlock.COLOR, newColor), 3);
            ItemStack stack = context.getItemInHand();
            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().instabuild)
            {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
            }
            SoundType soundType = existing.getSoundType();
            level.playSound(player, pos, this.getPlaceSound(existing), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Reimplements {@link BlockItem#place(BlockPlaceContext)} rather than calling it, because the
     * vanilla version always shrinks the stack by one on a successful placement - here we want to
     * damage it by one point instead, and leave the stack itself alone.
     */
    @Override
    public InteractionResult place(BlockPlaceContext context)
    {
        if (!context.canPlace())
        {
            return InteractionResult.FAIL;
        }

        BlockPlaceContext placeContext = this.updatePlacementContext(context);
        if (placeContext == null)
        {
            return InteractionResult.FAIL;
        }

        BlockState placementState = this.getPlacementState(placeContext);
        if (placementState == null || !this.placeBlock(placeContext, placementState))
        {
            return InteractionResult.FAIL;
        }

        BlockPos pos = placeContext.getClickedPos();
        Level level = placeContext.getLevel();
        Player player = placeContext.getPlayer();
        ItemStack stack = placeContext.getItemInHand();

        BlockState placedState = level.getBlockState(pos);
        if (placedState.is(placementState.getBlock()))
        {
            placedState.getBlock().setPlacedBy(level, pos, placedState, player, stack);
            if (player instanceof ServerPlayer serverPlayer)
            {
                CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
            }
        }

        SoundType soundType = placedState.getSoundType();
        level.playSound(player, pos, this.getPlaceSound(placedState), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);

        if (player != null && !player.getAbilities().instabuild)
        {
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(placeContext.getHand()));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
