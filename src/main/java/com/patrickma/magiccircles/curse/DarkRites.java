package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.entity.GhostPhantomEntity;
import com.patrickma.magiccircles.item.AthameItem;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Casting the curses - a ring six-and-six between Black and one other colour, cut with a blade
 * that already holds someone's blood.
 *
 * <p>The signature is the whole mechanism. A curse does not pick a target in the world; it follows
 * the blood on the knife, which means the work of cursing someone is really the work of cutting
 * them first. That also makes every curse reversible by the same act that could undo any of them -
 * washing the blade - and makes the knife itself a thing worth stealing.
 *
 * <p>The price is always the same and always paid by the caster: half their remaining life, the
 * Mark, and - one time in four, so it is never quite expected - a phantom that will hunt them for
 * it. That phantom is deliberately not a veiled one: everyone can see what follows a curser around,
 * which is rather the point.
 */
public final class DarkRites
{
    private static final int RUNES_PER_HALF = 6;
    private static final float HEALTH_COST_FRACTION = 0.5f;

    private DarkRites()
    {
    }

    /**
     * Attempts the curse this ring describes. Returns false (and does nothing at all) if the ring
     * isn't a valid half-and-half, the blade is clean, or the blood on it belongs to something no
     * longer in the world.
     */
    public static boolean cast(ServerLevel level, Player caster, BlockPos center, ItemStack athame,
                               Map<RuneColor, Integer> counts)
    {
        CurseKind kind = kindFor(counts);
        if (kind == null)
        {
            return false;
        }
        if (!AthameItem.isSigned(athame))
        {
            caster.displayClientMessage(Component.translatable("curse.magiccircles.needs_blood")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        UUID victimId = AthameItem.signedUuid(athame);
        UUID bladeId = AthameItem.bladeId(athame);
        LivingEntity victim = findLiving(level.getServer(), victimId);
        if (victim == null || bladeId == null)
        {
            caster.displayClientMessage(Component.translatable("curse.magiccircles.no_victim")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        if (CurseRegistry.isCursed(victimId, kind))
        {
            caster.displayClientMessage(Component.translatable("curse.magiccircles.already")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        payThePrice(level, caster, center);

        ActiveCurse curse = new ActiveCurse(kind, victimId, caster.getUUID(), bladeId,
                level.dimension(), center);
        CurseRegistry.register(curse);
        AthameItem.bindCurse(athame, kind, victim);

        if (kind == CurseKind.IMPRISONMENT)
        {
            // The ring becomes the cell: the victim is dragged to it from wherever they stood,
            // which is what makes this worth doing to someone who is nowhere near you.
            Vec3 cell = Vec3.atCenterOf(center).add(0.0, 1.0, 0.0);
            victim.teleportTo(cell.x, cell.y, cell.z);
            ImprisonmentCurse.buildPrison(level, victim, curse);
        }

        victim.sendSystemMessage(Component.translatable(kind.translationKey() + ".afflicted")
                .withStyle(ChatFormatting.DARK_PURPLE));
        caster.displayClientMessage(Component.translatable(kind.translationKey() + ".cast",
                victim.getName()).withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }

    /** Six Black and six of exactly one other colour, in any arrangement - position never matters. */
    static CurseKind kindFor(Map<RuneColor, Integer> counts)
    {
        if (counts.size() != 2 || !counts.containsKey(RuneColor.BLACK))
        {
            return null;
        }
        if (counts.get(RuneColor.BLACK) != RUNES_PER_HALF)
        {
            return null;
        }
        for (Map.Entry<RuneColor, Integer> entry : counts.entrySet())
        {
            if (entry.getKey() != RuneColor.BLACK && entry.getValue() == RUNES_PER_HALF)
            {
                return CurseKind.forPartner(entry.getKey());
            }
        }
        return null;
    }

    /** "A 25% chance thing, just to add a layer of surprise." */
    private static final float WITNESS_CHANCE = 0.25f;

    /**
     * Half the caster's remaining life, the Mark, and - one time in four - a hunter. Taken before
     * the rite lands, so one that kills you outright still costs you; the Art of Blood has never
     * cared whether the one paying survives it. Shared by every dark rite that asks a price, curses
     * and split rings alike.
     */
    static void payThePrice(ServerLevel level, Player caster, BlockPos center)
    {
        caster.setHealth(Math.max(1.0f, caster.getHealth() * HEALTH_COST_FRACTION));
        caster.addEffect(new MobEffectInstance(ModEffects.MARKED_BY_THE_DARK.get(), Integer.MAX_VALUE, 0, false, false));
        if (level.random.nextFloat() < WITNESS_CHANCE)
        {
            summonWitness(level, caster);
        }

        level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.6f, 1.6f);
        level.sendParticles(ParticleTypes.SOUL, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                50, 0.6, 0.8, 0.6, 0.03);
    }

    /**
     * A phantom that everyone can see. Unlike the ones hunting behind the veil this one is fully
     * present in the living world - it just doesn't burn, so daylight buys the curser nothing.
     */
    private static void summonWitness(ServerLevel level, Player caster)
    {
        GhostPhantomEntity phantom = ModEntities.GHOST_PHANTOM.get().create(level);
        if (phantom == null)
        {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double radius = 14.0 + level.random.nextDouble() * 8.0;
        phantom.moveTo(caster.getX() + Math.cos(angle) * radius,
                caster.getY() + 12.0 + level.random.nextDouble() * 6.0,
                caster.getZ() + Math.sin(angle) * radius,
                level.random.nextFloat() * 360.0f, 0.0f);
        phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(phantom.blockPosition()), MobSpawnType.EVENT, null, null);
        phantom.setVeiled(false);
        phantom.setVictim(caster);
        level.addFreshEntity(phantom);
    }

    /** Hunts one entity down by id across every loaded level - the blood doesn't care which world they're in. */
    public static LivingEntity findLiving(MinecraftServer server, UUID id)
    {
        if (id == null)
        {
            return null;
        }
        for (ServerLevel level : server.getAllLevels())
        {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living)
            {
                return living;
            }
        }
        return null;
    }

    /** Every curse currently on this entity - used by the per-tick effects. */
    public static Set<CurseKind> kindsOn(UUID victim)
    {
        Set<CurseKind> kinds = java.util.EnumSet.noneOf(CurseKind.class);
        for (ActiveCurse curse : CurseRegistry.active())
        {
            if (curse.victim().equals(victim))
            {
                kinds.add(curse.kind());
            }
        }
        return kinds;
    }
}
