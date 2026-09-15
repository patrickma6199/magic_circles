package com.patrickma.magiccircles.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * A placed book that other people can see being read. While someone has its screen open, the book
 * opens, lifts off its resting place, and turns and tilts to hold its pages up toward the reader's
 * eyes; when they put it down it closes and settles back the way it was placed.
 *
 * <p>Reading happens entirely in a client-side screen, so the server has to be told: the block's
 * right-click names the reader here ({@link #startReading}), the reader's screen closing sends
 * {@code network/CloseBookPacket} ({@link #stopReading}), and {@link #serverTick} lets go of anyone
 * who wanders off, dies or logs out without closing it. Who is reading is synced to every client
 * through the block entity's update tag - never saved, since an entity id means nothing after a
 * restart.
 *
 * <p>The animation itself is purely client-side: each client eases its own copy toward where the
 * reader currently stands, the same way the Enchanting Table's book tracks the nearest player.
 */
public abstract class ReadableBookBlockEntity extends BlockEntity
{
    private static final int NO_READER = -1;
    /** Container reach: step further away than this and you have stopped reading. */
    private static final double MAX_READ_DISTANCE = 8.0;
    private static final int READER_CHECK_INTERVAL_TICKS = 10;
    /** How far the book lifts off its resting place while open. */
    private static final double READING_LIFT = 0.35;
    /** Held up to be read - never stood fully on end, never left lying flat. */
    private static final float MIN_READING_TILT = 20.0F;
    private static final float MAX_READING_TILT = 75.0F;
    /** Openness gained or lost per tick - about half a second from shut to open. */
    private static final float OPEN_STEP = 0.1F;
    /** Fraction of the remaining turn made each tick. */
    private static final float TURN_EASE = 0.2F;

    private int readerId = NO_READER;
    private int age;

    // Client-side animation, previous and current tick, interpolated by the renderer.
    private boolean animationStarted;
    private float open;
    private float openO;
    private float yaw;
    private float yawO;
    private float tilt;
    private float tiltO;

    protected ReadableBookBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    /** The angle the book lies at on its own - see the renderers for what the numbers mean. */
    public abstract float restingTilt();

    /** How high above its block the book rests. */
    public abstract double restingHeight();

    public int getAge()
    {
        return age;
    }

    public void startReading(Player player)
    {
        readerId = player.getId();
        syncReader();
    }

    public void stopReading(Player player)
    {
        if (readerId == player.getId())
        {
            readerId = NO_READER;
            syncReader();
        }
    }

    private void syncReader()
    {
        if (level != null && !level.isClientSide)
        {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReadableBookBlockEntity book)
    {
        if (book.readerId == NO_READER || level.getGameTime() % READER_CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }
        Entity reader = level.getEntity(book.readerId);
        if (!(reader instanceof Player player) || !player.isAlive()
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_READ_DISTANCE * MAX_READ_DISTANCE)
        {
            book.readerId = NO_READER;
            book.syncReader();
        }
    }

    /** One tick of the open/turn/tilt animation - called from each book's own client tick. */
    protected void tickClient(Level level)
    {
        age++;
        float restYaw = restingYaw();
        if (!animationStarted)
        {
            yaw = restYaw;
            tilt = restingTilt();
            animationStarted = true;
        }
        openO = open;
        yawO = yaw;
        tiltO = tilt;

        float targetOpen = 0.0F;
        float targetYaw = restYaw;
        float targetTilt = restingTilt();
        Entity reader = readerId == NO_READER ? null : level.getEntity(readerId);
        if (reader != null)
        {
            double dx = reader.getX() - (worldPosition.getX() + 0.5);
            double dz = reader.getZ() - (worldPosition.getZ() + 0.5);
            double dy = reader.getEyeY() - (worldPosition.getY() + restingHeight() + READING_LIFT);
            targetOpen = 1.0F;
            // The same heading convention the Enchanting Table's book uses, which is also what
            // restingYaw produces for the block's own facing - so turning to a reader and settling
            // back afterwards are the same rotation.
            targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx));
            // Tilting the book back by this much points its open pages straight at the reader's eyes.
            float elevation = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            targetTilt = Mth.clamp(elevation, MIN_READING_TILT, MAX_READING_TILT);
        }

        open = Mth.clamp(open + Mth.clamp(targetOpen - open, -OPEN_STEP, OPEN_STEP), 0.0F, 1.0F);
        yaw = Mth.wrapDegrees(yaw + Mth.wrapDegrees(targetYaw - yaw) * TURN_EASE);
        tilt += (targetTilt - tilt) * TURN_EASE;
    }

    /** Which way the book lies when nobody is reading it, from the block's own facing. */
    private float restingYaw()
    {
        return getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                .orElse(Direction.NORTH).getClockWise().toYRot();
    }

    /** 0 shut, 1 fully open. */
    public float openness(float partialTick)
    {
        return Mth.lerp(partialTick, openO, open);
    }

    /** Heading in degrees, for {@code Axis.YP.rotationDegrees(-yaw)}. */
    public float yaw(float partialTick)
    {
        return yawO + Mth.wrapDegrees(yaw - yawO) * partialTick;
    }

    /** Tilt in degrees, for {@code Axis.ZP.rotationDegrees(tilt)}: 90 lies flat, lower stands it up. */
    public float tilt(float partialTick)
    {
        return Mth.lerp(partialTick, tiltO, tilt);
    }

    /** How far the book has lifted off its resting place, eased so it rises and settles gently. */
    public double lift(float partialTick)
    {
        float t = openness(partialTick);
        return READING_LIFT * t * t * (3.0F - 2.0F * t);
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Reader", readerId);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        // Only ever present in an update from the server - never on disk.
        readerId = tag.contains("Reader") ? tag.getInt("Reader") : NO_READER;
    }
}
