package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Random;

/**
 * The Ancient Heartstone's wisps (see {@code entity/AncientHeartstoneEntity}): two of every colour,
 * the dark rite's among them, circling it the way the queen's circle her - each on its own tilted
 * plane and in its own direction (see {@link TiltedOrbits}). It is the prime heart, the first of
 * them all, and it carries every line twice over.
 *
 * <p>It is also where a charging heart's mana comes from. While a Heart Core near the Wellspring is
 * running Mana Font (found by {@link WellspringWisps#findActiveManaFontHeart}), its wisps leave
 * their orbits one at a time and fly down into that heart on a gentle arc; as each is absorbed, a
 * new wisp of the same colour takes its place on the orbit it left, to be drawn in its own turn.
 * There are always twelve - the ones still circling and the ones on their way down, together.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AncientHeartstoneWisps
{
    private static final double RADIUS = 1.3;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int TRAIL_STEPS = 3;
    /** How much of the way to the charging heart a departing wisp covers each tick. */
    private static final double ABSORB_SPEED = 0.04;
    /** How high the path to the charging heart bows up at its middle. */
    private static final double ARC_HEIGHT = 1.2;
    /** Ticks between one wisp leaving its orbit and the next. */
    private static final int DEPART_MIN = 10;
    private static final int DEPART_RANDOM = 20;
    private static final int HEART_SEARCH_INTERVAL = 10;

    private static final RuneColor[] COLORS = twiceOver(RuneColor.values());
    private static final TiltedOrbits ORBITS = new TiltedOrbits(COLORS, RADIUS, PARTICLE_SIZE, TRAIL_STEPS);
    private static final int COUNT = ORBITS.count();

    /** Where each wisp was last tick on its orbit, so the trails join up. */
    private static final Vec3[] LAST = new Vec3[COUNT];
    /** For a wisp on its way to a charging heart: how far along (0 to 1), or -1 while it is still circling. */
    private static final double[] PROGRESS = new double[COUNT];
    private static final Vec3[] FROM = new Vec3[COUNT];
    private static final Vec3[] TARGET = new Vec3[COUNT];
    private static final Vec3[] TRAVEL_LAST = new Vec3[COUNT];
    private static final boolean[] AWAY = new boolean[COUNT];
    private static final Random RANDOM = new Random();

    private static ClientLevel lastLevel;
    private static BlockPos chargingHeart;
    private static int searchIn;
    private static int departIn;

    static
    {
        Arrays.fill(PROGRESS, -1.0);
    }

    private AncientHeartstoneWisps()
    {
    }

    /** Every colour, then every colour again - the second set lands on planes of its own. */
    private static RuneColor[] twiceOver(RuneColor[] colors)
    {
        RuneColor[] doubled = new RuneColor[colors.length * 2];
        for (int i = 0; i < doubled.length; i++)
        {
            doubled[i] = colors[i % colors.length];
        }
        return doubled;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel)
        {
            Arrays.fill(LAST, null);
            Arrays.fill(PROGRESS, -1.0);
            Arrays.fill(AWAY, false);
            chargingHeart = null;
            lastLevel = level;
        }
        if (level == null || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }

        BlockPos wellCenter = WorldTree.wellCenter();
        Vec3 center = new Vec3(wellCenter.getX() + 0.5, wellCenter.getY() + WorldTree.ANCIENT_HEARTSTONE_HEIGHT, wellCenter.getZ() + 0.5);
        double time = level.getGameTime();

        if (--searchIn <= 0)
        {
            chargingHeart = WellspringWisps.findActiveManaFontHeart(level);
            searchIn = HEART_SEARCH_INTERVAL;
        }
        if (chargingHeart != null && --departIn <= 0)
        {
            departIn = DEPART_MIN + RANDOM.nextInt(DEPART_RANDOM);
            depart(time, center);
        }

        for (int i = 0; i < COUNT; i++)
        {
            if (PROGRESS[i] >= 0.0)
            {
                travel(level, i);
            }
        }
        ORBITS.tick(level, time, center, LAST, AWAY);
    }

    /** One circling wisp, chosen at random, leaves its orbit for the charging heart. */
    private static void depart(double time, Vec3 center)
    {
        int circling = 0;
        for (int i = 0; i < COUNT; i++)
        {
            if (!AWAY[i])
            {
                circling++;
            }
        }
        if (circling == 0)
        {
            return;
        }
        int pick = RANDOM.nextInt(circling);
        for (int i = 0; i < COUNT; i++)
        {
            if (AWAY[i])
            {
                continue;
            }
            if (pick-- == 0)
            {
                FROM[i] = ORBITS.point(i, time, center);
                TARGET[i] = Vec3.atCenterOf(chargingHeart);
                TRAVEL_LAST[i] = FROM[i];
                PROGRESS[i] = 0.0;
                AWAY[i] = true;
                return;
            }
        }
    }

    /** A wisp on its way down: along a gentle arc to the heart - and, once there, a new one in its place on the orbit. */
    private static void travel(ClientLevel level, int i)
    {
        PROGRESS[i] = Math.min(1.0, PROGRESS[i] + ABSORB_SPEED);
        double t = PROGRESS[i];
        Vec3 point = FROM[i].lerp(TARGET[i], t).add(0.0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0.0);
        DustParticleOptions dust = new DustParticleOptions(COLORS[i].wispColor(), PARTICLE_SIZE);
        for (int step = 1; step <= TRAIL_STEPS; step++)
        {
            Vec3 at = TRAVEL_LAST[i].lerp(point, (double) step / TRAIL_STEPS);
            level.addParticle(dust, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
        TRAVEL_LAST[i] = point;
        if (t >= 1.0)
        {
            // Drunk in by the heart - and the prime heart sends out another of the same colour.
            level.addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z, 0.0, 0.03, 0.0);
            PROGRESS[i] = -1.0;
            AWAY[i] = false;
        }
    }
}
