package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModFluidTypes;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Drop an Ender Pearl into Wellspring Water and the wisps come for it: three seconds of them
 * spiralling inward, and then the pearl is a Lost Waystone.
 *
 * <p>Rather than sweeping the world for floating pearls every tick, each pearl is picked up once
 * as it enters the level ({@link #onEntityJoin}) and only those few are watched afterwards - a
 * dropped item is cheap to remember and there are never many. A pearl lifted back out of the water
 * mid-soak loses its progress; the wisps only finish what stays under.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WellspringWaystoneRitual
{
    /** "A delay of 3 seconds so that wisps can fly to the floating ender pearl." */
    private static final int SOAK_TICKS = 60;
    private static final int WISPS_PER_TICK = 2;
    private static final double WISP_START_RADIUS = 2.2;

    private static final List<ItemEntity> watched = new ArrayList<>();
    private static final Map<ItemEntity, Integer> soaked = new HashMap<>();

    private WellspringWaystoneRitual()
    {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ItemEntity item))
        {
            return;
        }
        if (item.getItem().is(Items.ENDER_PEARL))
        {
            watched.add(item);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || watched.isEmpty())
        {
            return;
        }
        Iterator<ItemEntity> iterator = watched.iterator();
        while (iterator.hasNext())
        {
            ItemEntity pearl = iterator.next();
            if (!pearl.isAlive() || !pearl.getItem().is(Items.ENDER_PEARL))
            {
                iterator.remove();
                soaked.remove(pearl);
                continue;
            }
            if (!(pearl.level() instanceof ServerLevel level))
            {
                continue;
            }
            if (!inWellspring(pearl))
            {
                soaked.remove(pearl);
                continue;
            }

            int elapsed = soaked.merge(pearl, 1, Integer::sum);
            drawWisps(level, pearl, elapsed);
            if (elapsed >= SOAK_TICKS)
            {
                transform(level, pearl);
                iterator.remove();
                soaked.remove(pearl);
            }
        }
    }

    private static boolean inWellspring(ItemEntity pearl)
    {
        return pearl.isInFluidType((type, height) -> type == ModFluidTypes.WELLSPRING_WATER.get(), false);
    }

    /** Wisps converging on the pearl, the ring tightening as the three seconds run out. */
    private static void drawWisps(ServerLevel level, ItemEntity pearl, int elapsed)
    {
        double progress = elapsed / (double) SOAK_TICKS;
        double radius = WISP_START_RADIUS * (1.0 - progress) + 0.15;
        Vec3 target = pearl.position().add(0.0, 0.15, 0.0);

        for (int i = 0; i < WISPS_PER_TICK; i++)
        {
            double angle = (elapsed * 0.35) + (Math.PI * 2.0 / WISPS_PER_TICK) * i;
            double x = target.x + Math.cos(angle) * radius;
            double z = target.z + Math.sin(angle) * radius;
            double y = target.y + Math.sin(elapsed * 0.2 + i) * 0.35;

            // Count 0 makes the three offsets a velocity instead of a spread, which is what
            // actually sends each wisp travelling toward the pearl rather than just sitting near it.
            Vec3 toward = target.subtract(x, y, z).normalize().scale(0.08);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, toward.x, toward.y, toward.z, 1.0);
        }

        if (elapsed % 20 == 0)
        {
            level.playSound(null, pearl.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.AMBIENT, 0.5f, 1.4f);
        }
    }

    private static void transform(ServerLevel level, ItemEntity pearl)
    {
        ItemStack pearls = pearl.getItem();
        pearl.setItem(new ItemStack(ModItems.LOST_WAYSTONE.get(), pearls.getCount()));

        level.sendParticles(ParticleTypes.END_ROD, pearl.getX(), pearl.getY() + 0.2, pearl.getZ(),
                40, 0.3, 0.3, 0.3, 0.08);
        level.sendParticles(ParticleTypes.GLOW, pearl.getX(), pearl.getY() + 0.2, pearl.getZ(),
                18, 0.35, 0.35, 0.35, 0.02);
        level.playSound(null, pearl.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.7f, 1.6f);
    }
}
