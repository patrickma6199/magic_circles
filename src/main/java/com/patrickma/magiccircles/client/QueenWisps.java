package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.entity.FairyQueenEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The Fairy Queen's wisps: one of every colour, the dark rite's among them - she is the only being
 * that has found balance between the Wellspring and the darkness, and they circle her together. Not
 * in one ring like anyone else's, but each on its own tilted plane and in its own direction, the way
 * electrons are drawn around a nucleus (see {@link TiltedOrbits}).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenWisps
{
    private static final double RADIUS = 1.5;
    /** Chest height on her - she is drawn larger than a fairy. */
    private static final double CENTER_HEIGHT = 1.3;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int TRAIL_STEPS = 3;

    private static final TiltedOrbits ORBITS = new TiltedOrbits(RuneColor.values(), RADIUS, PARTICLE_SIZE, TRAIL_STEPS);
    /** Where each queen's wisps were last tick, so their trails join up. */
    private static final Map<Integer, Vec3[]> LAST = new HashMap<>();

    private QueenWisps()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
        {
            return;
        }
        double time = level.getGameTime();
        for (Entity entity : level.entitiesForRendering())
        {
            if (!(entity instanceof FairyQueenEntity queen) || queen.isInvisible())
            {
                continue;
            }
            Vec3 center = queen.position().add(0.0, CENTER_HEIGHT, 0.0);
            Vec3[] last = LAST.computeIfAbsent(queen.getId(), id -> new Vec3[ORBITS.count()]);
            ORBITS.tick(level, time, center, last);
        }
        if (LAST.size() > 64)
        {
            LAST.clear();
        }
    }
}
