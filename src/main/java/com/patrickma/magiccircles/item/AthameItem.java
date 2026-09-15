package com.patrickma.magiccircles.item;

import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.curse.CurseRegistry;
import com.patrickma.magiccircles.curse.DarkRites;
import com.patrickma.magiccircles.curse.SummoningRite;
import com.patrickma.magiccircles.limbo.RiteOfPassage;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A double-edged ritual dagger, and the one tool every dark rite needs.
 *
 * <p>An athame remembers blood. Cast a rite with it and it signs itself to you; cut someone else
 * with it and it signs itself to them instead, because what it holds is simply whoever bled on it
 * last. That signature is not decoration - {@code curse/DarkRites} refuses to work without one,
 * and every curse it lays stays bound to the exact blade that laid it (see {@link #bladeId}), so a
 * victim's only hope is that the blade is washed, burned, or lost.
 *
 * <p>Right-click water to rinse it clean, which severs every curse signed with it at once.
 */
public class AthameItem extends Item
{
    private static final String TAG_SIGNED_UUID = "SignedUuid";
    private static final String TAG_SIGNED_NAME = "SignedName";
    private static final String TAG_BLADE_ID = "BladeId";
    /** The curses this blade is holding open - only so its tooltip can name them; see {@link #inventoryTick}. */
    private static final String TAG_BOUND_CURSES = "BoundCurses";
    private static final int BOUND_CHECK_INTERVAL_TICKS = 40;

    public AthameItem(Properties properties)
    {
        super(properties);
    }

    // ------------------------------------------------------------------
    // Signature
    // ------------------------------------------------------------------

    public static boolean isSigned(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_SIGNED_UUID);
    }

    public static UUID signedUuid(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_SIGNED_UUID) ? tag.getUUID(TAG_SIGNED_UUID) : null;
    }

    public static String signedName(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TAG_SIGNED_NAME);
    }

    /**
     * This particular blade's own identity, minted the first time it is signed and kept for as
     * long as it stays bloodied. Curses hold onto this rather than onto whoever signed it, so
     * passing the knife to someone else doesn't hand them the power to free the prisoner - only
     * cleaning or destroying that exact blade does.
     */
    public static UUID bladeId(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_BLADE_ID) ? tag.getUUID(TAG_BLADE_ID) : null;
    }

    /** Marks the blade with {@code victim}'s blood, replacing whatever it held before. */
    public static void sign(ItemStack stack, LivingEntity victim)
    {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_SIGNED_UUID, victim.getUUID());
        tag.putString(TAG_SIGNED_NAME, victim.getName().getString());
        if (!tag.hasUUID(TAG_BLADE_ID))
        {
            tag.putUUID(TAG_BLADE_ID, UUID.randomUUID());
        }
    }

    /** Rinses the blade. Everything it was holding shut comes open - see {@code CurseRegistry#onAthameCleansed}. */
    public static void wash(ItemStack stack, ServerLevel level)
    {
        UUID blade = bladeId(stack);
        CompoundTag tag = stack.getTag();
        if (tag != null)
        {
            tag.remove(TAG_SIGNED_UUID);
            tag.remove(TAG_SIGNED_NAME);
            tag.remove(TAG_BLADE_ID);
            tag.remove(TAG_BOUND_CURSES);
        }
        if (blade != null)
        {
            CurseRegistry.onBladeLost(level, blade);
        }
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /** Cutting someone signs the blade to them - it holds whoever bled on it last, not whoever swung it. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        if (!target.level().isClientSide)
        {
            sign(stack, target);
            target.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT,
                    SoundSource.PLAYERS, 0.6f, 0.7f);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /**
     * Right-clicking water rinses the blade clean. Right-clicking anything else draws your own
     * blood along it, and it signs itself to you.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        boolean atWater = hit.getType() == HitResult.Type.BLOCK && !level.getFluidState(hit.getBlockPos()).isEmpty();

        if (!atWater)
        {
            if (player.getUUID().equals(signedUuid(stack)))
            {
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide)
            {
                sign(stack, player);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.7f);
                player.displayClientMessage(Component.translatable("item.magiccircles.athame.self_signed")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!isSigned(stack))
        {
            return InteractionResultHolder.pass(stack);
        }

        if (level instanceof ServerLevel serverLevel)
        {
            wash(stack, serverLevel);
            serverLevel.playSound(null, hit.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
            player.displayClientMessage(Component.translatable("item.magiccircles.athame.washed"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (!(state.getBlock() instanceof MagicCircleBlock) || player == null)
        {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        return performRite((ServerLevel) level, player, pos, context.getItemInHand());
    }

    /**
     * Works whichever dark rite the ring around {@code center} describes. Shared by a rune at the
     * centre (above) and a Heart Core at the centre ({@code block/HeartCoreBlock}), so every rite
     * works the same whether or not a heart sits in the middle of it - and the summoning, which
     * needs one there, can be cast at all.
     */
    public static InteractionResult performRite(ServerLevel level, Player player, BlockPos center, ItemStack stack)
    {
        Map<RuneColor, Integer> counts = MagicCircleRitual.ringColorCounts(level, center);
        if (counts == null || !counts.containsKey(RuneColor.BLACK))
        {
            return InteractionResult.PASS;
        }

        // A ring of nothing but black is the crossing itself - the oldest rite, and the only one
        // that doesn't ask for a signature first.
        if (counts.size() == 1)
        {
            RiteOfPassage.begin(level, player, center);
            sign(stack, player);
            return InteractionResult.CONSUME;
        }

        // Each shape below is exclusive of the others by its colour count alone: every colour
        // once (summoning), three colours six-three-three (split rings), two colours six-six
        // (curses).
        if (SummoningRite.matches(counts))
        {
            return SummoningRite.cast(level, player, center, stack)
                    ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }

        com.patrickma.magiccircles.curse.TriColourRite splitRing = com.patrickma.magiccircles.curse.TriColourRite.match(counts);
        if (splitRing != null)
        {
            return splitRing.cast(level, player, center, stack)
                    ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }

        return DarkRites.cast(level, player, center, stack, counts)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    /** Whether the ring around {@code center} belongs to any dark rite - lets the client commit to the swing. */
    public static boolean isDarkRing(net.minecraft.world.level.BlockGetter level, BlockPos center)
    {
        Map<RuneColor, Integer> counts = MagicCircleRitual.ringColorCounts(level, center);
        return counts != null && counts.containsKey(RuneColor.BLACK);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag)
    {
        if (isSigned(stack))
        {
            tooltip.add(Component.translatable("item.magiccircles.athame.signed", signedName(stack))
                    .withStyle(ChatFormatting.DARK_RED));
        }
        else
        {
            tooltip.add(Component.translatable("item.magiccircles.athame.clean")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        CompoundTag tag = stack.getTag();
        if (tag == null)
        {
            return;
        }
        net.minecraft.nbt.ListTag bound = tag.getList(TAG_BOUND_CURSES, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < bound.size(); i++)
        {
            CompoundTag entry = bound.getCompound(i);
            com.patrickma.magiccircles.curse.CurseKind kind = curseKind(entry);
            if (kind != null)
            {
                tooltip.add(Component.translatable("item.magiccircles.athame.bound",
                                Component.translatable(kind.translationKey() + ".name"), entry.getString("VictimName"))
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    /** Records a curse this blade now holds open, for its tooltip. The curse itself lives in {@code CurseRegistry}. */
    public static void bindCurse(ItemStack stack, com.patrickma.magiccircles.curse.CurseKind kind, LivingEntity victim)
    {
        CompoundTag tag = stack.getOrCreateTag();
        net.minecraft.nbt.ListTag bound = tag.getList(TAG_BOUND_CURSES, net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putString("Kind", kind.name());
        entry.putUUID("Victim", victim.getUUID());
        entry.putString("VictimName", victim.getName().getString());
        bound.add(entry);
        tag.put(TAG_BOUND_CURSES, bound);
    }

    /**
     * Keeps the tooltip honest. Curses last only as long as the server that cast them is running,
     * and end with the blade, so every couple of seconds a carried blade's list is trimmed to what
     * {@code CurseRegistry} says it still holds.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected)
    {
        CompoundTag tag = stack.getTag();
        if (level.isClientSide || tag == null || !tag.contains(TAG_BOUND_CURSES)
                || level.getGameTime() % BOUND_CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }
        UUID blade = bladeId(stack);
        net.minecraft.nbt.ListTag bound = tag.getList(TAG_BOUND_CURSES, net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag stillHeld = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < bound.size(); i++)
        {
            CompoundTag entry = bound.getCompound(i);
            com.patrickma.magiccircles.curse.CurseKind kind = curseKind(entry);
            if (blade != null && kind != null && entry.hasUUID("Victim") && holds(blade, kind, entry.getUUID("Victim")))
            {
                stillHeld.add(entry);
            }
        }
        if (stillHeld.size() == bound.size())
        {
            return;
        }
        if (stillHeld.isEmpty())
        {
            tag.remove(TAG_BOUND_CURSES);
        }
        else
        {
            tag.put(TAG_BOUND_CURSES, stillHeld);
        }
    }

    private static boolean holds(UUID blade, com.patrickma.magiccircles.curse.CurseKind kind, UUID victim)
    {
        for (com.patrickma.magiccircles.curse.ActiveCurse curse : CurseRegistry.active())
        {
            if (curse.bladeId().equals(blade) && curse.kind() == kind && curse.victim().equals(victim))
            {
                return true;
            }
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private static com.patrickma.magiccircles.curse.CurseKind curseKind(CompoundTag entry)
    {
        try
        {
            return com.patrickma.magiccircles.curse.CurseKind.valueOf(entry.getString("Kind"));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return isSigned(stack);
    }
}
