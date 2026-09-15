package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The creatures that end up on the other side - pets, villagers, anything with a name (see {@code
 * DeathLimboManager#crossesOver}) - and keeping the two sides from reaching each other.
 *
 * <ul>
 *   <li>Nothing picks a fight across the veil, in either direction: a named zombie's ghost does not
 *       go on hunting the living unseen, and the living world's mobs do not chase the dead.</li>
 *   <li>A villager's ghost lets go of the bed and workstation it held, so the living village is not
 *       left short of either by someone it cannot see.</li>
 *   <li>Killing a zombie sometimes frees the villager it used to be, whose ghost is left standing
 *       where the zombie fell - visible, like everything over there, only from the other side.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeilCreatures
{
    /** One zombie in four was somebody. */
    private static final float VILLAGER_RELEASE_CHANCE = 0.25f;
    /** Villager AI keeps trying to claim a bed and a workstation; a ghost's claims are let go this often. */
    private static final int CLAIM_RELEASE_INTERVAL_TICKS = 100;
    private static final List<MemoryModuleType<GlobalPos>> CLAIMS = List.of(
            MemoryModuleType.HOME, MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT);

    private VeilCreatures()
    {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event)
    {
        LivingEntity target = event.getNewTarget();
        if (target != null && Veil.isBehindVeil(event.getEntity()) != Veil.isBehindVeil(target))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != event.getEntity()
                && Veil.isBehindVeil(attacker) != Veil.isBehindVeil(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && entity.tickCount % CLAIM_RELEASE_INTERVAL_TICKS == 0
                && entity instanceof Villager && entity.getPersistentData().getBoolean(LimboRegistry.PET_GHOST_TAG))
        {
            releaseClaims(entity);
        }
    }

    /** Lets go of every bed, workstation and meeting place a villager's ghost is holding. A no-op for anything else. */
    static void releaseClaims(Entity entity)
    {
        if (!(entity instanceof Villager villager) || !(villager.level() instanceof ServerLevel level))
        {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        for (MemoryModuleType<GlobalPos> type : CLAIMS)
        {
            brain.getMemory(type).ifPresent(claim -> {
                ServerLevel claimLevel = level.getServer().getLevel(claim.dimension());
                // Only a POI that still exists can be released - a bed broken since would throw.
                if (claimLevel != null && claimLevel.getPoiManager().getType(claim.pos()).isPresent())
                {
                    try
                    {
                        claimLevel.getPoiManager().release(claim.pos());
                    }
                    catch (IllegalStateException ignored)
                    {
                        // Already free - nothing to give back.
                    }
                }
            });
            brain.eraseMemory(type);
        }
    }

    /**
     * Runs after the death rules have had their say, and never for a death they cancelled - a named
     * zombie crosses over itself rather than freeing anyone.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onZombieDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof Zombie zombie) || zombie instanceof ZombifiedPiglin
                || !(zombie.level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof Player)
                || zombie.getPersistentData().getBoolean(LimboRegistry.PET_GHOST_TAG))
        {
            return;
        }
        if (level.random.nextFloat() >= VILLAGER_RELEASE_CHANCE)
        {
            return;
        }

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null)
        {
            return;
        }
        villager.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), 0.0f);
        if (zombie instanceof ZombieVillager formerly)
        {
            villager.setVillagerData(formerly.getVillagerData());
        }
        else
        {
            villager.setVillagerData(villager.getVillagerData()
                    .setType(VillagerType.byBiome(level.getBiome(zombie.blockPosition()))));
        }
        // Behind the veil before it is ever added, so the living are never sent it at all.
        LimboRegistry.ghostifyPet(villager);
        level.addFreshEntity(villager);

        // The one sign the living get that anyone was freed.
        level.sendParticles(ParticleTypes.SOUL, zombie.getX(), zombie.getY() + 1.0, zombie.getZ(),
                12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, zombie.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 1.0f, 1.2f);
    }
}
