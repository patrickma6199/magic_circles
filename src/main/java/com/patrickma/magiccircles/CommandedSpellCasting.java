package com.patrickma.magiccircles;

import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.ritual.HeartSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The world-effect side of casting a spell straight from a commanded Heartstone (see {@link
 * com.patrickma.magiccircles.item.HeartstoneItem#use}) instead of a Heart Core's own ring. Every
 * effect here is a deliberately compact, player-centered re-derivation of the matching {@code
 * HeartCoreBlockEntity} spell - not a refactor of that class (its own effect methods are all
 * tightly bound to a fixed block {@code worldPosition}), and not tuned to be byte-identical to
 * the heart-cast version, just to read as the same spell.
 *
 * <p>Every prolonged spell (see {@link HeartSpell#isProlonged()}) runs for a fixed {@link
 * #DURATION_TICKS} (20 seconds) regardless of how long it runs when cast from a real Heart Core -
 * tracked here by a small self-ticking list ({@link #activeCasts}), not by anything stored on the
 * player or the item, so it survives the item being dropped/swapped mid-cast without leaking
 * state anywhere. One-shot spells (Flowers, Heal, XP, Cleansing Rain, Bloom of Life, Verdant
 * Harvest) just apply once, immediately, exactly like the ring-cast version does.
 *
 * <p>{@link HeartSpell#MANA_FONT} is never expected to reach {@link #cast} at all - {@code
 * HeartCoreBlock#interact}'s absorb branch refuses to ever let a player command it in the first
 * place (see that method's own doc comment) - but {@link #cast} still no-ops defensively if it
 * somehow does, rather than throwing.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandedSpellCasting
{
    public static final int DURATION_TICKS = 20 * 20;

    private static final double EFFECT_RADIUS = 10.0;
    private static final double SHIELD_RADIUS = 7.0;
    private static final int STORM_STRIKE_INTERVAL = 20 * 4;
    private static final int TEMPEST_STRIKE_INTERVAL = 20 * 3;
    private static final int BUFF_REAPPLY_INTERVAL = 20 * 4;

    private static final List<ActiveCast> activeCasts = new ArrayList<>();

    private CommandedSpellCasting()
    {
    }

    /** Client-only echo of {@link #cast} - see {@code CommandedHeartstoneWisps}, which this hands off to entirely; no world effect happens here. */
    public static void startClientVisual(Player player, HeartSpell spell)
    {
        com.patrickma.magiccircles.client.CommandedHeartstoneWisps.start(player, spell);
    }

    public static void cast(ServerLevel level, LivingEntity player, HeartSpell spell)
    {
        if (spell == HeartSpell.MANA_FONT)
        {
            return;
        }

        Vec3 center = player.position();
        RandomSource random = level.random;

        if (spell == HeartSpell.SHIELD && player instanceof Player)
        {
            // A player's Shield is the Faye's own ward, closed tight about them - see FairyWard. It
            // lasts as long as any lasting spell, and using the stone again drops it (see #endCast).
            FairyWard.raise(level, player);
            activeCasts.add(new ActiveCast(level, player.getUUID(), spell, player.position(), DURATION_TICKS, false));
            return;
        }

        if (spell.isProlonged())
        {
            // Shield is indefinite now - it runs until the stone's own mana actually runs out
            // (see #drainShieldMana), the same real cost the placed-Heart-Core version pays,
            // rather than a fixed timer every other prolonged spell still uses. ticksRemaining
            // is irrelevant for it (see #onServerTick's own indefinite check) but still needs a
            // positive value so it doesn't look already-expired before the first tick runs.
            // Only a player's - a fairy (entity/FairyEntity) flies off, and a shield it left
            // behind would otherwise stand where it cast it until something happened to hit it.
            boolean indefinite = spell == HeartSpell.SHIELD && player instanceof Player;
            ActiveCast active = new ActiveCast(level, player.getUUID(), spell, center, DURATION_TICKS, indefinite);
            if (spell == HeartSpell.SHIELD)
            {
                active.shieldOrbs = spawnShieldOrbs(level, center, player.getUUID());
            }
            activeCasts.add(active);
            return;
        }

        applyOneShot(level, player, spell, center, random);
    }

    /** Ends this caster's own still-running cast of {@code spell}, if there is one - a second right-click on the stone. */
    public static boolean cancelActive(UUID casterId, HeartSpell spell)
    {
        Iterator<ActiveCast> iterator = activeCasts.iterator();
        while (iterator.hasNext())
        {
            ActiveCast active = iterator.next();
            if (active.spell == spell && active.playerId.equals(casterId))
            {
                endCast(active);
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private static List<ShieldOrbEntity> spawnShieldOrbs(ServerLevel level, Vec3 center, UUID ownerPlayerUuid)
    {
        List<ShieldOrbEntity> orbs = new ArrayList<>();
        int count = 60;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < count; i++)
        {
            double v = 1.0 - (i / (double) (count - 1)) * 2.0;
            double radiusAtV = Math.sqrt(Math.max(0.0, 1.0 - v * v));
            double theta = goldenAngle * i;
            double x = center.x + Math.cos(theta) * radiusAtV * SHIELD_RADIUS;
            double z = center.z + Math.sin(theta) * radiusAtV * SHIELD_RADIUS;
            double y = center.y + 1.0 + v * SHIELD_RADIUS;
            ShieldOrbEntity orb = new ShieldOrbEntity(ModEntities.SHIELD_ORB.get(), level);
            orb.setPos(x, y, z);
            orb.setOwnerPlayerUuid(ownerPlayerUuid);
            level.addFreshEntity(orb);
            orbs.add(orb);
        }
        return orbs;
    }

    /**
     * The commanded-Shield equivalent of {@code HeartCoreBlockEntity#drainShieldMana} - 1 mana
     * per hit point, straight out of whichever commanded Heartstone this player is still carrying
     * with {@code HeartSpell#SHIELD} set (see {@link #findCommandedShieldStone}), ending the
     * shield the instant that runs out. If the stone itself is gone entirely (dropped, moved to
     * another player, whatever), the shield simply ends outright rather than draining nothing
     * forever - there's no mana left to charge it against.
     */
    public static void drainShieldMana(ServerLevel level, UUID playerUuid, float damageAmount)
    {
        if (damageAmount <= 0.0f)
        {
            return;
        }
        LivingEntity player = level.getEntity(playerUuid) instanceof LivingEntity caster ? caster : null;
        ItemStack stone = player == null ? ItemStack.EMPTY : findCommandedShieldStone(player);
        if (player == null || stone.isEmpty())
        {
            endActiveShield(playerUuid);
            return;
        }
        int cost = Math.max(1, Math.round(damageAmount));
        int mana = HeartstoneItem.getMana(stone);
        if (mana <= cost)
        {
            HeartstoneItem.setMana(stone, 0);
            endActiveShield(playerUuid);
        }
        else
        {
            HeartstoneItem.setMana(stone, mana - cost);
        }
    }

    private static ItemStack findCommandedShieldStone(LivingEntity caster)
    {
        if (caster instanceof Player player)
        {
            for (ItemStack stack : player.getInventory().items)
            {
                if (stack.getItem() instanceof HeartstoneItem && HeartstoneItem.getCommandedSpell(stack) == HeartSpell.SHIELD)
                {
                    return stack;
                }
            }
        }
        // Anyone else carries theirs in hand - a fairy, say.
        for (ItemStack held : List.of(caster.getMainHandItem(), caster.getOffhandItem()))
        {
            if (held.getItem() instanceof HeartstoneItem && HeartstoneItem.getCommandedSpell(held) == HeartSpell.SHIELD)
            {
                return held;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void endActiveShield(UUID playerUuid)
    {
        Iterator<ActiveCast> iterator = activeCasts.iterator();
        while (iterator.hasNext())
        {
            ActiveCast active = iterator.next();
            if (active.spell == HeartSpell.SHIELD && active.playerId.equals(playerUuid))
            {
                endCast(active);
                iterator.remove();
            }
        }
    }

    private static void applyOneShot(ServerLevel level, LivingEntity player, HeartSpell spell, Vec3 center, RandomSource random)
    {
        switch (spell)
        {
            case FLOWERS -> castFlowers(level, center, random);
            case HEAL -> castHeal(level, center);
            case XP -> castXp(level, center, random);
            case CLEANSING_RAIN -> castCleansingRain(level, center);
            case BLOOM_OF_LIFE -> castBloomOfLife(level, player, center, random);
            case VERDANT_HARVEST -> castVerdantHarvest(level, center);
            default ->
            {
                // STORM/SHIELD/TEMPEST_WARD/VITAL_SURGE are all prolonged and handled in #cast;
                // MANA_FONT is refused before this is ever reached.
            }
        }
    }

    private static void castFlowers(ServerLevel level, Vec3 center, RandomSource random)
    {
        BlockState[] flowers = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
                Blocks.BLUE_ORCHID.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
        int cx = (int) Math.floor(center.x);
        int cz = (int) Math.floor(center.z);
        int cy = (int) Math.floor(center.y);
        for (int i = 0; i < 40; i++)
        {
            int x = cx + random.nextInt(11) - 5;
            int z = cz + random.nextInt(11) - 5;
            for (int y = cy - 2; y <= cy + 2; y++)
            {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK) && level.getBlockState(pos.above()).isAir())
                {
                    level.setBlock(pos.above(), flowers[random.nextInt(flowers.length)], 2);
                    break;
                }
            }
        }
    }

    private static void castHeal(ServerLevel level, Vec3 center)
    {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(EFFECT_RADIUS)))
        {
            entity.heal(8.0f);
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        }
    }

    private static void castXp(ServerLevel level, Vec3 center, RandomSource random)
    {
        for (int i = 0; i < 6; i++)
        {
            ExperienceOrb orb = new ExperienceOrb(level, center.x, center.y + 0.5, center.z, 5 + random.nextInt(6));
            level.addFreshEntity(orb);
        }
    }

    private static void castCleansingRain(ServerLevel level, Vec3 center)
    {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(EFFECT_RADIUS)))
        {
            entity.clearFire();
            for (net.minecraft.world.effect.MobEffect harmful : List.copyOf(entity.getActiveEffectsMap().keySet()))
            {
                if (harmful.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                {
                    entity.removeEffect(harmful);
                }
            }
        }
    }

    private static void castBloomOfLife(ServerLevel level, LivingEntity player, Vec3 center, RandomSource random)
    {
        net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos((int) center.x, (int) center.y, (int) center.z);
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6)))
        {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bonemealable && bonemealable.isValidBonemealTarget(level, pos, state, level.isClientSide))
            {
                bonemealable.performBonemeal(level, random, pos, state);
            }
        }
    }

    private static void castVerdantHarvest(ServerLevel level, Vec3 center)
    {
        net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos((int) center.x, (int) center.y, (int) center.z);
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6)))
        {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state))
            {
                level.setBlock(pos, crop.getStateForAge(crop.getMaxAge()), 2);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || activeCasts.isEmpty())
        {
            return;
        }
        Iterator<ActiveCast> iterator = activeCasts.iterator();
        while (iterator.hasNext())
        {
            ActiveCast active = iterator.next();
            tickCast(active);
            if (active.indefinite)
            {
                // Shield only ever ends via #endActiveShield (mana depleted) - never on a timer.
                continue;
            }
            active.ticksRemaining--;
            if (active.ticksRemaining <= 0)
            {
                endCast(active);
                iterator.remove();
            }
        }
    }

    private static void tickCast(ActiveCast active)
    {
        Entity player = active.level.getEntity(active.playerId);
        switch (active.spell)
        {
            case STORM ->
            {
                active.level.setWeatherParameters(0, 20, true, true);
                if (--active.nextStrikeIn <= 0)
                {
                    active.nextStrikeIn = STORM_STRIKE_INTERVAL;
                    strikeNear(active.level, active.center, 14.0);
                }
            }
            case TEMPEST_WARD ->
            {
                if (--active.nextStrikeIn <= 0)
                {
                    active.nextStrikeIn = TEMPEST_STRIKE_INTERVAL;
                    strikeHostile(active.level, active.center, 16.0);
                }
            }
            case VITAL_SURGE ->
            {
                if (player != null && --active.nextStrikeIn <= 0)
                {
                    active.nextStrikeIn = BUFF_REAPPLY_INTERVAL;
                    for (LivingEntity entity : active.level.getEntitiesOfClass(LivingEntity.class, new AABB(active.center, active.center).inflate(EFFECT_RADIUS)))
                    {
                        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_REAPPLY_INTERVAL + 20, 0));
                        entity.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, BUFF_REAPPLY_INTERVAL + 20, 0));
                    }
                }
            }
            case SHIELD -> tickShieldContainment(active);
            default ->
            {
            }
        }
    }

    private static void tickShieldContainment(ActiveCast active)
    {
        if (active.shieldOrbs == null)
        {
            return;
        }
        for (Entity entity : active.level.getEntitiesOfClass(Entity.class, new AABB(active.center, active.center).inflate(SHIELD_RADIUS + 4.0)))
        {
            if (entity.getUUID().equals(active.playerId) || entity instanceof ShieldOrbEntity)
            {
                continue;
            }
            Vec3 offset = entity.position().subtract(active.center);
            double dist = offset.length();
            if (dist < SHIELD_RADIUS && dist > 1.0E-4)
            {
                Vec3 pushed = active.center.add(offset.scale(SHIELD_RADIUS / dist));
                entity.teleportTo(pushed.x, pushed.y, pushed.z);
            }
        }
    }

    private static void strikeNear(ServerLevel level, Vec3 center, double radius)
    {
        RandomSource random = level.random;
        double x = center.x + (random.nextDouble() * 2.0 - 1.0) * radius;
        double z = center.z + (random.nextDouble() * 2.0 - 1.0) * radius;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null)
        {
            bolt.moveTo(x, y, z);
            level.addFreshEntity(bolt);
        }
    }

    private static void strikeHostile(ServerLevel level, Vec3 center, double radius)
    {
        Mob nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius)))
        {
            if (!(mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER))
            {
                continue;
            }
            double dist = mob.position().distanceToSqr(center);
            if (dist < nearestDist)
            {
                nearestDist = dist;
                nearest = mob;
            }
        }
        if (nearest != null)
        {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null)
            {
                bolt.moveTo(nearest.getX(), nearest.getY(), nearest.getZ());
                level.addFreshEntity(bolt);
            }
        }
        else
        {
            strikeNear(level, center, radius);
        }
    }

    private static void endCast(ActiveCast active)
    {
        if (active.shieldOrbs != null)
        {
            for (ShieldOrbEntity orb : active.shieldOrbs)
            {
                orb.discard();
            }
        }
        else if (active.spell == HeartSpell.SHIELD)
        {
            FairyWard.lower(active.playerId);
        }
    }

    private static final class ActiveCast
    {
        final ServerLevel level;
        final UUID playerId;
        final HeartSpell spell;
        final Vec3 center;
        final boolean indefinite;
        int ticksRemaining;
        int nextStrikeIn;
        List<ShieldOrbEntity> shieldOrbs;

        ActiveCast(ServerLevel level, UUID playerId, HeartSpell spell, Vec3 center, int ticksRemaining, boolean indefinite)
        {
            this.level = level;
            this.playerId = playerId;
            this.spell = spell;
            this.center = center;
            this.ticksRemaining = ticksRemaining;
            this.indefinite = indefinite;
            this.nextStrikeIn = 0;
        }
    }
}
