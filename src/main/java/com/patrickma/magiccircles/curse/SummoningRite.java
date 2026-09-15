package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import com.patrickma.magiccircles.entity.FerrymanEntity;
import com.patrickma.magiccircles.item.AthameItem;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The greatest of the dark rites: dragging the Ferryman bodily out of the veil and into the
 * living world.
 *
 * <p>One rune of every colour the Wellspring answers to, and black for all the rest - the whole of
 * the Faye's magic held in a ring and then drowned in it. At its heart a Heartstone full to the
 * brim, which the rite does not spend so much as consume: a thousand mana and the stone itself,
 * gone.
 *
 * <p>What it buys is a door that opens the wrong way, once. The Ferryman stands where the stone
 * was, plainly visible to anyone, and whoever touches him takes the crossing themselves - dying on
 * the spot - while every soul still behind the veil is pulled out into the light. Then he goes.
 *
 * <p>While he stands, his summoner is not haunted (see {@code limbo/MarkedByTheDarkManager}): he is
 * already their Ferryman, and there is only ever one. That is remembered on the summoner themselves
 * rather than by searching for him, because a Ferryman standing in an unloaded chunk still exists
 * and a search would miss him.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SummoningRite
{
    /** Every colour but Black, one rune each - the whole Wellspring, outnumbered. */
    private static final int REQUIRED_COLOUR_RUNES = 1;
    private static final int MANA_COST = 1000;
    /** On the summoner's persisted data: the Ferryman they brought through, while he still stands. */
    private static final String SUMMONED_TAG = "MagicCirclesSummonedFerryman";

    /** Summoners whose Ferryman went while they were offline - cleared the next time they log in. */
    private static final Set<UUID> pendingClear = new HashSet<>();

    private SummoningRite()
    {
    }

    /** Whether this ring is the summoning pattern: exactly one of each non-black colour, black filling the rest. */
    public static boolean matches(Map<RuneColor, Integer> counts)
    {
        if (!counts.containsKey(RuneColor.BLACK))
        {
            return false;
        }
        for (RuneColor color : RuneColor.values())
        {
            if (color == RuneColor.BLACK)
            {
                continue;
            }
            if (counts.getOrDefault(color, 0) != REQUIRED_COLOUR_RUNES)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Performs the summoning if the centre really holds a Heartstone with the full thousand in it.
     * Returns false without touching anything otherwise - this is far too expensive to half-cast.
     */
    public static boolean cast(ServerLevel level, Player caster, BlockPos center, ItemStack athame)
    {
        BlockEntity blockEntity = level.getBlockEntity(center);
        if (!(blockEntity instanceof HeartCoreBlockEntity heart))
        {
            caster.displayClientMessage(Component.translatable("rite.magiccircles.summoning.needs_heart")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (heart.getMana() < MANA_COST)
        {
            caster.displayClientMessage(Component.translatable("rite.magiccircles.summoning.needs_mana", MANA_COST)
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        // The stone is not drained, it is destroyed - the mana only matters as proof it was full.
        heart.setMana(0);
        // Both halves of the heart go - the floating top first, so nothing is left hanging in the
        // air over the place the stone used to be.
        if (level.getBlockState(center.above()).is(com.patrickma.magiccircles.registry.ModBlocks.HEART_CORE_TOP.get()))
        {
            level.setBlockAndUpdate(center.above(), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());

        AthameItem.sign(athame, caster);

        FerrymanEntity ferryman = new FerrymanEntity(ModEntities.FERRYMAN.get(), level);
        ferryman.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, caster.getYRot(), 0.0f);
        ferryman.setVeiled(false);
        // He answers anyone who touches him (see LimboRegistry#emptyTheVeil, which is checked before
        // any caster match), but he is still the summoner's Ferryman - which is what keeps the
        // glimpses away from them while he stands.
        ferryman.setCasterUuid(caster.getUUID());
        level.addFreshEntity(ferryman);
        persisted(caster).putUUID(SUMMONED_TAG, ferryman.getUUID());

        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                80, 0.8, 1.2, 0.8, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5,
                40, 0.6, 0.8, 0.6, 0.02);
        level.playSound(null, center, SoundEvents.WARDEN_EMERGE, SoundSource.BLOCKS, 1.0f, 0.7f);

        caster.displayClientMessage(Component.translatable("rite.magiccircles.summoning.done")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        return true;
    }

    /** Whether a Ferryman this player summoned is still standing somewhere. */
    public static boolean hasLiveSummon(Player player)
    {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).hasUUID(SUMMONED_TAG);
    }

    private static CompoundTag persisted(Player player)
    {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG))
        {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /**
     * A summoned Ferryman going for good - vanished after his one trade, or killed - releases his
     * summoner. Only real destruction counts: one merely leaving because his chunk unloaded is
     * still standing there, and still theirs.
     */
    @SubscribeEvent
    public static void onFerrymanGone(EntityLeaveLevelEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof FerrymanEntity ferryman)
                || !ferryman.isSummoned() || ferryman.getCasterUuid() == null)
        {
            return;
        }
        Entity.RemovalReason reason = ferryman.getRemovalReason();
        if (reason == null || !reason.shouldDestroy())
        {
            return;
        }
        release(level.getServer(), ferryman.getCasterUuid(), ferryman.getUUID());
    }

    private static void release(MinecraftServer server, UUID summoner, UUID ferrymanId)
    {
        ServerPlayer player = server.getPlayerList().getPlayer(summoner);
        if (player == null)
        {
            pendingClear.add(summoner);
            return;
        }
        CompoundTag tag = persisted(player);
        // Only if it's still this one - a newer summoning would have replaced the record.
        if (tag.hasUUID(SUMMONED_TAG) && tag.getUUID(SUMMONED_TAG).equals(ferrymanId))
        {
            tag.remove(SUMMONED_TAG);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (pendingClear.remove(event.getEntity().getUUID()))
        {
            persisted(event.getEntity()).remove(SUMMONED_TAG);
        }
    }
}
