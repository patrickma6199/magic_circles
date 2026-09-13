package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.FairyRealmShield;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The Fairy Realm's permanent boundary shield ({@link FairyRealmShield}) has no visible orbs at
 * all - a purple wisp lattice traced onto the exact same ellipsoid the boundary actually enforces
 * is the only way to see where it is. This follows the same rule the player-cast Shield spell's
 * own wisps do ({@code ShieldRingWisps}): a shield formed without an outer rune boundary just has
 * its wisps circle the sphere's own radius - the realm boundary was never rune-cast at all, so it
 * always falls into that case.
 *
 * <p>The lattice ({@link #WISP_COUNT} points, the same Fibonacci-sphere spread {@link
 * FairyRealmShield} places its own collision orbs with) keeps rotating every tick regardless of
 * the player's position, so a point's angle is always continuous once it does come into view -
 * only whether a point actually spawns a particle *this* tick is limited to {@link
 * #RENDER_DISTANCE} of the player, purely to avoid wasting particle spawns on the far side of a
 * 250-block-radius sphere nobody could see anyway.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RealmBoundaryWisps
{
    private static final int WISP_COUNT = 260;
    private static final double RENDER_DISTANCE = 130.0;
    private static final double ROTATION_SPEED = 0.0015;
    private static final float PARTICLE_SIZE = 1.1f;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private static double[] baseTheta;
    private static double[] vHeight;

    private RealmBoundaryWisps()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }

        if (baseTheta == null)
        {
            initLattice();
        }

        double radiusH = FairyRealmShield.radiusH();
        double radiusV = FairyRealmShield.radiusV();
        double centerY = FairyRealmShield.centerY();
        double rotation = level.getGameTime() * ROTATION_SPEED;
        Vector3f color = RuneColor.PURPLE.wispColor();

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double renderDistSq = RENDER_DISTANCE * RENDER_DISTANCE;

        for (int i = 0; i < WISP_COUNT; i++)
        {
            double v = vHeight[i];
            double radiusAtV = Math.sqrt(Math.max(0.0, 1.0 - v * v));
            double theta = baseTheta[i] + rotation;
            double x = Math.cos(theta) * radiusAtV * radiusH;
            double z = Math.sin(theta) * radiusAtV * radiusH;
            double y = centerY + v * radiusV;

            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            if (dx * dx + dy * dy + dz * dz > renderDistSq)
            {
                continue;
            }

            level.addParticle(new DustParticleOptions(color, PARTICLE_SIZE), x, y, z, 0.0, 0.0, 0.0);
        }
    }

    private static void initLattice()
    {
        baseTheta = new double[WISP_COUNT];
        vHeight = new double[WISP_COUNT];
        for (int i = 0; i < WISP_COUNT; i++)
        {
            vHeight[i] = 1.0 - (i / (double) (WISP_COUNT - 1)) * 2.0;
            baseTheta[i] = GOLDEN_ANGLE * i;
        }
    }
}
