package com.patrickma.magiccircles.item;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.limbo.RiteOfPassage;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * A double-edged ritual dagger - right-click the center rune of a complete Black-chalk ring
 * (see {@link RuneColor#BLACK}) with this to perform {@link RiteOfPassage}. Does nothing
 * anywhere else - this isn't a weapon, just the key the rite needs.
 */
public class AthameItem extends Item
{
    public AthameItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (!(state.getBlock() instanceof MagicCircleBlock) || state.getValue(MagicCircleBlock.COLOR) != RuneColor.BLACK)
        {
            return InteractionResult.PASS;
        }

        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        if (player == null)
        {
            return InteractionResult.PASS;
        }

        Map<RuneColor, Integer> counts = MagicCircleRitual.ringColorCounts(level, pos);
        if (counts == null || counts.size() != 1 || !counts.containsKey(RuneColor.BLACK))
        {
            return InteractionResult.PASS;
        }

        RiteOfPassage.begin((ServerLevel) level, player, pos);
        return InteractionResult.CONSUME;
    }
}
