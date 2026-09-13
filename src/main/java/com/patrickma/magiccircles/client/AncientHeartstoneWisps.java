package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * A close, tight purple wisp orbit around the Ancient Heartstone (see {@code
 * entity/AncientHeartstoneEntity}) - the same "dormant" dock orbit {@code ClientHeartWisps}' own
 * white ring keeps around any idle, spell-less Heart Core, just purple (the shield/ward color
 * this mod uses everywhere else) instead of white, and permanent rather than conditional - this
 * heart never casts anything, so it's always dormant.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AncientHeartstoneWisps
{
    private static final Vector3f PURPLE = RuneColor.PURPLE.wispColor();
    private static final int WISP_COUNT = 6;
    // Matches ClientHeartWisps' own idle DOCK_RADIUS - the same tight, close-in orbit an idle
    // Heart Core's white wisps keep.
    private static final double DOCK_RADIUS = 0.35;
    private static final double SPEED = 0.02;
    private static final float PARTICLE_SIZE = 0.85f;

    private AncientHeartstoneWisps()
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
        if (level == null || level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }

        BlockPos wellCenter = WorldTree.wellCenter();
        double cx = wellCenter.getX() + 0.5;
        double cy = wellCenter.getY() + WorldTree.ANCIENT_HEARTSTONE_HEIGHT;
        double cz = wellCenter.getZ() + 0.5;
        double time = level.getGameTime();

        for (int i = 0; i < WISP_COUNT; i++)
        {
            double phase = i * (Math.PI * 2.0 / WISP_COUNT);
            double angle = time * SPEED + phase;
            double x = cx + DOCK_RADIUS * Math.cos(angle);
            double z = cz + DOCK_RADIUS * Math.sin(angle);
            double y = cy + 0.15 * Math.sin(time * 0.03 + phase * 1.5);
            level.addParticle(new DustParticleOptions(PURPLE, PARTICLE_SIZE), x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
