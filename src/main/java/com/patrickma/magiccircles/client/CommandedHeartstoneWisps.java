package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.CommandedSpellCasting;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.ritual.HeartSpell;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The visual half of casting a spell from a commanded Heartstone (see {@code
 * HeartstoneItem#use}/{@code CommandedSpellCasting}) - purely client-local, purely cosmetic, and
 * only ever shown to the casting player (no networking - the client that clicked already knows
 * everything it needs: which spell, and its own position at the moment of casting).
 *
 * <p>Three white wisps always orbit the player for the whole duration ({@link
 * CommandedSpellCasting#DURATION_TICKS} for a prolonged spell; a short burst for a one-shot one) -
 * the same "a spell is still running" language {@code ClientHeartWisps}' own white ring uses for a
 * real Heart Core, just centered on a person instead of a block. The spell's own color(s) fly out
 * from roughly where the held stone is and back, in the same rotation sense the ring-cast version
 * uses - except for {@link HeartSpell#SHIELD}, whose purple wisps instead trace a *stationary*
 * ring at the exact world position the player was standing in when they cast it (mirroring {@code
 * ShieldRingWisps}, but around a bare point in space rather than a placed ring of runes), since the
 * hand-cast shield itself doesn't move with the player either.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CommandedHeartstoneWisps
{
    private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final int WHITE_COUNT = 3;
    private static final double WHITE_RADIUS = 1.4;
    private static final double WHITE_SPEED = 0.05;
    private static final double COLOR_RADIUS = 2.2;
    private static final double COLOR_SPEED = 0.03;
    private static final float PARTICLE_SIZE = 0.85f;
    private static final int ONE_SHOT_BURST_TICKS = 20;

    private static final int SHIELD_WISP_COUNT = MagicCircleRitual.RING_OFFSETS.length;
    private static final double SHIELD_RADIUS = 7.0;
    private static final double SHIELD_SPEED = 0.022;
    private static final double SHIELD_LEG_HEIGHT = 0.3;

    private static final Map<UUID, ActiveVisual> ACTIVE = new HashMap<>();

    private CommandedHeartstoneWisps()
    {
    }

    /** Called from {@code HeartstoneItem#use}, client side only, the instant a commanded cast is triggered. */
    public static void start(Player player, HeartSpell spell)
    {
        RuneColor[] colors = spellColors(spell);
        int duration = spell.isProlonged() ? CommandedSpellCasting.DURATION_TICKS : ONE_SHOT_BURST_TICKS;
        ACTIVE.put(player.getUUID(), new ActiveVisual(spell, colors, player.position(), duration));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty())
        {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        Level level = mc.level;
        Player localPlayer = mc.player;
        if (level == null || localPlayer == null)
        {
            return;
        }

        ActiveVisual visual = ACTIVE.get(localPlayer.getUUID());
        if (visual == null)
        {
            return;
        }
        visual.ticksRemaining--;
        if (visual.ticksRemaining <= 0)
        {
            ACTIVE.remove(localPlayer.getUUID());
            return;
        }

        double time = level.getGameTime();
        tickWhiteOrbit(level, localPlayer, time);
        if (visual.spell == HeartSpell.SHIELD)
        {
            tickStationaryRing(level, visual.castCenter, time);
        }
        else
        {
            tickColorBurst(level, localPlayer, visual.colors, time);
        }
    }

    private static void tickWhiteOrbit(Level level, Player player, double time)
    {
        for (int i = 0; i < WHITE_COUNT; i++)
        {
            double angle = time * WHITE_SPEED + (2.0 * Math.PI / WHITE_COUNT) * i;
            double x = player.getX() + Math.cos(angle) * WHITE_RADIUS;
            double z = player.getZ() + Math.sin(angle) * WHITE_RADIUS;
            double y = player.getY() + player.getBbHeight() * 0.6;
            level.addParticle(new DustParticleOptions(WHITE, PARTICLE_SIZE), x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** The spell's own color(s), flying out from roughly hand height and back - same rotation direction (+1.0) the ring-cast version's own colored wisps use. */
    private static void tickColorBurst(Level level, Player player, RuneColor[] colors, double time)
    {
        for (int i = 0; i < colors.length; i++)
        {
            Vector3f color = colors[i].wispColor();
            double phase = colors.length > 1 ? (Math.PI * i) : 0.0;
            double angle = time * COLOR_SPEED + phase;
            double radius = COLOR_RADIUS * (0.5 + 0.5 * Math.sin(time * 0.05 + phase));
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + 1.0 + 0.3 * Math.sin(time * 0.07 + phase);
            level.addParticle(new DustParticleOptions(color, PARTICLE_SIZE), x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** Purple wisps tracing a fixed ring at {@code center} - the shield boundary itself, unmoving even as the player who cast it walks away. Same math as {@code ShieldRingWisps}, just around a bare Vec3 instead of a placed ring's own rune positions. */
    private static void tickStationaryRing(Level level, Vec3 center, double time)
    {
        Vector3f purple = RuneColor.PURPLE.wispColor();
        double progress = (time * SHIELD_SPEED) % SHIELD_WISP_COUNT;
        if (progress < 0)
        {
            progress += SHIELD_WISP_COUNT;
        }
        for (int i = 0; i < SHIELD_WISP_COUNT; i++)
        {
            double t = (progress + i) % SHIELD_WISP_COUNT;
            double angle = (t / SHIELD_WISP_COUNT) * 2.0 * Math.PI;
            double x = center.x + Math.cos(angle) * SHIELD_RADIUS;
            double z = center.z + Math.sin(angle) * SHIELD_RADIUS;
            double y = center.y + SHIELD_LEG_HEIGHT;
            level.addParticle(new DustParticleOptions(purple, 0.8f), x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** The 1 (solo) or 2 (combo) {@link RuneColor}s that cast this spell, per {@link HeartSpell#soloFor}/{@link HeartSpell#comboFor}. */
    private static RuneColor[] spellColors(HeartSpell spell)
    {
        return switch (spell)
        {
            case STORM -> new RuneColor[]{RuneColor.BLUE};
            case SHIELD -> new RuneColor[]{RuneColor.PURPLE};
            case FLOWERS -> new RuneColor[]{RuneColor.GOLD};
            case HEAL -> new RuneColor[]{RuneColor.RED};
            case XP -> new RuneColor[]{RuneColor.GREEN};
            case TEMPEST_WARD -> new RuneColor[]{RuneColor.BLUE, RuneColor.PURPLE};
            case CLEANSING_RAIN -> new RuneColor[]{RuneColor.BLUE, RuneColor.RED};
            case VERDANT_HARVEST -> new RuneColor[]{RuneColor.GOLD, RuneColor.GREEN};
            case BLOOM_OF_LIFE -> new RuneColor[]{RuneColor.GOLD, RuneColor.RED};
            case MANA_FONT -> new RuneColor[]{RuneColor.PURPLE, RuneColor.GREEN};
            case VITAL_SURGE -> new RuneColor[]{RuneColor.RED, RuneColor.GREEN};
        };
    }

    private static final class ActiveVisual
    {
        final HeartSpell spell;
        final RuneColor[] colors;
        final Vec3 castCenter;
        int ticksRemaining;

        ActiveVisual(HeartSpell spell, RuneColor[] colors, Vec3 castCenter, int ticksRemaining)
        {
            this.spell = spell;
            this.colors = colors;
            this.castCenter = castCenter;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
