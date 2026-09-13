package com.patrickma.magiccircles.item;

import com.patrickma.magiccircles.CommandedSpellCasting;
import com.patrickma.magiccircles.block.MagicCircleBlock;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import com.patrickma.magiccircles.client.HeartstoneItemRenderer;
import com.patrickma.magiccircles.registry.ModBlockTags;
import com.patrickma.magiccircles.registry.ModBlocks;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.ritual.HeartSpell;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import com.patrickma.magiccircles.ritual.PortalRitual;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Stores mana rather than durability, but reuses the vanilla durability-bar rendering to
 * display it (see the {@code isBarVisible}/{@code getBarWidth}/{@code getBarColor}
 * overrides below) - the item is never actually damaged or destroyed by vanilla's damage
 * system. Right-clicking it on a magic circle's *center* rune - which requires that rune to
 * be the center of either the rounded 12-rune ring ({@link MagicCircleRitual}) or the portal's
 * 5x5 border ({@link PortalRitual}) - replaces that rune with a {@link ModBlocks#HEART_CORE}
 * block carrying the Heartstone's mana. How you first obtain a Heartstone is left for later -
 * for now it's available from the creative inventory.
 */
public class HeartstoneItem extends Item
{
    public static final int MAX_MANA = 1000;

    public HeartstoneItem(Properties properties)
    {
        super(properties);
    }

    /**
     * Gives this item the same orb+aura model {@link com.patrickma.magiccircles.block.HeartCoreBlock}
     * renders with, in hand/on the ground/in item frames, instead of a flat 2D icon - this is
     * the standard Forge hook for that (see {@code Item#initClient}, which only ever calls this
     * on the client, so the client-only {@link HeartstoneItemRenderer} reference below is safe
     * even though this class itself is loaded on the server too).
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer)
    {
        consumer.accept(new IClientItemExtensions()
        {
            private HeartstoneItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer()
            {
                if (renderer == null)
                {
                    renderer = new HeartstoneItemRenderer(
                            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                            Minecraft.getInstance().getEntityModels());
                }
                return renderer;
            }
        });
    }

    public static int getMana(ItemStack stack)
    {
        return stack.getOrCreateTag().contains("Mana") ? stack.getTag().getInt("Mana") : MAX_MANA;
    }

    public static void setMana(ItemStack stack, int mana)
    {
        stack.getOrCreateTag().putInt("Mana", Mth.clamp(mana, 0, MAX_MANA));
    }

    /**
     * The spell this stone is "commanded" to cast from hand, or {@code null} for a plain, blank
     * Heartstone. Set by absorbing a working Heart Core's ring (see {@code HeartCoreBlock#interact}'s
     * empty-hand branch, gated on {@link ModEffects#BLESSED_BY_WELLSPRING}); cleared only by the
     * Cleansing Ritual (see {@link #performCleansingRitual}). A commanded stone can still be
     * "charged" - spent on {@link #useOn} to power a brand new Heart Core, same as any blank
     * stone - since that action only ever cares about the stone's raw mana, never this tag (a
     * Heart Core's own spell always comes from its ring's color composition, not from anything
     * carried over from whatever stone happened to fund it). Casting the commanded spell itself
     * and the Cleansing Ritual are the only two actions this tag *does* gate.
     */
    @Nullable
    public static HeartSpell getCommandedSpell(ItemStack stack)
    {
        if (!stack.hasTag() || !stack.getTag().contains("CommandedSpell"))
        {
            return null;
        }
        try
        {
            return HeartSpell.valueOf(stack.getTag().getString("CommandedSpell"));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    public static void setCommandedSpell(ItemStack stack, HeartSpell spell)
    {
        stack.getOrCreateTag().putString("CommandedSpell", spell.name());
    }

    public static void clearCommandedSpell(ItemStack stack)
    {
        if (stack.hasTag())
        {
            stack.getTag().remove("CommandedSpell");
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        return getMana(stack) < MAX_MANA;
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        return Math.round(13.0f * getMana(stack) / MAX_MANA);
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        float fraction = getMana(stack) / (float) MAX_MANA;
        return Mth.hsvToRgb(0.58f, 0.85f, 0.35f + 0.55f * fraction);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.magiccircles.heartstone.tooltip", getMana(stack), MAX_MANA)
                .withStyle(ChatFormatting.DARK_AQUA));
        HeartSpell commanded = getCommandedSpell(stack);
        if (commanded != null)
        {
            tooltip.add(Component.translatable("item.magiccircles.heartstone.commanded_spell",
                    Component.translatable("spell.magiccircles." + commanded.name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // The Cleansing Ritual: sneak-right-click a commanded Heartstone against the Wellspring
        // itself, washing the old commission back out of the stone - the only way to clear
        // getCommandedSpell so a fresh ring can be captured into the same stone. See the book
        // (BookOfTheFaye) for the in-world lore text on this.
        if (player != null && player.isShiftKeyDown() && level.getBlockState(center).is(ModBlockTags.WELLSPRING))
        {
            return performCleansingRitual(level, player, stack);
        }

        BlockState centerState = level.getBlockState(center);
        if (!centerState.is(ModBlocks.MAGIC_CIRCLE.get()))
        {
            return InteractionResult.PASS;
        }

        // A rune can be the center of either ritual shape - the rounded 12-ring (most spells)
        // or the portal's 5x5 border (see PortalRitual) - a Heart Core works the same either
        // way, it just costs mana to a different pattern once it's there (see HeartCoreBlock).
        // matchesBorder also counts, not just matches, since the portal's water pit might
        // already be dug (holding water, not air) before the Heartstone's even placed here.
        if (!MagicCircleRitual.hasCompleteRing(level, center) && !PortalRitual.matches(level, center) && !PortalRitual.matchesBorder(level, center))
        {
            if (!level.isClientSide && player != null)
            {
                player.displayClientMessage(
                        Component.translatable("item.magiccircles.heartstone.incomplete_ring").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        int mana = getMana(stack);

        RuneColor color = centerState.getValue(MagicCircleBlock.COLOR);

        level.setBlockAndUpdate(center, ModBlocks.HEART_CORE.get().defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(center);
        if (blockEntity instanceof HeartCoreBlockEntity heart)
        {
            heart.setMana(mana);
            heart.setCenterColor(color);
        }

        BlockPos topPos = center.above();
        if (level.getBlockState(topPos).isAir())
        {
            level.setBlockAndUpdate(topPos, ModBlocks.HEART_CORE_TOP.get().defaultBlockState());
        }

        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.2f);

        if (player == null || !player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    /** See the Cleansing Ritual's own call site in {@link #useOn} above. */
    private static InteractionResult performCleansingRitual(Level level, Player player, ItemStack stack)
    {
        if (getCommandedSpell(stack) == null)
        {
            // A blank stone has nothing to cleanse - not an error, just nothing to do.
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        clearCommandedSpell(stack);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        if (level instanceof ServerLevel serverLevel)
        {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
        }
        player.displayClientMessage(Component.translatable("item.magiccircles.heartstone.cleansed").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    /**
     * Right-clicking with a commanded Heartstone in hand (not aimed at anything {@link #useOn}
     * already handles - a ring rune or the Wellspring) casts whatever spell it's set to, exactly
     * like {@code FairyHornItem}/{@code HeartCoreBlock#interact} does for a real ring, but
     * centered on the player rather than any block, fixed at {@link CommandedSpellCasting#DURATION_TICKS}
     * (20 seconds) regardless of that spell's own normal duration, and only usable at all while
     * {@link ModEffects#BLESSED_BY_WELLSPRING} is active - the moment that buff runs out, this
     * stops working, mid-cast or not.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        HeartSpell spell = getCommandedSpell(stack);
        if (spell == null)
        {
            return InteractionResultHolder.pass(stack);
        }

        if (!player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get()))
        {
            if (!level.isClientSide)
            {
                player.displayClientMessage(Component.translatable("item.magiccircles.heartstone.needs_blessing").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide)
        {
            CommandedSpellCasting.startClientVisual(player, spell);
            return InteractionResultHolder.success(stack);
        }

        int mana = getMana(stack);
        if (mana < spell.manaCost())
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.not_enough_mana").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        setMana(stack, mana - spell.manaCost());
        CommandedSpellCasting.cast((ServerLevel) level, player, spell);
        return InteractionResultHolder.success(stack);
    }
}
