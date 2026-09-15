package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.FairyEntity;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.limbo.LimboRegistry;
import com.patrickma.magiccircles.limbo.LimboState;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.network.OpenFairyDialoguePacket;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModItems;
import com.patrickma.magiccircles.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What fairies will deal in, and the dealing. They trade for redstone and nothing else: their realm
 * has none, and every portal they open to reach other realms - as we opened ours to theirs - drinks
 * it. Each fairy has exactly one thing to sell (see {@code FairyEntity#tradeId}), so finding what
 * you want means asking around: purple chalk, which is theirs alone to make, is common; cooked
 * mana wyrm less so; arcane dust, a live wyrm, a full heartstone and the Book of the Faye itself
 * are rare - the dearer ones priced to make you think twice. The Art of Blood is the queen's alone to sell, for
 * a fortune. Redstone blocks count as nine dust, and they give change.
 *
 * <p>The list is shared by both sides: the client draws it (see {@code client/FairyDialogueScreen}),
 * the server settles it - checking again that the fairy is there, close enough, and still willing,
 * and that the redstone really is in your pockets.
 */
public final class FairyTrades
{
    public static final double TALK_RANGE = 8.0;

    /** What is offered, what it costs in redstone, and how often a fairy turns out to be selling it. */
    public record Trade(Supplier<ItemStack> result, int cost, int weight)
    {
    }

    /** Everything the Faye sell. The last is the queen's alone, and no ordinary fairy ever has it. */
    public static final List<Trade> ALL = List.of(
            new Trade(() -> new ItemStack(ModItems.PURPLE_CHALK.get()), 32, 3),
            new Trade(() -> new ItemStack(ModItems.ARCANE_DUST.get(), 4), 12, 1),
            new Trade(() -> new ItemStack(ModItems.COOKED_MANA_WYRM.get()), 128, 2),
            new Trade(() -> new ItemStack(ModItems.RAW_MANA_WYRM.get()), 96, 1),
            new Trade(FairyTrades::fullHeartstone, 320, 1),
            new Trade(() -> new ItemStack(ModItems.BOOK_OF_THE_FAYE.get()), 64, 1),
            new Trade(() -> new ItemStack(ModItems.ART_OF_BLOOD.get()), 640, 0));
    /** The Art of Blood - sold only by the queen, who alone of the Faye has walked the dark side of the veil. */
    public static final int QUEEN_TRADE = ALL.size() - 1;

    /** Which one thing a new fairy sells - the common ones often, the dear ones rarely. */
    public static int randomFairyTrade(net.minecraft.util.RandomSource random)
    {
        int total = 0;
        for (Trade trade : ALL)
        {
            total += trade.weight();
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < ALL.size(); i++)
        {
            roll -= ALL.get(i).weight();
            if (roll < 0)
            {
                return i;
            }
        }
        return 0;
    }

    private FairyTrades()
    {
    }

    private static ItemStack fullHeartstone()
    {
        ItemStack stone = new ItemStack(ModItems.HEARTSTONE.get());
        HeartstoneItem.setMana(stone, HeartstoneItem.MAX_MANA);
        return stone;
    }

    /** Right-clicking a fairy: the server says what the conversation will be, and the client opens it. */
    public static void openDialogue(ServerPlayer player, FairyEntity fairy)
    {
        boolean bargain = fairy.isQueen() && LimboState.isGhost(player);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenFairyDialoguePacket(fairy.getId(), fairy.isQueen(), bargain, fairy.tradeId()));
        fairy.playSound(ModSounds.FAIRY_GREET.get(), 1.0f, fairy.getVoicePitch());
    }

    /** All the redstone someone is carrying, counting a block as nine dust. */
    public static int redstoneHeld(Player player)
    {
        int total = 0;
        for (ItemStack stack : pockets(player))
        {
            if (stack.is(Items.REDSTONE))
            {
                total += stack.getCount();
            }
            else if (stack.is(Items.REDSTONE_BLOCK))
            {
                total += 9 * stack.getCount();
            }
        }
        return total;
    }

    private static List<ItemStack> pockets(Player player)
    {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    /** Takes loose dust first, then breaks into blocks, handing back whatever a block was worth beyond the price. */
    private static boolean pay(Player player, int cost)
    {
        if (redstoneHeld(player) < cost)
        {
            return false;
        }
        int owed = cost;
        for (ItemStack stack : pockets(player))
        {
            if (owed > 0 && stack.is(Items.REDSTONE))
            {
                int take = Math.min(owed, stack.getCount());
                stack.shrink(take);
                owed -= take;
            }
        }
        for (ItemStack stack : pockets(player))
        {
            if (owed > 0 && stack.is(Items.REDSTONE_BLOCK))
            {
                int blocks = Math.min(stack.getCount(), (owed + 8) / 9);
                stack.shrink(blocks);
                owed -= blocks * 9;
            }
        }
        if (owed < 0)
        {
            give(player, new ItemStack(Items.REDSTONE, -owed));
        }
        return true;
    }

    private static void give(Player player, ItemStack stack)
    {
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
    }

    @Nullable
    private static FairyEntity reachable(ServerPlayer player, int entityId)
    {
        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof FairyEntity fairy && fairy.isAlive() && player.distanceToSqr(fairy) <= TALK_RANGE * TALK_RANGE)
        {
            return fairy;
        }
        return null;
    }

    public static void trade(ServerPlayer player, int entityId, int index)
    {
        FairyEntity fairy = reachable(player, entityId);
        if (fairy == null || index < 0 || index >= ALL.size() || index != fairy.tradeId() || LimboState.isInLimbo(player))
        {
            return;
        }
        if (!fairy.willTradeWith(player))
        {
            player.displayClientMessage(Component.translatable("fairy.magiccircles.trade.refused").withStyle(ChatFormatting.RED), true);
            return;
        }
        Trade trade = ALL.get(index);
        if (!pay(player, trade.cost()))
        {
            player.displayClientMessage(Component.translatable("fairy.magiccircles.trade.poor", trade.cost())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        give(player, trade.result().get());
        fairy.playSound(ModSounds.FAIRY_TRADE.get(), 1.0f, fairy.getVoicePitch());
    }

    /**
     * The Fairy Queen's bargain, for the dead: she sends you back to your body - everything in it
     * returned, exactly as the Ferryman would - and from then on you are Indebted to her.
     */
    public static void acceptBargain(ServerPlayer player, int entityId)
    {
        FairyEntity queen = reachable(player, entityId);
        if (queen == null || !queen.isQueen() || !LimboState.isGhost(player))
        {
            return;
        }
        queen.level().playSound(null, queen.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.5f, 1.4f);
        if (!LimboRegistry.revive(player))
        {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.INDEBTED.get(), Integer.MAX_VALUE, 0, false, false, true));
        player.sendSystemMessage(Component.translatable("fairy.magiccircles.queen.bargain_done")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }
}
