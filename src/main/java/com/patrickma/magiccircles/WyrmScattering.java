package com.patrickma.magiccircles;

import com.mojang.logging.LogUtils;
import com.patrickma.magiccircles.entity.ManaWyrmEntity;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WellspringOcean;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDrownEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Where the Mana Wyrms live, and how they spread.
 *
 * <p>At first, only in the Wellspring's own sea deep under the island, close beneath the Ancient
 * Heartstone - placed there when the realm is made (see {@code worldgen/WellspringOcean}) - and
 * none are ever born out in the rivers and pools of the islands. The first time a player kills one
 * down in that sea, the rest scatter: from then on they are born naturally in the waters out
 * across the islands too, though only a few ({@link #OUTER_WATERS_CHANCE}) - out there they are too
 * far from the Ancient Heartstone, the prime heart they draw their mana from, to thrive. Never in
 * the well at the foot of the tree, which is the heart's own.
 *
 * <p>The sea itself never runs short of them, scattered or not: while anyone is in the realm it is
 * topped back up to {@value #SEA_POPULATION} wyrms, a few every few seconds ({@link #onServerTick}) -
 * vanilla's own spawning can never reach a closed cavity under the island, so nothing else would.
 *
 * <p>And nothing drowns in the Wellspring: a creature that goes under it and runs out of breath is
 * not hurt, but taken there and then, and a Mana Wyrm swims away in its place ({@link #onDrown}).
 *
 * <p>Whether the wyrms have scattered is saved with the Fairy Realm ({@link Scattered}) and mirrored
 * here in memory, so the spawn rule itself never has to touch saved data.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WyrmScattering
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Of every natural spawn out in the open waters, only this share come to anything. */
    private static final float OUTER_WATERS_CHANCE = 0.3f;
    /** The well at the foot of the tree, and the pool around it, never grow wyrms of their own. */
    private static final double WELL_EXCLUSION_RADIUS = 16.0;

    /** How many wyrms the Wellspring's sea keeps, and how quickly it is topped back up. */
    private static final int SEA_POPULATION = 60;
    private static final int SEA_CHECK_TICKS = 20 * 5;
    private static final int SEA_SPAWNS_PER_CHECK = 4;
    private static final int SEA_SPAWN_ATTEMPTS = 12;

    private static volatile boolean scattered;

    private WyrmScattering()
    {
    }

    /** Saved with the Fairy Realm: whether a wyrm has yet been killed in the deep, and the rest have scattered. */
    public static final class Scattered extends SavedData
    {
        boolean scattered;

        public static Scattered load(CompoundTag tag)
        {
            Scattered data = new Scattered();
            data.scattered = tag.getBoolean("Scattered");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            tag.putBoolean("Scattered", this.scattered);
            return tag;
        }
    }

    private static Scattered data(ServerLevel realm)
    {
        return realm.getDataStorage().computeIfAbsent(Scattered::load, Scattered::new, "magiccircles_wyrm_scattering");
    }

    /** The Mana Wyrm's natural spawn rule (see {@code MagicCircles#commonSetup}). */
    public static boolean canSpawn(EntityType<ManaWyrmEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random)
    {
        if (!level.getFluidState(pos).is(FluidTags.WATER))
        {
            return false;
        }
        if (!level.getLevel().dimension().equals(ModDimensions.FAIRY_REALM))
        {
            return true;
        }
        if (!scattered)
        {
            return false;
        }
        BlockPos well = WorldTree.wellCenter();
        if (Math.hypot(pos.getX() - well.getX(), pos.getZ() - well.getZ()) < WELL_EXCLUSION_RADIUS || WellspringOcean.inSea(pos))
        {
            return false;
        }
        return random.nextFloat() < OUTER_WATERS_CHANCE;
    }

    /** The first wyrm a player kills down in the Wellspring's sea scatters the rest out into the open waters. */
    @SubscribeEvent
    public static void onWyrmDeath(LivingDeathEvent event)
    {
        if (scattered || !(event.getEntity() instanceof ManaWyrmEntity wyrm) || !(wyrm.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.FAIRY_REALM) || !(event.getSource().getEntity() instanceof Player))
        {
            return;
        }
        if (!WellspringOcean.inSea(wyrm.blockPosition()))
        {
            return;
        }
        Scattered data = data(level);
        data.scattered = true;
        data.setDirty();
        scattered = true;
        LOGGER.info("A Mana Wyrm was killed in the Wellspring's sea - the wyrms have scattered into the open waters.");
    }

    /**
     * Nothing drowns in the Wellspring. The moment a creature under its water runs out of breath -
     * before a single point of drowning damage - the water takes it, and it becomes a Mana Wyrm
     * where it was, keeping any name it had. Not the Wellspring's own creatures (fairies, pixies,
     * dream elk, the wyrms themselves), nothing behind the veil, no corpse or Ferryman, no player,
     * and no boss.
     */
    @SubscribeEvent
    public static void onDrown(LivingDrownEvent event)
    {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob creature) || !(creature.level() instanceof ServerLevel level)
                || !creature.isAlive() || creature.getEyeInFluidType() != ModFluidTypes.WELLSPRING_WATER.get() || !takenByTheWater(creature))
        {
            return;
        }
        event.setCanceled(true);
        ManaWyrmEntity wyrm = creature.convertTo(ModEntities.MANA_WYRM.get(), false);
        if (wyrm == null)
        {
            return;
        }
        wyrm.setAirSupply(wyrm.getMaxAirSupply());
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, wyrm.getX(), wyrm.getY() + 0.5, wyrm.getZ(),
                24, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, wyrm.getX(), wyrm.getY() + 0.5, wyrm.getZ(),
                10, 0.3, 0.3, 0.3, 0.02);
        wyrm.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    /** Whether the Wellspring takes this creature - anything but its own, the veil's, and the things too great for it. */
    private static boolean takenByTheWater(net.minecraft.world.entity.Mob creature)
    {
        net.minecraft.world.entity.Entity entity = creature;
        boolean itsOwn = entity instanceof ManaWyrmEntity
                || entity instanceof com.patrickma.magiccircles.entity.FairyEntity
                || entity instanceof com.patrickma.magiccircles.entity.PixieEntity
                || entity instanceof com.patrickma.magiccircles.entity.DreamElkEntity;
        boolean theVeils = entity instanceof com.patrickma.magiccircles.entity.FerrymanEntity
                || entity instanceof com.patrickma.magiccircles.entity.CreatureCorpseEntity
                || entity instanceof com.patrickma.magiccircles.entity.PlayerCorpseEntity
                || entity instanceof com.patrickma.magiccircles.entity.GhostPhantomEntity
                || com.patrickma.magiccircles.limbo.LimboRegistry.isCreatureGhost(entity);
        boolean tooGreat = entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss;
        return !itsOwn && !theVeils && !tooGreat;
    }

    /** Keeps the sea stocked: counts the wyrms in the parts of it that are loaded, and adds a few if it is short. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % SEA_CHECK_TICKS != 0)
        {
            return;
        }
        ServerLevel realm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (realm == null || realm.players().isEmpty())
        {
            return;
        }
        BlockPos well = WorldTree.wellCenter();
        double reach = WellspringOcean.seaReach();
        AABB sea = new AABB(well.getX() - reach, realm.getMinBuildHeight(), well.getZ() - reach,
                well.getX() + reach, WellspringOcean.seaCeilingY() + 1, well.getZ() + reach);
        int living = realm.getEntitiesOfClass(ManaWyrmEntity.class, sea, wyrm -> WellspringOcean.inSea(wyrm.blockPosition())).size();
        int wanted = Math.min(SEA_SPAWNS_PER_CHECK, SEA_POPULATION - living);
        for (int i = 0; i < wanted; i++)
        {
            spawnInSea(realm, well, reach);
        }
    }

    /** One more wyrm, somewhere in a loaded part of the sea - or none, if a few tries find nowhere. */
    private static void spawnInSea(ServerLevel realm, BlockPos well, double reach)
    {
        RandomSource random = realm.random;
        for (int attempt = 0; attempt < SEA_SPAWN_ATTEMPTS; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * reach;
            int x = well.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = well.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!realm.hasChunkAt(new BlockPos(x, 0, z)))
            {
                continue;
            }
            BlockPos spot = wellspringWaterIn(realm, x, z);
            if (spot == null)
            {
                continue;
            }
            ManaWyrmEntity wyrm = ModEntities.MANA_WYRM.get().create(realm);
            if (wyrm == null)
            {
                return;
            }
            wyrm.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
            wyrm.finalizeSpawn(realm, realm.getCurrentDifficultyAt(spot), MobSpawnType.NATURAL, null, null);
            realm.addFreshEntity(wyrm);
            return;
        }
    }

    /** A random spot of Wellspring water in this column of the sea, with more of it overhead - or null if the sea doesn't reach here. */
    private static BlockPos wellspringWaterIn(ServerLevel realm, int x, int z)
    {
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = WellspringOcean.seaCeilingY(); y > realm.getMinBuildHeight(); y--)
        {
            cursor.set(x, y, z);
            if (isWellspring(realm, cursor) && isWellspring(realm, cursor.above()))
            {
                spots.add(cursor.immutable());
            }
        }
        return spots.isEmpty() ? null : spots.get(realm.random.nextInt(spots.size()));
    }

    private static boolean isWellspring(ServerLevel realm, BlockPos pos)
    {
        return realm.getFluidState(pos).getFluidType() == ModFluidTypes.WELLSPRING_WATER.get();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel realm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        scattered = realm != null && data(realm).scattered;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        scattered = false;
    }
}
