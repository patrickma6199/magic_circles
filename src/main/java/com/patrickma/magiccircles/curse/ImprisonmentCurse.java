package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Black and Purple: Korrin's unbreakable ward, turned inward.
 *
 * <p>The same wall the Faye raise to keep harm out, built as a cage instead - a sphere of ward
 * orbs closed around whoever was standing in the ring. The prisoner cannot leave it, cannot break
 * the circle holding it, and cannot dig out through the floor. Nothing they do touches it: the
 * only thing that opens this is the blade that closed it being washed or destroyed, which is
 * entirely in someone else's hands.
 *
 * <p>The orbs themselves are ordinary {@code ShieldOrbEntity}s with no owning Heart Core, so no
 * mana drains and nothing can chip them down - striking them simply accomplishes nothing.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImprisonmentCurse
{
    private static final double PRISON_RADIUS = 3.5;
    /**
     * How high above the prisoner's feet the sphere is centred - raised well above the middle of
     * the body, so the cage stands tall over the circle rather than lying half under its floor.
     */
    private static final double CENTER_HEIGHT = 1.75;
    /** Enough orbs for the same wall density the smaller cage had (56 at a radius of 2.5) - scaled with the surface area. */
    private static final int ORB_COUNT = 110;
    /** How far past the wall the prisoner is allowed to drift before being pulled back. */
    private static final double LEASH_SLACK = 0.35;

    private ImprisonmentCurse()
    {
    }

    /** Closes the cage around {@code victim}, centred where they stand. */
    public static void buildPrison(ServerLevel level, LivingEntity victim, ActiveCurse curse)
    {
        Vec3 center = victim.position().add(0.0, CENTER_HEIGHT, 0.0);
        for (Vec3 point : sphere(center, PRISON_RADIUS))
        {
            ShieldOrbEntity orb = new ShieldOrbEntity(ModEntities.SHIELD_ORB.get(), level);
            orb.moveTo(point.x, point.y, point.z, 0.0f, 0.0f);
            // No owning heart and no owning player: nothing to drain, so nothing to wear down.
            orb.getPersistentData().putUUID(PRISON_TAG, curse.bladeId());
            level.addFreshEntity(orb);
        }
        level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 60, 1.2, 1.2, 1.2, 0.03);
    }

    static final String PRISON_TAG = "MagicCirclesPrisonBlade";

    /** Opens the cage - every orb belonging to this blade simply stops being. */
    public static void tearDownPrison(ActiveCurse curse, MinecraftServer server)
    {
        ServerLevel level = curse.level(server);
        if (level == null)
        {
            return;
        }
        AABB search = new AABB(curse.anchor()).inflate(PRISON_RADIUS + 24.0);
        for (ShieldOrbEntity orb : level.getEntitiesOfClass(ShieldOrbEntity.class, search))
        {
            if (curse.bladeId().equals(orb.getPersistentData().hasUUID(PRISON_TAG)
                    ? orb.getPersistentData().getUUID(PRISON_TAG) : null))
            {
                level.sendParticles(ParticleTypes.SMOKE, orb.getX(), orb.getY(), orb.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
                orb.discard();
            }
        }
    }

    /** An even scatter of points over a sphere - the same golden-angle spiral the Heart Core's own ward uses. */
    private static List<Vec3> sphere(Vec3 center, double radius)
    {
        List<Vec3> points = new java.util.ArrayList<>();
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < ORB_COUNT; i++)
        {
            double y = 1.0 - (i / (double) (ORB_COUNT - 1)) * 2.0;
            double ringRadius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            points.add(center.add(Math.cos(theta) * ringRadius * radius, y * radius, Math.sin(theta) * ringRadius * radius));
        }
        return points;
    }

    // ------------------------------------------------------------------
    // Enforcement
    // ------------------------------------------------------------------

    /** Pulls anyone still cursed back inside their cage. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide || living.tickCount % 4 != 0)
        {
            return;
        }
        ActiveCurse curse = CurseRegistry.find(living.getUUID(), CurseKind.IMPRISONMENT);
        if (curse == null || !living.level().dimension().equals(curse.dimension()))
        {
            return;
        }

        Vec3 center = Vec3.atBottomCenterOf(curse.anchor()).add(0.0, CENTER_HEIGHT, 0.0);
        Vec3 offset = living.position().subtract(center);
        double distance = offset.length();
        if (distance > PRISON_RADIUS - LEASH_SLACK && distance > 1.0E-4)
        {
            Vec3 pulled = center.add(offset.scale((PRISON_RADIUS - LEASH_SLACK) / distance));
            living.teleportTo(pulled.x, pulled.y, pulled.z);
            living.setDeltaMovement(Vec3.ZERO);
            living.hasImpulse = true;
        }
    }

    /** The prisoner cannot dig their way out - not the ring holding them, not the floor beneath them. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        Player player = event.getPlayer();
        if (player == null || !CurseRegistry.isCursed(player.getUUID(), CurseKind.IMPRISONMENT))
        {
            return;
        }
        event.setCanceled(true);
    }

    /** Nor place their way out. */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getEntity() instanceof Player player
                && CurseRegistry.isCursed(player.getUUID(), CurseKind.IMPRISONMENT))
        {
            event.setCanceled(true);
        }
    }

    /** Keeps the cage standing even if the chunk reloads and the orbs come back without their tag intact. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 0)
        {
            return;
        }
        for (ActiveCurse curse : CurseRegistry.active())
        {
            if (curse.kind() != CurseKind.IMPRISONMENT)
            {
                continue;
            }
            ServerLevel level = curse.level(event.getServer());
            LivingEntity victim = curse.findVictim(event.getServer());
            if (level == null || victim == null)
            {
                continue;
            }
            AABB search = new AABB(curse.anchor()).inflate(PRISON_RADIUS + 4.0);
            long standing = level.getEntitiesOfClass(ShieldOrbEntity.class, search).stream()
                    .filter(orb -> orb.getPersistentData().hasUUID(PRISON_TAG))
                    .count();
            if (standing < ORB_COUNT / 2L)
            {
                buildPrison(level, victim, curse);
            }
        }
    }

    /** Curses reach through the veil as readily as anywhere else - a prisoner is a prisoner. */
    static boolean canBeCursed(Entity entity)
    {
        return entity instanceof LivingEntity;
    }
}
