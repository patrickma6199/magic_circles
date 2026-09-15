package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.FerrymanEntity;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.registry.ModFluidTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carrying {@code registry/ModEffects#MARKED_BY_THE_DARK} (see {@code limbo/RiteOfPassage}, the
 * only thing that ever applies it): normal magic refuses a marked player, and the one cure is 10
 * continuous seconds swimming in Wellspring Water, which only exists in the Fairy Realm - so
 * clearing the mark genuinely requires making that trip.
 *
 * <p>This used to also send phantoms after a marked player out in the living world. That's gone:
 * phantoms belong to the veil and nowhere else, they hunt only while their victim is still across
 * it, and they stop the instant that person comes home (see {@code RealmOfTheDeadManager} and
 * {@code entity/GhostPhantomEntity}). Being marked now costs you the heartstone's favor until you
 * reach the Wellspring, not a permanent escort of monsters.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MarkedByTheDarkManager
{
    private static final int CURE_SWIM_TICKS = 20 * 10;

    /** "2.5-3.5 seconds" between glimpses. */
    private static final int HAUNT_MIN_TICKS = 50;
    private static final int HAUNT_RANDOM_TICKS = 21;
    /** How long he stands there before going again - long enough to see, short enough to doubt. */
    private static final int HAUNT_LINGER_TICKS = 16;
    private static final double HAUNT_MIN_DISTANCE = 7.0;
    private static final double HAUNT_RANDOM_DISTANCE = 7.0;

    private static final Map<UUID, Integer> wellspringSwimTicks = new HashMap<>();
    private static final Map<UUID, Integer> hauntCountdown = new HashMap<>();
    private static final List<Haunt> hauntsToDismiss = new ArrayList<>();

    private MarkedByTheDarkManager()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        if (!(player.level() instanceof ServerLevel level))
        {
            return;
        }
        UUID id = player.getUUID();

        if (!player.hasEffect(ModEffects.MARKED_BY_THE_DARK.get()))
        {
            wellspringSwimTicks.remove(id);
            hauntCountdown.remove(id);
            return;
        }

        tickWellspringCure(level, player, id);
        tickHaunting(level, player, id);
    }

    /** How near a real Ferryman has to be to stop the glimpses - comfortably beyond sight in most places. */
    private static final double HAUNT_SUPPRESS_RADIUS = 64.0;

    /**
     * Whether a real Ferryman - not a glimpse - is somewhere this player can see him. Across the
     * veil that is the one carrying them; in the living world it is the one the summoning dragged
     * through, or a veiled one caught by Deathsight.
     */
    private static boolean realFerrymanInSight(ServerLevel level, ServerPlayer player)
    {
        return !level.getEntitiesOfClass(FerrymanEntity.class, player.getBoundingBox().inflate(HAUNT_SUPPRESS_RADIUS),
                ferryman -> ferryman.getHauntTarget() == null
                        && (!ferryman.isVeiled() || Veil.visibleTo(ferryman, player))).isEmpty();
    }

    /**
     * The Ferryman, glimpsed. While you carry the Mark he keeps appearing at the edge of where you
     * are and vanishing again a moment later - never approaching, never doing anything, just there
     * and then not. Only the Marked player is ever sent him ({@code Veil#visibleTo}), so to anyone
     * else that ground is empty: the Mark means a foot in each plane, and this is the half nobody
     * else can see.
     */
    private static void tickHaunting(ServerLevel level, ServerPlayer player, UUID id)
    {
        int remaining = hauntCountdown.merge(id, -1, Integer::sum);
        if (remaining > 0)
        {
            return;
        }
        hauntCountdown.put(id, HAUNT_MIN_TICKS + level.random.nextInt(HAUNT_RANDOM_TICKS));

        // There is only ever one Ferryman - at least for each person. Nobody is haunted while a
        // Ferryman stands for them: the one carrying them across the veil (for as long as that
        // lasts, they are behind it), or the one they dragged into the living world with the
        // summoning, wherever he is standing. Nor while any real one is in sight. Two people can
        // each have their own; no one person ever sees two.
        if (LimboState.isInLimbo(player)
                || com.patrickma.magiccircles.curse.SummoningRite.hasLiveSummon(player)
                || realFerrymanInSight(level, player))
        {
            return;
        }

        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double distance = HAUNT_MIN_DISTANCE + level.random.nextDouble() * HAUNT_RANDOM_DISTANCE;
        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        BlockPos ground = findStandableGround(level, x, player.getBlockY(), z);
        if (ground == null)
        {
            return;
        }

        FerrymanEntity haunt = new FerrymanEntity(ModEntities.FERRYMAN.get(), level);
        haunt.moveTo(x, ground.getY(), z, 0.0f, 0.0f);
        haunt.setHauntTarget(id);
        haunt.setVeiled(false);
        haunt.setInvulnerable(true);
        haunt.setSilent(true);
        // Always looking straight at them, which is considerably worse than him ignoring them.
        haunt.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.position());
        level.addFreshEntity(haunt);

        hauntsToDismiss.add(new Haunt(haunt, level.getGameTime() + HAUNT_LINGER_TICKS));
    }

    /** Finds a solid-enough footing near the sampled column, so he doesn't stand inside a hill or hover over a ravine. */
    private static BlockPos findStandableGround(ServerLevel level, double x, int aroundY, double z)
    {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(x), aroundY + 3, Mth.floor(z));
        for (int drop = 0; drop < 8; drop++)
        {
            if (!level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()
                    && level.getBlockState(cursor.above(2)).isAir())
            {
                return cursor.above().immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    /** Takes each haunting away again once its moment is up. */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || hauntsToDismiss.isEmpty())
        {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        java.util.Iterator<Haunt> iterator = hauntsToDismiss.iterator();
        while (iterator.hasNext())
        {
            Haunt haunt = iterator.next();
            if (now >= haunt.dismissAt || !haunt.ferryman.isAlive())
            {
                haunt.ferryman.discard();
                iterator.remove();
            }
        }
    }

    private record Haunt(FerrymanEntity ferryman, long dismissAt)
    {
    }

    private static void tickWellspringCure(ServerLevel level, ServerPlayer player, UUID id)
    {
        boolean inWellspring = player.isInFluidType((type, height) -> type == ModFluidTypes.WELLSPRING_WATER.get(), false);
        if (!inWellspring)
        {
            wellspringSwimTicks.remove(id);
            return;
        }

        int ticks = wellspringSwimTicks.merge(id, 1, Integer::sum);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
        if (ticks >= CURE_SWIM_TICKS)
        {
            player.removeEffect(ModEffects.MARKED_BY_THE_DARK.get());
            wellspringSwimTicks.remove(id);
            player.displayClientMessage(Component.translatable("effect.magiccircles.marked_by_the_dark.cured"), true);
        }
    }
}
