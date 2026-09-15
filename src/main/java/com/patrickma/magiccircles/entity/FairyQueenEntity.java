package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.FairyCourt;
import com.patrickma.magiccircles.FairyQueenNames;
import com.patrickma.magiccircles.FairyWard;
import com.patrickma.magiccircles.worldgen.FairyThroneRoom;
import com.patrickma.magiccircles.limbo.LimboState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Fairy Queen. There is only ever one in the Fairy Realm, and there is always one: when the
 * throne is empty, a fairy woman steps up to it (see {@link FairyCourt}). She alone of anything that
 * lives has made her peace with both the Wellspring and the darkness, and her wisps show it - one of
 * every colour, the dark rite's among them, each circling her on its own tilted path. Because she
 * stands on both sides at once, she can see the dead, and the dead can speak to her (see {@code
 * FairyTrades#acceptBargain}).
 *
 * <p>She never lands - the only place she ever settles is her throne in the Fairy Court, high in the
 * World Tree's heartwood (see {@link FairyThroneRoom} and {@link HoldCourtGoal}), where she sits a
 * while now and then watching the way in. Strike her, or strike any fairy within {@value
 * #PRESENCE_RADIUS} blocks of her,
 * and she condemns you: the sky answers you with lightning, again and again, until you are dead -
 * even from inside her ward, which she raises like any fairy but does not flee from. Between bolts
 * she keeps her distance above you and sends volleys of wisps after you.
 *
 * <p>She doesn't cross over when she dies; she returns to the Wellspring whole, and the throne passes
 * on. The one being that belongs to both sides is claimed by neither.
 */
public class FairyQueenEntity extends FairyEntity
{
    public static final double PRESENCE_RADIUS = 32.0;
    private static final double WRATH_RANGE = 64.0;
    private static final int LIGHTNING_INTERVAL_TICKS = 60;
    private static final int VOLLEY_INTERVAL_TICKS = 100;
    private static final double KEEP_DISTANCE = 10.0;
    private static final double HOVER_ABOVE = 5.0;
    private static final int COURT_REPORT_INTERVAL_TICKS = 100;

    /** Those she has condemned - only for as long as the server runs, and only until they die. */
    private final Set<UUID> condemned = new HashSet<>();
    @Nullable
    private ServerPlayer wrathTarget;
    private int lightningCooldown = 20;
    private int volleyCooldown = 40;

    /** Whether she is on her throne - synced, so every client draws her seated (see client/QueenModel). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_SEATED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FairyQueenEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public FairyQueenEntity(EntityType<? extends FairyQueenEntity> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return FairyEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.FLYING_SPEED, 0.95)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals()
    {
        super.registerGoals();
        this.goalSelector.addGoal(1, new CourtWrathGoal(this));
        this.goalSelector.addGoal(2, new HoldCourtGoal(this));
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SEATED, false);
    }

    public boolean isSeated()
    {
        return this.entityData.get(DATA_SEATED);
    }

    /** Takes her throne - see {@link FairyThroneRoom}. */
    private void sitOnThrone()
    {
        this.entityData.set(DATA_SEATED, true);
        this.getNavigation().stop();
        pinToThrone();
    }

    /** Held in place on the seat, facing the way in - her head still turns to whoever comes. */
    private void pinToThrone()
    {
        Vec3 seat = FairyThroneRoom.SEATED_POSITION;
        this.setPos(seat.x, seat.y, seat.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(FairyThroneRoom.SEAT_YAW);
        this.yBodyRot = FairyThroneRoom.SEAT_YAW;
        this.yBodyRotO = FairyThroneRoom.SEAT_YAW;
    }

    /** Rises from the throne and takes to the air again, just in front of it. */
    private void standFromThrone()
    {
        if (isSeated())
        {
            this.entityData.set(DATA_SEATED, false);
            Vec3 up = FairyThroneRoom.APPROACH;
            this.setPos(up.x, up.y, up.z);
        }
    }

    /** Struck on her throne, she is on her feet - and in the air - before anything else. */
    @Override
    protected void onAttacked(ServerLevel level, net.minecraft.world.entity.LivingEntity attacker)
    {
        standFromThrone();
        super.onAttacked(level, attacker);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag)
    {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        setFemale(true);
        // Every queen carries a name and her title, for all to see (FairyCourt#crown gives a fairy
        // who steps up her own name back first).
        if (!hasCustomName())
        {
            setCustomName(Component.literal(FairyQueenNames.crown(FairyQueenNames.pick(this.random))));
        }
        setCustomNameVisible(true);
        return data;
    }

    @Override
    public boolean isQueen()
    {
        return true;
    }

    /** She sells one thing only, and nobody else does: the Art of Blood. */
    @Override
    public int tradeId()
    {
        return com.patrickma.magiccircles.FairyTrades.QUEEN_TRADE;
    }

    /** Her people's voice, lower - every word, every wingbeat (see client/WingFlutterSound). */
    @Override
    public float getVoicePitch()
    {
        return super.getVoicePitch() * 0.8f;
    }

    /** Always on the wing. */
    @Override
    public boolean isFlying()
    {
        return true;
    }

    @Override
    protected void startResting()
    {
        // She never settles on a branch like her people - only ever on her throne (see HoldCourtGoal).
    }

    @Override
    protected boolean fleesWhenWardLifts()
    {
        return false;
    }

    @Override
    protected boolean isOccupied()
    {
        return super.isOccupied() || this.wrathTarget != null;
    }

    @Override
    public boolean willTradeWith(Player player)
    {
        return !this.condemned.contains(player.getUUID()) && super.willTradeWith(player);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level() instanceof ServerLevel level)
        {
            this.setNoGravity(true);
            if (this.tickCount % COURT_REPORT_INTERVAL_TICKS == 0)
            {
                FairyCourt.noteQueen(level, this);
            }
        }
    }

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        tickWrath((ServerLevel) this.level());
    }

    private void tickWrath(ServerLevel level)
    {
        // Wrath ends with its object: dead, gone across the veil, or gone from the realm.
        this.condemned.removeIf(id -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            return player == null || player.level() != level || !player.isAlive() || LimboState.isInLimbo(player);
        });

        this.wrathTarget = null;
        double nearest = WRATH_RANGE * WRATH_RANGE;
        for (UUID id : this.condemned)
        {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.isCreative() || player.isSpectator())
            {
                continue;
            }
            double distance = player.distanceToSqr(this);
            if (distance < nearest)
            {
                nearest = distance;
                this.wrathTarget = player;
            }
        }
        if (this.wrathTarget == null)
        {
            return;
        }

        // Her wrath brings the rain down with the lightning, for as long as it lasts.
        if (level.dimension().equals(com.patrickma.magiccircles.registry.ModDimensions.FAIRY_REALM))
        {
            com.patrickma.magiccircles.FairyRealmWeather.rainFor(20 * 20);
        }

        // Her ward is no obstacle to the sky.
        if (--this.lightningCooldown <= 0)
        {
            this.lightningCooldown = LIGHTNING_INTERVAL_TICKS;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null)
            {
                bolt.moveTo(this.wrathTarget.getX(), this.wrathTarget.getY(), this.wrathTarget.getZ());
                level.addFreshEntity(bolt);
            }
        }
        if (--this.volleyCooldown <= 0)
        {
            this.volleyCooldown = VOLLEY_INTERVAL_TICKS;
            if (this.hasLineOfSight(this.wrathTarget))
            {
                WispMissileEntity.volley(level, this, this.wrathTarget);
            }
        }
    }

    /** Marks a player for her wrath - told once, the moment it begins. */
    public void condemn(ServerPlayer player)
    {
        if (this.condemned.add(player.getUUID()))
        {
            player.sendSystemMessage(Component.translatable("fairy.magiccircles.queen.wrath")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            player.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
    }

    /** A player struck a fairy - any queen near enough to see it condemns them for it. */
    public static void reportAttack(ServerLevel level, FairyEntity victim, Player attacker)
    {
        if (!(attacker instanceof ServerPlayer player))
        {
            return;
        }
        for (FairyQueenEntity queen : level.getEntitiesOfClass(FairyQueenEntity.class, victim.getBoundingBox().inflate(PRESENCE_RADIUS)))
        {
            queen.condemn(player);
        }
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (this.level() instanceof ServerLevel level)
        {
            FairyCourt.onQueenDied(level, this);
        }
    }

    /**
     * Whether today is a court day. She holds court one whole day and roams the next, turn and turn
     * about - counted in the overworld's own days, the ones a sleeping player skips through (with the
     * daylight cycle stopped, in the game's running clock instead).
     */
    public static boolean isCourtDay(ServerLevel level)
    {
        ServerLevel overworld = level.getServer().overworld();
        long time = overworld.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)
                ? overworld.getDayTime() : overworld.getGameTime();
        return Math.floorMod(time / 24000L, 2L) == 0L;
    }

    /**
     * Holding court: on a court day (see {@link #isCourtDay}) she flies home to the Fairy Court and
     * sits her throne the whole day through, watching the way in while her people come and go (see
     * {@code FairyEntity.AttendCourtGoal}); the day after, she is abroad from dawn to dawn. She rises
     * the moment she is needed - when someone earns her wrath, or strikes at her - and goes back to
     * her throne once it is dealt with, if it is still a court day.
     *
     * <p>The way home is planned, not searched for (see {@link FairyThroneRoom#routeTo}): round the
     * outside of the tree to the court's own flight line, in along it, through the arch and up to
     * the dais - flown by {@link CourtFlight}, so however far off she was, she always arrives.
     */
    private static final class HoldCourtGoal extends Goal
    {
        private static final int CHECK_TICKS = 40;
        /** A last guard against a route that somehow never ends - CourtFlight always arrives well before this. */
        private static final int JOURNEY_LIMIT = 20 * 120;
        private static final double SPEED = 1.2;

        private final FairyQueenEntity queen;
        private final CourtFlight flight;
        private int check;
        private int journeyTicks;

        HoldCourtGoal(FairyQueenEntity queen)
        {
            this.queen = queen;
            this.flight = new CourtFlight(queen);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (!this.queen.atHome() || this.queen.isOccupied() || --this.check > 0)
            {
                return false;
            }
            this.check = CHECK_TICKS;
            ServerLevel level = (ServerLevel) this.queen.level();
            return isCourtDay(level) && FairyThroneRoom.throneStands(level);
        }

        @Override
        public void start()
        {
            this.journeyTicks = 0;
            this.flight.plan(FairyThroneRoom.routeTo(this.queen.position(), FairyThroneRoom.APPROACH));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            if (this.queen.isSeated())
            {
                this.queen.pinToThrone();
                return;
            }
            this.journeyTicks++;
            if (this.flight.tick(SPEED))
            {
                this.queen.sitOnThrone();
            }
        }

        @Override
        public boolean canContinueToUse()
        {
            if (this.queen.isOccupied() || !isCourtDay((ServerLevel) this.queen.level()))
            {
                return false;
            }
            return this.queen.isSeated() || this.journeyTicks < JOURNEY_LIMIT;
        }

        @Override
        public void stop()
        {
            this.queen.standFromThrone();
            this.queen.getNavigation().stop();
        }
    }

    /** Hunting whoever she has condemned: hanging above them at a distance, never closing in, never letting go. */
    private static final class CourtWrathGoal extends Goal
    {
        private final FairyQueenEntity queen;

        CourtWrathGoal(FairyQueenEntity queen)
        {
            this.queen = queen;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            return this.queen.wrathTarget != null && !FairyWard.isWarded(this.queen);
        }

        @Override
        public boolean canContinueToUse()
        {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            ServerPlayer target = this.queen.wrathTarget;
            if (target == null)
            {
                return;
            }
            Vec3 away = new Vec3(this.queen.getX() - target.getX(), 0.0, this.queen.getZ() - target.getZ());
            if (away.lengthSqr() < 1.0E-4)
            {
                away = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 spot = target.position().add(away.normalize().scale(KEEP_DISTANCE)).add(0.0, HOVER_ABOVE, 0.0);
            this.queen.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (this.queen.clearLineTo(spot))
            {
                this.queen.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.2);
            }
            else if (this.queen.getNavigation().isDone())
            {
                this.queen.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.2);
            }
        }
    }
}
