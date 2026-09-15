package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.FairyEntity;
import com.patrickma.magiccircles.entity.FairyQueenEntity;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A fairy's ward: what it throws up the moment it is struck. A sphere the size of the prison
 * cell's (see {@code curse/ImprisonmentCurse}), built of the same shield orbs, around the fairy -
 * and everything else inside is thrown straight out of it, every tick, for {@value #DURATION_TICKS}
 * ticks. Nothing from outside can hurt the fairy while it stands: a blow, an arrow, a wisp or a
 * lightning bolt that comes from beyond it lands on the ward instead, which flashes where it was
 * struck. Striking a fairy's ward still counts as striking the fairy, as far as the Fairy Queen is
 * concerned.
 *
 * <p>Once it comes down, an ordinary fairy flies; the queen stays and keeps fighting (see {@link
 * FairyEntity#onWardLifted}). Wards only ever exist while the server runs - their orbs are removed
 * before it stops, so none is ever left standing in a saved world.
 *
 * <p>A player casting Shield from a commanded Heartstone raises the very same ward around themselves
 * (see {@code CommandedSpellCasting#cast}) - lifted a block first if they are standing on the ground,
 * so the sphere closes round them rather than half into the dirt, and, Blessed as they must be to
 * cast from a stone at all, with their wings opened so they hang still in its heart as a fairy does
 * ({@code network/WardHoverPacket}). They can drop it early by using the stone again ({@link #lower}).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyWard
{
    /** The prison cell's own radius. */
    public static final double RADIUS = 2.5;
    public static final int DURATION_TICKS = 20 * 20;
    /** On each of a ward's orbs: the fairy it belongs to - so that fairy's own shots pass out through it. */
    public static final String ORB_TAG = "MagicCirclesFairyWard";
    private static final int ORB_COUNT = 56;
    /** How far past the surface anything inside is put down, so it doesn't land back on the edge. */
    private static final double PUSH_MARGIN = 0.4;

    private static final Map<UUID, Ward> WARDS = new HashMap<>();

    private FairyWard()
    {
    }

    private static final class Ward
    {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final List<ShieldOrbEntity> orbs;
        int ticksLeft;

        Ward(ServerLevel level, UUID owner, Vec3 center, List<ShieldOrbEntity> orbs)
        {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.orbs = orbs;
            this.ticksLeft = DURATION_TICKS;
        }
    }

    /** Server side only - the client is never told, and never needs to be. */
    public static boolean isWarded(Entity entity)
    {
        return WARDS.containsKey(entity.getUUID());
    }

    public static void raise(ServerLevel level, FairyEntity fairy)
    {
        raise(level, (net.minecraft.world.entity.LivingEntity) fairy);
    }

    /** Raises a ward close round {@code owner} - a player is lifted a block first if they are standing on the ground. */
    public static void raise(ServerLevel level, net.minecraft.world.entity.LivingEntity owner)
    {
        if (owner instanceof Player player)
        {
            if (player.onGround())
            {
                player.teleportTo(player.getX(), player.getY() + 1.0, player.getZ());
            }
            player.setDeltaMovement(Vec3.ZERO);
            if (FairyFlightManager.canGlide(player))
            {
                // On the wing, like the Faye - hovering in the middle of the ward, not dropped onto its floor.
                player.setOnGround(false);
                player.startFallFlying();
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                {
                    com.patrickma.magiccircles.network.ModNetworking.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                            new com.patrickma.magiccircles.network.WardHoverPacket());
                }
            }
        }
        Ward existing = WARDS.remove(owner.getUUID());
        if (existing != null)
        {
            lift(existing, owner);
        }
        Vec3 center = owner.position().add(0.0, owner.getBbHeight() / 2.0, 0.0);
        List<ShieldOrbEntity> orbs = new ArrayList<>();
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < ORB_COUNT; i++)
        {
            double y = 1.0 - (i / (double) (ORB_COUNT - 1)) * 2.0;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            ShieldOrbEntity orb = new ShieldOrbEntity(ModEntities.SHIELD_ORB.get(), level);
            orb.moveTo(center.x + Math.cos(theta) * ring * RADIUS, center.y + y * RADIUS, center.z + Math.sin(theta) * ring * RADIUS,
                    0.0f, 0.0f);
            orb.getPersistentData().putUUID(ORB_TAG, owner.getUUID());
            if (owner instanceof Player)
            {
                // A blow landing on a player's ward is paid for out of their stone - see ShieldOrbEntity#hurt.
                orb.setOwnerPlayerUuid(owner.getUUID());
            }
            level.addFreshEntity(orb);
            orbs.add(orb);
        }
        Ward ward = new Ward(level, owner.getUUID(), center, orbs);
        WARDS.put(owner.getUUID(), ward);
        level.playSound(null, center.x, center.y, center.z, ModSounds.FAIRY_WARD_RAISE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 30, 1.0, 1.0, 1.0, 0.05);
        pushOut(ward);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || WARDS.isEmpty())
        {
            return;
        }
        Iterator<Ward> iterator = WARDS.values().iterator();
        while (iterator.hasNext())
        {
            Ward ward = iterator.next();
            Entity owner = ward.level.getEntity(ward.owner);
            if (owner == null || !owner.isAlive() || --ward.ticksLeft <= 0)
            {
                iterator.remove();
                lift(ward, owner);
                continue;
            }
            pushOut(ward);
        }
    }

    /** Drops {@code owner}'s ward early, if one stands - a player's second use of their Shield stone. */
    public static void lower(UUID owner)
    {
        Ward ward = WARDS.remove(owner);
        if (ward != null)
        {
            lift(ward, ward.level.getEntity(owner));
        }
    }

    /** Offsets to try round the spot straight out from the ward, nearest first - see {@link #safeSpotOutside}. */
    private static final List<Vec3> SEARCH_OFFSETS = searchOffsets();
    private static final int SEARCH_REACH = 3;

    private static List<Vec3> searchOffsets()
    {
        List<Vec3> offsets = new ArrayList<>();
        for (int dx = -SEARCH_REACH; dx <= SEARCH_REACH; dx++)
        {
            for (int dy = -SEARCH_REACH; dy <= SEARCH_REACH; dy++)
            {
                for (int dz = -SEARCH_REACH; dz <= SEARCH_REACH; dz++)
                {
                    offsets.add(new Vec3(dx, dy, dz));
                }
            }
        }
        // Nearest first; between two as near, the one that isn't lower - better set on the ground than in it.
        offsets.sort(java.util.Comparator.comparingDouble(offset -> offset.lengthSqr() + (offset.y < 0 ? 0.25 : 0.0)));
        return offsets;
    }

    /**
     * Where to set {@code entity} down outside the ward: as near as possible to {@code wanted} - the
     * spot straight out from the ward's middle - wherever its whole body fits outside the sphere with
     * no block in it, feet and head alike. Only blocks count; the ward's own orbs are no obstacle to
     * being set down beside them. Null if nowhere near fits.
     */
    @org.jetbrains.annotations.Nullable
    private static Vec3 safeSpotOutside(Ward ward, Entity entity, Vec3 wanted)
    {
        Vec3 feetToBox = entity.position();
        for (Vec3 offset : SEARCH_OFFSETS)
        {
            Vec3 feet = wanted.add(offset);
            AABB box = entity.getBoundingBox().move(feet.subtract(feetToBox));
            if (box.getCenter().distanceTo(ward.center) < RADIUS + 0.1)
            {
                continue;
            }
            if (!ward.level.getBlockCollisions(entity, box).iterator().hasNext())
            {
                return feet;
            }
        }
        return null;
    }

    /**
     * Everything but the ward's owner is set down just outside, wherever it was inside - never into
     * the ground, never with its feet caught in a block (see {@link #safeSpotOutside}).
     */
    private static void pushOut(Ward ward)
    {
        AABB area = new AABB(ward.center, ward.center).inflate(RADIUS + 2.0);
        for (Entity entity : ward.level.getEntities((Entity) null, area,
                e -> !e.getUUID().equals(ward.owner) && !(e instanceof ShieldOrbEntity) && !e.isSpectator()))
        {
            Vec3 middle = entity.getBoundingBox().getCenter();
            Vec3 offset = middle.subtract(ward.center);
            double distance = offset.length();
            if (distance >= RADIUS)
            {
                continue;
            }
            Vec3 direction = distance < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : offset.scale(1.0 / distance);
            Vec3 outside = ward.center.add(direction.scale(RADIUS + PUSH_MARGIN));
            double feetBelowMiddle = entity.getY() - middle.y;
            Vec3 wanted = new Vec3(outside.x, outside.y + feetBelowMiddle, outside.z);
            Vec3 spot = safeSpotOutside(ward, entity, wanted);
            if (spot == null)
            {
                // Hemmed in all round: over the top of the ward, if even that fits - else where it was bound for.
                Vec3 above = new Vec3(ward.center.x, ward.center.y + RADIUS + PUSH_MARGIN, ward.center.z);
                spot = safeSpotOutside(ward, entity, above);
                if (spot == null)
                {
                    spot = wanted;
                }
            }
            entity.teleportTo(spot.x, spot.y, spot.z);
            entity.setDeltaMovement(direction.multiply(0.3, 0.0, 0.3));
            entity.hurtMarked = true;
        }
    }

    private static void lift(Ward ward, Entity owner)
    {
        for (ShieldOrbEntity orb : ward.orbs)
        {
            orb.discard();
        }
        ward.level.playSound(null, ward.center.x, ward.center.y, ward.center.z, ModSounds.FAIRY_WARD_BREAK.get(),
                SoundSource.NEUTRAL, 1.0f, 1.0f);
        if (owner instanceof FairyEntity fairy && fairy.isAlive())
        {
            fairy.onWardLifted();
        }
    }

    /** Nothing from beyond the ward reaches the fairy inside - it lands on the ward instead. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        Ward ward = WARDS.get(event.getEntity().getUUID());
        if (ward == null)
        {
            return;
        }
        DamageSource source = event.getSource();
        Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        Vec3 origin = from != null ? from.getEyePosition() : source.getSourcePosition();
        if (origin == null || origin.distanceTo(ward.center) <= RADIUS)
        {
            return;
        }
        event.setCanceled(true);
        Vec3 point = ward.center.add(origin.subtract(ward.center).normalize().scale(RADIUS));
        ShieldFlashEffects.spawnFlash(ward.level, point);
        if (event.getEntity() instanceof Player shielded)
        {
            // A player's ward is paid for as it is struck: a mana from their stone for every point it turns aside.
            CommandedSpellCasting.drainShieldMana(ward.level, shielded.getUUID(), event.getAmount());
        }
        if (source.getEntity() instanceof Player player && event.getEntity() instanceof FairyEntity fairy)
        {
            FairyQueenEntity.reportAttack(ward.level, fairy, player);
        }
    }

    /** A bolt falling outside the ward doesn't reach in either - lightning carries no attacker to check. */
    @SubscribeEvent
    public static void onStruckByLightning(EntityStruckByLightningEvent event)
    {
        Ward ward = WARDS.get(event.getEntity().getUUID());
        if (ward != null && event.getLightning().position().distanceTo(ward.center) > RADIUS)
        {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player shielded)
            {
                CommandedSpellCasting.drainShieldMana(ward.level, shielded.getUUID(), 5.0f);
            }
        }
    }

    /** Before the world is saved for the last time - no ward's orbs outlive the server that raised it. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        for (Ward ward : WARDS.values())
        {
            for (ShieldOrbEntity orb : ward.orbs)
            {
                orb.discard();
            }
        }
        WARDS.clear();
    }
}
