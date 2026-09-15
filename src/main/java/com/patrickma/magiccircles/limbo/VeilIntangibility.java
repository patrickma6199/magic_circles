package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The realm of the dead leaves no mark on the world of the living. A creature behind the veil - a
 * pet, a villager, a fairy, anything named - is never in the way: the living build straight through
 * where it stands, and a block set down on it simply moves it a block aside. Nor does it press a
 * plate, trip a wire (see {@code mixin/EntityMixin}) or trample a field.
 *
 * <p>What makes a block refuse to go where a creature stands is that creature's {@code
 * blocksBuilding} flag - true for every living thing, and never saved - so it is cleared whenever a
 * creature crosses over ({@link LimboRegistry#ghostifyPet}) and again whenever a ghost loads.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeilIntangibility
{
    /** Where a ghost is moved to when a block is set down on it, nearest first: aside, then up, then up and aside. */
    private static final int[][] STEPS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {0, 1, 0},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {0, 2, 0}
    };

    private VeilIntangibility()
    {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide() && LimboRegistry.isCreatureGhost(event.getEntity()))
        {
            event.getEntity().blocksBuilding = false;
        }
    }

    /** A block set down where a ghost stands moves the ghost - never the block. */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        BlockPos pos = event.getPos();
        for (Entity ghost : level.getEntities((Entity) null, new AABB(pos), LimboRegistry::isCreatureGhost))
        {
            stepAside(level, ghost, pos);
        }
    }

    private static void stepAside(ServerLevel level, Entity ghost, BlockPos placed)
    {
        for (int[] step : STEPS)
        {
            AABB moved = ghost.getBoundingBox().move(step[0], step[1], step[2]);
            if (level.noCollision(ghost, moved))
            {
                Vec3 to = ghost.position().add(step[0], step[1], step[2]);
                ghost.teleportTo(to.x, to.y, to.z);
                return;
            }
        }
        // Boxed in on every side: onto the block itself.
        ghost.teleportTo(ghost.getX(), placed.getY() + 1.0, ghost.getZ());
    }

    /** No field is trampled by something the living cannot see. */
    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event)
    {
        if (LimboRegistry.isCreatureGhost(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }
}
