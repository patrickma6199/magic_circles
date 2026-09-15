package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * What the four non-imprisoning curses actually do, tick by tick.
 *
 * <p>Each is a Wellspring blessing read backwards, and each is meant to be survivable but genuinely
 * miserable - a curse that only inconveniences someone isn't worth half a caster's blood. None of
 * them care whether the victim is a player, so cursing someone's horse is entirely possible and
 * entirely in the spirit of the thing.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseEffects
{
    private static final int STORM_INTERVAL_TICKS = 20 * 12;
    private static final int BLIGHT_INTERVAL_TICKS = 20 * 3;
    private static final int BLIGHT_RADIUS = 3;
    private static final float BLIGHT_DAMAGE = 1.0f;
    private static final int GLEAN_INTERVAL_TICKS = 20 * 8;
    private static final int GLEAN_AMOUNT = 3;

    private CurseEffects()
    {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level))
        {
            return;
        }
        Set<CurseKind> kinds = DarkRites.kindsOn(victim.getUUID());
        if (kinds.isEmpty())
        {
            return;
        }

        if (kinds.contains(CurseKind.STORMCALLED))
        {
            tickStormcalled(level, victim);
        }
        if (kinds.contains(CurseKind.BLIGHT))
        {
            tickBlight(level, victim);
        }
        if (kinds.contains(CurseKind.DENIED_MERCY))
        {
            tickDeniedMercy(level, victim);
        }
        if (kinds.contains(CurseKind.GLEANING))
        {
            tickGleaning(level, victim);
        }
    }

    /**
     * Black and Blue - Zuzo's storm, given a name to follow. The sky finds them wherever it can
     * see them, which makes open ground genuinely dangerous and turns the curse into a slow siege:
     * they can hide from it, but only indoors.
     */
    private static void tickStormcalled(ServerLevel level, LivingEntity victim)
    {
        if (victim.tickCount % STORM_INTERVAL_TICKS != 0 || !level.canSeeSky(victim.blockPosition()))
        {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null)
        {
            return;
        }
        bolt.moveTo(victim.getX(), victim.getY(), victim.getZ());
        level.addFreshEntity(bolt);
    }

    /**
     * Black and Gold - the Verdant Mother's bloom, rotting. Grass and flowers die wherever the
     * cursed walks, so the blight is visible in the world long after they have moved on, and the
     * same rot works steadily inward on them.
     */
    private static void tickBlight(ServerLevel level, LivingEntity victim)
    {
        if (victim.tickCount % BLIGHT_INTERVAL_TICKS != 0)
        {
            return;
        }
        BlockPos center = victim.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-BLIGHT_RADIUS, -1, -BLIGHT_RADIUS),
                center.offset(BLIGHT_RADIUS, 1, BLIGHT_RADIUS)))
        {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FLOWERS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                    || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN))
            {
                level.destroyBlock(pos, false);
            }
            else if (state.is(Blocks.GRASS_BLOCK))
            {
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            }
        }
        victim.hurt(level.damageSources().magic(), BLIGHT_DAMAGE);
        level.sendParticles(ParticleTypes.ASH, victim.getX(), victim.getY() + 0.5, victim.getZ(),
                12, 0.5, 0.6, 0.5, 0.01);
    }

    /**
     * Black and Red - Sylvaine's mercy withheld. Regeneration is stripped as fast as it is applied
     * and healing is blocked outright (see {@link #onHeal}); all that remains is hunger for the
     * wound to close on its own, which it will not.
     */
    private static void tickDeniedMercy(ServerLevel level, LivingEntity victim)
    {
        if (victim.hasEffect(MobEffects.REGENERATION))
        {
            victim.removeEffect(MobEffects.REGENERATION);
        }
        if (victim.tickCount % 40 == 0)
        {
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, false, false));
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    2, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * Black and Green - the Gleaner's fortune, gleaned from the living. Experience bleeds out of
     * the victim and finds whoever cursed them, wherever they are, which makes this the one curse
     * that actively pays its caster rather than merely costing them.
     */
    private static void tickGleaning(ServerLevel level, LivingEntity victim)
    {
        if (victim.tickCount % GLEAN_INTERVAL_TICKS != 0 || !(victim instanceof ServerPlayer player))
        {
            return;
        }
        if (player.totalExperience <= 0 && player.experienceLevel <= 0)
        {
            return;
        }
        player.giveExperiencePoints(-GLEAN_AMOUNT);

        ActiveCurse curse = CurseRegistry.find(victim.getUUID(), CurseKind.GLEANING);
        if (curse != null)
        {
            LivingEntity caster = DarkRites.findLiving(level.getServer(), curse.caster());
            if (caster instanceof ServerPlayer thief)
            {
                thief.giveExperiencePoints(GLEAN_AMOUNT);
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, victim.getX(), victim.getY() + 1.0, victim.getZ(),
                8, 0.4, 0.5, 0.4, 0.4);
    }

    /** Denied Mercy, at the only point that actually matters: nothing heals them. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event)
    {
        if (CurseRegistry.isCursed(event.getEntity().getUUID(), CurseKind.DENIED_MERCY))
        {
            event.setCanceled(true);
        }
    }

    /** The Gleaning takes what they earn as they earn it, not only what they already had. */
    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event)
    {
        Player player = event.getEntity();
        if (!CurseRegistry.isCursed(player.getUUID(), CurseKind.GLEANING)
                || !(player.level() instanceof ServerLevel level))
        {
            return;
        }
        ActiveCurse curse = CurseRegistry.find(player.getUUID(), CurseKind.GLEANING);
        if (curse == null)
        {
            return;
        }
        int stolen = event.getOrb().getValue();
        event.setCanceled(true);
        event.getOrb().discard();
        if (DarkRites.findLiving(level.getServer(), curse.caster()) instanceof ServerPlayer thief)
        {
            thief.giveExperiencePoints(stolen);
        }
    }

    /** A stormcalled victim drowning in their own weather still dies to it properly - no special casing. */
    static boolean isLethal(DamageSource source)
    {
        return source != null;
    }
}
