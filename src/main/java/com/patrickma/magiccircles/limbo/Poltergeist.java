package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A ghost who died carrying a curse. The curse goes over with them, and it keeps a hand in the
 * living world: a poltergeist can still work doors, trapdoors, gates, buttons and levers, lift the
 * lid of a chest (and look inside, never take), call rain down on the living, and drag a skeleton up
 * out of the ground. They are otherwise an ordinary ghost - and come home the ordinary way.
 *
 * <p>Marked at death in {@code DeathLimboManager}; the mark lives in {@link LimboState} and goes
 * with everything else there when they are brought back. The two powers are key bindings (see
 * {@code client/GhostHud}), sent here by {@code network/PoltergeistPacket}.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Poltergeist
{
    public static final byte CALL_RAIN = 0;
    public static final byte RAISE_SKELETON = 1;

    /** Public so the HUD can show how far along each power's recovery is. */
    public static final int RAIN_COOLDOWN_TICKS = 20 * 60 * 5;
    private static final int RAIN_DURATION_TICKS = 20 * 60 * 3;
    public static final int SKELETON_COOLDOWN_TICKS = 20 * 60;
    /** How long a chest lid stays up after a poltergeist lifts it. */
    private static final int LID_OPEN_TICKS = 30;
    /** A ghost can drift well above the ground; this is how far down to look for somewhere to raise a skeleton. */
    private static final int GROUND_SEARCH_DEPTH = 24;

    private static final Map<UUID, Long> rainReadyAt = new HashMap<>();
    private static final Map<UUID, Long> skeletonReadyAt = new HashMap<>();
    private static final List<Lid> lidsToClose = new ArrayList<>();

    private Poltergeist()
    {
    }

    public static boolean is(Player player)
    {
        return LimboState.isPoltergeist(player);
    }

    /** Called at the moment of a cursed death, after the player has become a ghost. */
    static void begin(ServerPlayer player)
    {
        LimboState.makePoltergeist(player);
        sync(player);
        player.sendSystemMessage(Component.translatable("limbo.magiccircles.poltergeist.become",
                        Component.keybind("key.magiccircles.poltergeist_rain"),
                        Component.keybind("key.magiccircles.poltergeist_skeleton"))
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    // ------------------------------------------------------------------
    // A hand in the living world
    // ------------------------------------------------------------------

    /**
     * Runs ahead of {@code GhostRules}, which refuses the dead every right-click - so what a
     * poltergeist is allowed is decided here, and anything else falls through to that refusal.
     * Forge fires this before vanilla's own "spectators may only open menus" rule, which is what
     * lets a spectator's hand reach a door at all.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !is(player)
                || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        InteractionResult result;
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock)
        {
            result = state.use(level, player, event.getHand(), event.getHitVec());
        }
        else
        {
            MenuProvider menu = state.getMenuProvider(level, pos);
            if (menu == null)
            {
                return;
            }
            // Looking only - vanilla never lets a spectator move anything in a container.
            player.openMenu(menu);
            if (block instanceof ChestBlock)
            {
                creakOpen(level, pos, state);
            }
            result = InteractionResult.SUCCESS;
        }
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    /**
     * A spectator opening a chest leaves its lid shut - nobody would ever know. A poltergeist's lid
     * lifts, creaks, and drops again a moment later, for everyone watching.
     */
    private static void creakOpen(ServerLevel level, BlockPos pos, BlockState state)
    {
        List<BlockPos> halves = new ArrayList<>(List.of(pos.immutable()));
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE)
        {
            halves.add(pos.relative(ChestBlock.getConnectedDirection(state)).immutable());
        }
        for (BlockPos half : halves)
        {
            level.blockEvent(half, state.getBlock(), 1, 1);
        }
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 0.6f);
        lidsToClose.add(new Lid(level, halves, state.getBlock(), level.getGameTime() + LID_OPEN_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || lidsToClose.isEmpty())
        {
            return;
        }
        Iterator<Lid> iterator = lidsToClose.iterator();
        while (iterator.hasNext())
        {
            Lid lid = iterator.next();
            if (lid.level.getGameTime() < lid.closeAt)
            {
                continue;
            }
            iterator.remove();
            BlockPos first = lid.halves.get(0);
            // Someone living has it open in the meantime - their lid, not ours, so leave it be.
            if (!lid.level.getBlockState(first).is(lid.block) || ChestBlockEntity.getOpenCount(lid.level, first) > 0)
            {
                continue;
            }
            for (BlockPos half : lid.halves)
            {
                lid.level.blockEvent(half, lid.block, 1, 0);
            }
            lid.level.playSound(null, first, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 0.6f);
        }
    }

    private record Lid(ServerLevel level, List<BlockPos> halves, Block block, long closeAt)
    {
    }

    // ------------------------------------------------------------------
    // Powers
    // ------------------------------------------------------------------

    public static void useAbility(ServerPlayer player, byte ability)
    {
        if (!is(player))
        {
            return;
        }
        if (ability == CALL_RAIN)
        {
            callRain(player);
        }
        else if (ability == RAISE_SKELETON)
        {
            raiseSkeleton(player);
        }
        sync(player);
    }

    private static void callRain(ServerPlayer player)
    {
        ServerLevel level = player.serverLevel();
        // Only the overworld keeps its own weather - every other dimension borrows it and ignores being told.
        if (level.dimension() != Level.OVERWORLD)
        {
            player.displayClientMessage(Component.translatable("limbo.magiccircles.poltergeist.rain_here"), true);
            return;
        }
        if (!ready(player, rainReadyAt, level.getGameTime()))
        {
            return;
        }
        level.setWeatherParameters(0, RAIN_DURATION_TICKS, true, false);
        rainReadyAt.put(player.getUUID(), level.getGameTime() + RAIN_COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.WEATHER, 1.0f, 0.7f);
        player.displayClientMessage(Component.translatable("limbo.magiccircles.poltergeist.rain")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    private static void raiseSkeleton(ServerPlayer player)
    {
        ServerLevel level = player.serverLevel();
        if (!ready(player, skeletonReadyAt, level.getGameTime()))
        {
            return;
        }
        BlockPos ground = findGround(level, player.blockPosition());
        if (ground == null)
        {
            player.displayClientMessage(Component.translatable("limbo.magiccircles.poltergeist.no_ground"), true);
            return;
        }
        Skeleton skeleton = EntityType.SKELETON.create(level);
        if (skeleton == null)
        {
            return;
        }
        skeleton.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.MOB_SUMMONED, null, null);
        level.addFreshEntity(skeleton);

        level.sendParticles(ParticleTypes.SOUL, ground.getX() + 0.5, ground.getY() + 0.2, ground.getZ() + 0.5,
                20, 0.4, 0.1, 0.4, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5,
                10, 0.3, 0.4, 0.3, 0.01);
        level.playSound(null, ground, SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.6f);

        skeletonReadyAt.put(player.getUUID(), level.getGameTime() + SKELETON_COOLDOWN_TICKS);
        player.displayClientMessage(Component.translatable("limbo.magiccircles.poltergeist.skeleton")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** False, with a message saying how long is left, while this power is still gathering itself. */
    private static boolean ready(ServerPlayer player, Map<UUID, Long> readyAt, long now)
    {
        Long at = readyAt.get(player.getUUID());
        if (at != null && now < at)
        {
            long seconds = (at - now + 19) / 20;
            player.displayClientMessage(Component.translatable("limbo.magiccircles.poltergeist.cooldown", seconds), true);
            return false;
        }
        return true;
    }

    /** The first spot at or below {@code from} with firm footing and room for a skeleton to stand. */
    private static BlockPos findGround(ServerLevel level, BlockPos from)
    {
        BlockPos.MutableBlockPos cursor = from.mutable();
        for (int i = 0; i < GROUND_SEARCH_DEPTH && cursor.getY() > level.getMinBuildHeight(); i++)
        {
            BlockPos below = cursor.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
                    && level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty())
            {
                return cursor.immutable();
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Keeping the client's HUD in step
    // ------------------------------------------------------------------

    private static final int SYNC_INTERVAL_TICKS = 10;
    /** Players whose client currently believes they are a poltergeist - told again when they stop being one. */
    private static final java.util.Set<UUID> synced = new java.util.HashSet<>();

    /** Tells this player's client whether they are a poltergeist, and how long each power has left to gather. */
    private static void sync(ServerPlayer player)
    {
        long now = player.serverLevel().getGameTime();
        boolean active = is(player);
        com.patrickma.magiccircles.network.ModNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.patrickma.magiccircles.network.PoltergeistStatusPacket(active,
                        remaining(rainReadyAt, player, now), remaining(skeletonReadyAt, player, now)));
        if (active)
        {
            synced.add(player.getUUID());
        }
        else
        {
            synced.remove(player.getUUID());
        }
    }

    private static int remaining(Map<UUID, Long> readyAt, ServerPlayer player, long now)
    {
        Long at = readyAt.get(player.getUUID());
        return at == null ? 0 : (int) Math.max(0L, at - now);
    }

    /**
     * Keeps the HUD honest: a poltergeist is re-told twice a second, and one who has just stopped
     * being one - brought back, timed out - is told once more so their HUD clears.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % SYNC_INTERVAL_TICKS != 0)
        {
            return;
        }
        if (is(player) || synced.contains(player.getUUID()))
        {
            sync(player);
        }
    }

    /** A returning player's client starts from nothing, so they are told afresh. */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)
    {
        synced.remove(event.getEntity().getUUID());
    }
}
