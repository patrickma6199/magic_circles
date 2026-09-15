package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The wisps that circle a player, the way the Fairy Queen's circle her (see {@link TiltedOrbits}):
 * each on its own tilted plane and in its own direction, like electrons about a nucleus.
 *
 * <ul>
 *   <li>Blessed by the Wellspring (see {@code registry/ModEffects#BLESSED_BY_WELLSPRING}): one wisp
 *       of each of the Wellspring's five colours - blue, gold, purple, red and green.</li>
 *   <li>Marked by the Dark ({@code ModEffects#MARKED_BY_THE_DARK}): the dark rite's own wisp, the one
 *       the queen carries among hers, on a slightly wider path of its own - with or without the
 *       Blessing's five.</li>
 * </ul>
 *
 * <p>Runs for every such player in the client's render distance, not just the local player.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlessedWispOrbit
{
    private static final double RADIUS = 1.4;
    /** The dark wisp swings a little wider, so it never rides on the same path as the blue one. */
    private static final double DARK_RADIUS = 1.8;
    /** About the chest - the middle of the body, so the orbits wrap the whole of it. */
    private static final double CENTER_HEIGHT = 1.1;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int TRAIL_STEPS = 3;

    private static final TiltedOrbits BLESSED = new TiltedOrbits(new RuneColor[]{
            RuneColor.BLUE, RuneColor.GOLD, RuneColor.PURPLE, RuneColor.RED, RuneColor.GREEN}, RADIUS, PARTICLE_SIZE, TRAIL_STEPS);
    private static final TiltedOrbits MARKED = new TiltedOrbits(new RuneColor[]{RuneColor.BLACK}, DARK_RADIUS, PARTICLE_SIZE, TRAIL_STEPS);
    /** Where each player's wisps were last tick, so their trails join up. */
    private static final Map<UUID, Vec3[]> LAST_BLESSED = new HashMap<>();
    private static final Map<UUID, Vec3[]> LAST_MARKED = new HashMap<>();
    private static ClientLevel lastLevel;

    private BlessedWispOrbit()
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
        if (level != lastLevel)
        {
            LAST_BLESSED.clear();
            LAST_MARKED.clear();
            lastLevel = level;
        }
        if (level == null)
        {
            return;
        }

        double time = level.getGameTime();
        for (Player player : level.players())
        {
            UUID id = player.getUUID();
            boolean visible = player.isAlive() && !player.isSpectator();
            boolean blessed = visible && player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING.get());
            boolean marked = visible && player.hasEffect(ModEffects.MARKED_BY_THE_DARK.get());
            Vec3 center = player.position().add(0.0, CENTER_HEIGHT, 0.0);
            if (blessed)
            {
                BLESSED.tick(level, time, center, LAST_BLESSED.computeIfAbsent(id, key -> new Vec3[BLESSED.count()]));
            }
            else
            {
                LAST_BLESSED.remove(id);
            }
            if (marked)
            {
                MARKED.tick(level, time, center, LAST_MARKED.computeIfAbsent(id, key -> new Vec3[MARKED.count()]));
            }
            else
            {
                LAST_MARKED.remove(id);
            }
        }
    }
}
