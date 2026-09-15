package com.patrickma.magiccircles.entity;

import com.patrickma.magiccircles.CommandedSpellCasting;
import com.patrickma.magiccircles.FairyWard;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.limbo.LimboRegistry;
import com.patrickma.magiccircles.limbo.Veil;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.registry.ModItems;
import com.patrickma.magiccircles.registry.ModSounds;
import com.patrickma.magiccircles.ritual.HeartSpell;
import com.patrickma.magiccircles.worldgen.WorldTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * The Faye themselves - or what the Fairy Realm still has of them. Human-shaped, drawn with the
 * player's own model and animations (see {@code client/FairyRenderer}), a woman or a man (see
 * {@link #isFemale}), and never without their wings.
 *
 * <p><b>Flight.</b> Fairies live in the World Tree. Most of the time they drift between open pockets
 * of air among its branches ({@link DriftWithinTreeGoal}) - always a spot they can actually reach,
 * checked with a real flight path first rather than aimed at blindly - and now and then settle on a
 * branch for a while. Every so often one goes out and flies a lap of the whole tree ({@link
 * CircleTheTreeGoal}), rising and dipping as it goes, finding its way round anything in the way and
 * giving up on a waypoint rather than grinding against a branch. Taken outside the Fairy Realm, with
 * no tree to keep to, they just wander the sky.
 *
 * <p><b>Magic.</b> Each fairy carries a Heartstone commanded with one spell (see {@link
 * #giveHeartstone}) and casts it exactly the way a player casting from a commanded stone does
 * ({@link CommandedSpellCasting#cast}), when the moment suits it: wards and storms against monsters,
 * mending when hurt, cleansing when poisoned or burning, flowers and bloom when at ease, and a little
 * experience as a gift to anyone who comes close. The stone recharges on its own, fastest at home.
 *
 * <p>A fairy that dies crosses over, the same as a pet or a villager (see {@code
 * limbo/DeathLimboManager#crossesOver}); what crosses does no magic in the living world.
 */
public class FairyEntity extends PathfinderMob implements FlyingAnimal
{
    private static final EntityDataAccessor<Boolean> DATA_FEMALE =
            SynchedEntityData.defineId(FairyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(FairyEntity.class, EntityDataSerializers.INT);

    /** The colours a fairy can be - the Wellspring's own. Black is the dark rite's, and only the queen carries it. */
    public static final RuneColor[] COLORS = {RuneColor.BLUE, RuneColor.GOLD, RuneColor.PURPLE, RuneColor.RED, RuneColor.GREEN};

    private static final int FIRST_VOLLEY_TICKS = 20;
    private static final int VOLLEY_INTERVAL_TICKS = 20 * 4;
    private static final double VOLLEY_RANGE = 24.0;
    private static final int FLEE_TICKS = 100;

    private static final int MAGIC_CHECK_INTERVAL_TICKS = 20;
    /** Mana back per second - quicker at home, near the Wellspring that feeds them. */
    private static final int MANA_REGEN_AT_HOME = 4;
    private static final int MANA_REGEN_ABROAD = 1;
    private static final int WARD_COOLDOWN_TICKS = 20 * 30;
    private static final int GENTLE_COOLDOWN_TICKS = 20 * 60 * 3;
    private static final double THREAT_RADIUS = 12.0;
    private static final double GIFT_RADIUS = 8.0;
    /** Per second, once a gentle spell is ready - so a fairy at ease casts one now and then, not the moment it can. */
    private static final float GENTLE_CHANCE = 0.08f;
    private static final float MAX_LEAN_DEGREES = 40.0f;
    private static final float HEARTSTONE_DROP_CHANCE = 0.1f;

    /** Spells a fairy casts in its own defence - the rest are for when it is at ease. */
    private static final EnumSet<HeartSpell> WARDS = EnumSet.of(HeartSpell.SHIELD, HeartSpell.TEMPEST_WARD,
            HeartSpell.STORM, HeartSpell.HEAL, HeartSpell.VITAL_SURGE, HeartSpell.CLEANSING_RAIN);

    private int spellCooldown = 200;
    private int magicCheck;
    /** Ticks left sitting on a branch - see {@link #startResting}. */
    private int restTicks;
    /** The one thing this fairy sells - see {@link #tradeId}. */
    private int tradeId = -1;

    /** Who this fairy is fighting - whoever last struck it - and how long it has left to get away from them. */
    @Nullable
    private java.util.UUID foe;
    private int fleeTicks;
    private int volleyCooldown;
    /** Where it hovers while its ward stands - the ward doesn't move with it. */
    @Nullable
    private Vec3 wardAnchor;

    // Client-only: how far forward the body leans in flight, eased tick to tick.
    private float leanO;
    private float lean;

    public FairyEntity(EntityType<? extends FairyEntity> type, Level level)
    {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level)
    {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FleeAttackerGoal(this));
        this.goalSelector.addGoal(2, new AttendCourtGoal(this));
        this.goalSelector.addGoal(3, new PerchOnBranchGoal(this));
        this.goalSelector.addGoal(4, new CircleTheTreeGoal(this));
        this.goalSelector.addGoal(5, new DriftWithinTreeGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0)
        {
            @Override
            public boolean canUse()
            {
                // Only with no tree to keep to - at home, the tree goals decide everything.
                return !FairyEntity.this.atHome() && !FairyEntity.this.isOccupied() && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_FEMALE, false);
        this.entityData.define(DATA_COLOR, 0);
    }

    public boolean isFemale()
    {
        return this.entityData.get(DATA_FEMALE);
    }

    public void setFemale(boolean female)
    {
        this.entityData.set(DATA_FEMALE, female);
    }

    public RuneColor color()
    {
        return COLORS[Math.floorMod(this.entityData.get(DATA_COLOR), COLORS.length)];
    }

    public void setColor(int index)
    {
        this.entityData.set(DATA_COLOR, index);
    }

    /** Only the Fairy Queen is. */
    public boolean isQueen()
    {
        return false;
    }

    /** Fighting or fleeing - too busy for flying about the tree. A soul waiting at court goes nowhere at all. */
    protected boolean isOccupied()
    {
        return FairyWard.isWarded(this) || this.fleeTicks > 0 || isSoulAtCourt();
    }

    /** A fairy's ghost, standing in line in the Fairy Court until the queen gives it life back - see {@code FairyCourt#receiveSoul}. */
    public boolean isSoulAtCourt()
    {
        return LimboRegistry.isCreatureGhost(this) && this.getPersistentData().contains(com.patrickma.magiccircles.FairyCourt.SOUL_LINE_TAG);
    }

    /** Not with whoever it's fighting. */
    public boolean willTradeWith(Player player)
    {
        return this.foe == null || !this.foe.equals(player.getUUID());
    }

    /** The one thing this fairy has to sell (see {@link com.patrickma.magiccircles.FairyTrades}). Each has its own - ask around. */
    public int tradeId()
    {
        if (this.tradeId < 0)
        {
            this.tradeId = com.patrickma.magiccircles.FairyTrades.randomFairyTrade(this.random);
        }
        return this.tradeId;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag)
    {
        setFemale(this.random.nextBoolean());
        setColor(this.random.nextInt(COLORS.length));
        this.tradeId = com.patrickma.magiccircles.FairyTrades.randomFairyTrade(this.random);
        giveHeartstone();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /** A full Heartstone, commanded with one spell - anything a ring can cast except Mana Font, which no stone can hold. */
    private void giveHeartstone()
    {
        HeartSpell[] spells = Arrays.stream(HeartSpell.values()).filter(spell -> spell != HeartSpell.MANA_FONT)
                .toArray(HeartSpell[]::new);
        ItemStack stone = new ItemStack(ModItems.HEARTSTONE.get());
        HeartstoneItem.setMana(stone, HeartstoneItem.MAX_MANA);
        HeartstoneItem.setCommandedSpell(stone, spells[this.random.nextInt(spells.length)]);
        this.setItemSlot(EquipmentSlot.MAINHAND, stone);
        this.setDropChance(EquipmentSlot.MAINHAND, HEARTSTONE_DROP_CHANCE);
    }

    // ------------------------------------------------------------------
    // Flight
    // ------------------------------------------------------------------

    @Override
    public boolean isFlying()
    {
        return !this.onGround();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source)
    {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos)
    {
        // Wings - there is no such thing as a fall.
    }

    /** In flight the legs hang still under the wings instead of walking through the air. */
    @Override
    public void calculateEntityAnimation(boolean includeHeight)
    {
        if (this.isFlying())
        {
            this.walkAnimation.update(0.0f, 0.4f);
        }
        else
        {
            super.calculateEntityAnimation(includeHeight);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    boolean atHome()
    {
        return this.level().dimension().equals(ModDimensions.FAIRY_REALM);
    }

    /** Whether nothing solid lies on the straight line from this fairy's eyes to {@code target}. */
    boolean clearLineTo(Vec3 target)
    {
        return this.level().clip(new ClipContext(this.getEyePosition(), target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Within the tree's own reach - among its branches, not out over the meadow. */
    static boolean inTree(BlockPos pos)
    {
        double dx = pos.getX() + 0.5 - WorldTree.CENTER_X;
        double dz = pos.getZ() + 0.5 - WorldTree.CENTER_Z;
        return dx * dx + dz * dz <= Mth.square(WorldTree.maxRadius() + 2)
                && pos.getY() >= WorldTree.groundY() && pos.getY() <= WorldTree.topY() + 2;
    }

    /**
     * Settles on the branch it just reached for a while - the wings fold and it simply sits, long
     * enough for anyone who wants a word to reach it (see {@link PerchOnBranchGoal}).
     */
    protected void startResting()
    {
        this.restTicks = 20 * 8 + this.random.nextInt(20 * 12);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(false);
    }

    /** Whether this fairy's wingbeat has been started on this client - see client/WingFlutterSound. */
    private boolean flutterStarted;

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            if (!this.flutterStarted)
            {
                this.flutterStarted = true;
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.patrickma.magiccircles.client.WingFlutterSound.startFor(this));
            }
            this.leanO = this.lean;
            double dx = this.getX() - this.xo;
            double dz = this.getZ() - this.zo;
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            float target = this.isFlying() ? Mth.clamp(speed * 90.0f, 0.0f, MAX_LEAN_DEGREES) : 0.0f;
            this.lean += (target - this.lean) * 0.2f;
        }
    }

    /** How far forward the body leans, in degrees - faster flight, deeper lean, like a glider on an elytra. */
    public float lean(float partialTick)
    {
        return Mth.lerp(partialTick, this.leanO, this.lean);
    }

    // ------------------------------------------------------------------
    // Magic
    // ------------------------------------------------------------------

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        if (isSoulAtCourt())
        {
            com.patrickma.magiccircles.FairyCourt.holdInLine(this);
            return;
        }
        if (this.restTicks > 0)
        {
            this.restTicks--;
        }
        tickCombat((ServerLevel) this.level());
        if (--this.magicCheck <= 0)
        {
            this.magicCheck = MAGIC_CHECK_INTERVAL_TICKS;
            tickMagic((ServerLevel) this.level());
        }
    }

    private void tickMagic(ServerLevel level)
    {
        ItemStack stone = this.getMainHandItem();
        HeartSpell spell = stone.getItem() instanceof HeartstoneItem ? HeartstoneItem.getCommandedSpell(stone) : null;
        if (spell == null)
        {
            return;
        }
        int mana = HeartstoneItem.getMana(stone);
        if (mana < HeartstoneItem.MAX_MANA)
        {
            HeartstoneItem.setMana(stone, mana + (atHome() ? MANA_REGEN_AT_HOME : MANA_REGEN_ABROAD));
        }

        // Nothing from the other side reaches the living - a fairy's ghost casts nothing.
        if (LimboRegistry.isCreatureGhost(this))
        {
            return;
        }
        if (this.spellCooldown > 0)
        {
            this.spellCooldown -= MAGIC_CHECK_INTERVAL_TICKS;
            return;
        }
        if (HeartstoneItem.getMana(stone) < spell.manaCost() || !wantsToCast(level, spell))
        {
            return;
        }

        HeartstoneItem.setMana(stone, HeartstoneItem.getMana(stone) - spell.manaCost());
        CommandedSpellCasting.cast(level, this, spell);
        this.swing(InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.2, this.getZ(), 14, 0.4, 0.5, 0.4, 0.04);
        // Through the fairy itself, so a ghost fairy's spell is heard only by those who can see it.
        this.playSound(ModSounds.FAIRY_CAST.get(), 1.0f, 1.0f);
        this.spellCooldown = WARDS.contains(spell) ? WARD_COOLDOWN_TICKS : GENTLE_COOLDOWN_TICKS;
    }

    /** Whether now is a moment this particular spell is for. */
    private boolean wantsToCast(ServerLevel level, HeartSpell spell)
    {
        boolean threatened = recentlyHurt() || monstersNearby(level);
        return switch (spell)
        {
            case SHIELD, TEMPEST_WARD, STORM -> threatened;
            case HEAL -> this.getHealth() < this.getMaxHealth() * 0.7f;
            case VITAL_SURGE -> threatened || this.getHealth() < this.getMaxHealth() * 0.7f;
            case CLEANSING_RAIN -> this.isOnFire() || this.getActiveEffects().stream()
                    .anyMatch(effect -> effect.getEffect().getCategory() == MobEffectCategory.HARMFUL);
            case XP -> !threatened && this.random.nextFloat() < GENTLE_CHANCE && playerNearby(level);
            case FLOWERS, BLOOM_OF_LIFE, VERDANT_HARVEST -> !threatened && this.random.nextFloat() < GENTLE_CHANCE
                    && nearGround(level);
            default -> false;
        };
    }

    private boolean recentlyHurt()
    {
        return this.getLastHurtByMob() != null && this.tickCount - this.getLastHurtByMobTimestamp() < 200;
    }

    private boolean monstersNearby(ServerLevel level)
    {
        return !level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(THREAT_RADIUS),
                mob -> mob instanceof Enemy && mob.isAlive() && !Veil.isBehindVeil(mob)).isEmpty();
    }

    private boolean playerNearby(ServerLevel level)
    {
        Player player = level.getNearestPlayer(this, GIFT_RADIUS);
        return player != null && !player.isSpectator();
    }

    /** Close enough to the ground for flowers to take - there's no point blooming thin air. */
    private boolean nearGround(ServerLevel level)
    {
        BlockPos.MutableBlockPos cursor = this.blockPosition().mutable();
        for (int i = 0; i < 4; i++)
        {
            cursor.move(Direction.DOWN);
            if (!level.getBlockState(cursor).isAir())
            {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Talking, and fighting
    // ------------------------------------------------------------------

    /**
     * Right-clicking a fairy opens a conversation - the server decides what kind (see {@code
     * FairyTrades#openDialogue}). A fairy's ghost has nothing to say to the living.
     */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (LimboRegistry.isCreatureGhost(this))
        {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
        {
            this.getNavigation().stop();
            this.getLookControl().setLookAt(player, 30.0f, 30.0f);
            com.patrickma.magiccircles.FairyTrades.openDialogue(serverPlayer, this);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && this.level() instanceof ServerLevel level && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof FairyEntity) && !LimboRegistry.isCreatureGhost(this))
        {
            onAttacked(level, attacker);
        }
        return hurt;
    }

    /**
     * Struck: up goes the ward (see {@link FairyWard}), and whoever struck becomes the one this fairy
     * fights - with volleys of wisps from inside it. Any Fairy Queen nearby takes note.
     */
    protected void onAttacked(ServerLevel level, LivingEntity attacker)
    {
        this.foe = attacker.getUUID();
        if (!FairyWard.isWarded(this))
        {
            this.wardAnchor = this.position();
            FairyWard.raise(level, this);
            this.volleyCooldown = FIRST_VOLLEY_TICKS;
        }
        if (attacker instanceof Player player)
        {
            FairyQueenEntity.reportAttack(level, this, player);
        }
    }

    /** From {@link FairyWard}, the moment the ward comes down. */
    public void onWardLifted()
    {
        this.wardAnchor = null;
        if (this.foe != null && fleesWhenWardLifts())
        {
            this.fleeTicks = FLEE_TICKS;
        }
    }

    /** An ordinary fairy takes the chance to get away; the queen doesn't. */
    protected boolean fleesWhenWardLifts()
    {
        return true;
    }

    private void tickCombat(ServerLevel level)
    {
        if (FairyWard.isWarded(this))
        {
            // Holding still inside its ward - the ward stays where it was raised.
            this.getNavigation().stop();
            if (this.wardAnchor != null)
            {
                this.getMoveControl().setWantedPosition(this.wardAnchor.x, this.wardAnchor.y, this.wardAnchor.z, 0.5);
            }
            LivingEntity target = foe(level);
            // The queen keeps her own time for volleys - see FairyQueenEntity.
            if (!isQueen() && target != null && --this.volleyCooldown <= 0)
            {
                this.volleyCooldown = VOLLEY_INTERVAL_TICKS;
                if (this.hasLineOfSight(target) && this.distanceTo(target) <= VOLLEY_RANGE)
                {
                    WispMissileEntity.volley(level, this, target);
                }
            }
        }
        else if (this.fleeTicks > 0 && --this.fleeTicks == 0)
        {
            this.foe = null;
        }
    }

    /** Whoever this fairy is fighting, if they are still there to be fought. */
    @Nullable
    protected LivingEntity foe(ServerLevel level)
    {
        if (this.foe == null)
        {
            return null;
        }
        net.minecraft.world.entity.Entity entity = level.getEntity(this.foe);
        if (entity instanceof LivingEntity living && living.isAlive() && !Veil.isBehindVeil(living)
                && !(living instanceof Player player && (player.isCreative() || player.isSpectator())))
        {
            return living;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Voice, and keeping
    // ------------------------------------------------------------------

    // Their own voice (see tools/gen_sounds.py): chatter to themselves, a greeting when spoken to
    // (FairyTrades#openDialogue), and the beat of their wings in flight (client/WingFlutterSound).

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.FAIRY_CHATTER.get();
    }

    /** A tree full of fairies all chattering at the usual rate would never be quiet. */
    @Override
    public int getAmbientSoundInterval()
    {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.FAIRY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.FAIRY_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Female", isFemale());
        tag.putInt("SpellCooldown", this.spellCooldown);
        tag.putInt("Color", this.entityData.get(DATA_COLOR));
        tag.putInt("Trade", tradeId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        setFemale(tag.getBoolean("Female"));
        this.spellCooldown = tag.getInt("SpellCooldown");
        setColor(tag.getInt("Color"));
        this.tradeId = tag.contains("Trade") ? tag.getInt("Trade") : -1;
    }

    // ------------------------------------------------------------------
    // Goals
    // ------------------------------------------------------------------

    /** Once its ward comes down, an ordinary fairy gets away - up and off, away from whoever it was fighting. */
    private static final class FleeAttackerGoal extends Goal
    {
        private static final double FLEE_DISTANCE = 20.0;
        private static final double FLEE_RISE = 6.0;

        private final FairyEntity fairy;

        FleeAttackerGoal(FairyEntity fairy)
        {
            this.fairy = fairy;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            return this.fairy.fleeTicks > 0 && !FairyWard.isWarded(this.fairy);
        }

        @Override
        public void start()
        {
            Vec3 away = Vec3.ZERO;
            if (this.fairy.level() instanceof ServerLevel level)
            {
                LivingEntity foe = this.fairy.foe(level);
                if (foe != null)
                {
                    away = this.fairy.position().subtract(foe.position());
                }
            }
            away = new Vec3(away.x, 0.0, away.z);
            if (away.lengthSqr() < 1.0E-4)
            {
                double angle = this.fairy.getRandom().nextDouble() * Math.PI * 2.0;
                away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            }
            Vec3 target = this.fairy.position().add(away.normalize().scale(FLEE_DISTANCE)).add(0.0, FLEE_RISE, 0.0);
            this.fairy.getNavigation().moveTo(target.x, target.y, target.z, 1.7);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.fairy.fleeTicks > 0 && !this.fairy.getNavigation().isDone();
        }

        @Override
        public void stop()
        {
            this.fairy.fleeTicks = 0;
            this.fairy.foe = null;
            this.fairy.getNavigation().stop();
        }
    }

    /**
     * Attending court: while the queen sits her throne, fairies from anywhere nearby come and take
     * their places in the Fairy Court - three on the floor along the runner, the rest on ledges cut
     * into the trunk above, all turned to her (see {@code worldgen/FairyThroneRoom#courtierPlace}).
     * Each flies there by the court's own route ({@link CourtFlight}), stays a few minutes, and goes
     * back to its own life for a while before it comes again - so on a court day her people come
     * and go rather than standing there from dawn to dawn. Only as many come as there are places,
     * and all of them leave the moment she rises.
     */
    private static final class AttendCourtGoal extends Goal
    {
        private static final double SUMMON_RADIUS = 64.0;
        private static final int CHECK_TICKS = 40;
        private static final int MIN_STAY = 20 * 60 * 3;
        private static final int MAX_STAY = 20 * 60 * 6;
        private static final int MIN_AWAY = 20 * 60 * 3;
        private static final int MAX_AWAY = 20 * 60 * 8;
        private static final double SPEED = 1.3;

        private final FairyEntity fairy;
        private final CourtFlight flight;
        private int check;
        private int place = -1;
        private boolean standing;
        private int stayTicks;
        @Nullable
        private FairyQueenEntity queen;

        AttendCourtGoal(FairyEntity fairy)
        {
            this.fairy = fairy;
            this.flight = new CourtFlight(fairy);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (this.fairy.isQueen() || !this.fairy.atHome() || LimboRegistry.isCreatureGhost(this.fairy) || this.fairy.isOccupied()
                    || --this.check > 0)
            {
                return false;
            }
            this.check = CHECK_TICKS + this.fairy.getRandom().nextInt(CHECK_TICKS);
            ServerLevel level = (ServerLevel) this.fairy.level();
            List<FairyQueenEntity> queens = level.getEntitiesOfClass(FairyQueenEntity.class,
                    this.fairy.getBoundingBox().inflate(SUMMON_RADIUS), queen -> queen.isAlive() && queen.isSeated());
            if (queens.isEmpty())
            {
                return false;
            }
            this.place = WorldTreeCourt.claim(level, this.fairy);
            if (this.place < 0)
            {
                return false;
            }
            this.queen = queens.get(0);
            return true;
        }

        @Override
        public void start()
        {
            this.standing = false;
            this.stayTicks = MIN_STAY + this.fairy.getRandom().nextInt(MAX_STAY - MIN_STAY);
            this.fairy.restTicks = 0;
            this.fairy.setNoGravity(true);
            this.flight.plan(com.patrickma.magiccircles.worldgen.FairyThroneRoom.routeTo(this.fairy.position(),
                    com.patrickma.magiccircles.worldgen.FairyThroneRoom.courtierPlace(this.place)));
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            if (!this.standing && this.flight.tick(SPEED))
            {
                this.standing = true;
            }
            if (this.standing)
            {
                this.stayTicks--;
                stand();
            }
        }

        /** In place, wings folded, body turned the way its place faces and head turned to the throne. */
        private void stand()
        {
            Vec3 spot = com.patrickma.magiccircles.worldgen.FairyThroneRoom.courtierPlace(this.place);
            float yaw = com.patrickma.magiccircles.worldgen.FairyThroneRoom.courtierYaw(this.place);
            this.fairy.getNavigation().stop();
            this.fairy.setNoGravity(false);
            this.fairy.setPos(spot.x, spot.y, spot.z);
            this.fairy.setDeltaMovement(Vec3.ZERO);
            this.fairy.setYRot(yaw);
            this.fairy.yBodyRot = yaw;
            if (this.queen != null)
            {
                this.fairy.getLookControl().setLookAt(this.queen, 30.0f, 30.0f);
            }
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.stayTicks > 0 && this.queen != null && this.queen.isAlive() && this.queen.isSeated() && this.fairy.atHome()
                    && !this.fairy.isOccupied();
        }

        @Override
        public void stop()
        {
            WorldTreeCourt.release(this.fairy);
            if (this.standing)
            {
                // Its turn at court is done - some while yet before it comes again.
                this.check = MIN_AWAY + this.fairy.getRandom().nextInt(MAX_AWAY - MIN_AWAY);
            }
            this.place = -1;
            this.queen = null;
            this.standing = false;
            this.fairy.setNoGravity(true);
            this.fairy.getNavigation().stop();
        }
    }

    /** The court's places, as the fairies' goals see them - see {@code worldgen/FairyThroneRoom}. */
    private static final class WorldTreeCourt
    {
        static int claim(ServerLevel level, FairyEntity fairy)
        {
            return com.patrickma.magiccircles.worldgen.FairyThroneRoom.claimCourtierPlace(level, fairy.getUUID());
        }

        static void release(FairyEntity fairy)
        {
            com.patrickma.magiccircles.worldgen.FairyThroneRoom.releaseCourtierPlace(fairy.getUUID());
        }
    }

    /**
     * Coming down to sit: every half minute or so, a fairy with nothing else to do picks a branch
     * nearby - a spot of open air with something solid under it, inside the tree, that it can
     * actually fly to - goes to it, and settles there for a good while (see {@link #startResting}).
     * On a branch it can be walked up to and spoken with; on the wing it never could be. Not the
     * queen, who only ever sits her throne.
     */
    private static final class PerchOnBranchGoal extends Goal
    {
        private static final int MIN_COOLDOWN = 20 * 20;
        private static final int MAX_COOLDOWN = 20 * 50;
        private static final int SEARCH_REACH = 12;
        private static final int ATTEMPTS = 40;
        private static final double SPEED = 1.0;
        private static final int MAX_RUN_TICKS = 200;
        private static final double SETTLE_DISTANCE_SQR = 1.5;

        private final FairyEntity fairy;
        @Nullable
        private BlockPos target;
        private int cooldown = 20 * 5;
        private int runTicks;

        PerchOnBranchGoal(FairyEntity fairy)
        {
            this.fairy = fairy;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (!this.fairy.atHome() || this.fairy.isQueen() || this.fairy.restTicks > 0 || this.fairy.isOccupied()
                    || --this.cooldown > 0)
            {
                return false;
            }
            this.cooldown = MIN_COOLDOWN + this.fairy.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
            this.target = findBranch();
            return this.target != null;
        }

        @Nullable
        private BlockPos findBranch()
        {
            ServerLevel level = (ServerLevel) this.fairy.level();
            RandomSource random = this.fairy.getRandom();
            for (int attempt = 0; attempt < ATTEMPTS; attempt++)
            {
                BlockPos candidate = this.fairy.blockPosition().offset(
                        random.nextInt(SEARCH_REACH * 2 + 1) - SEARCH_REACH,
                        random.nextInt(13) - 6,
                        random.nextInt(SEARCH_REACH * 2 + 1) - SEARCH_REACH);
                BlockPos below = candidate.below();
                if (!inTree(candidate) || !level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()
                        || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                {
                    continue;
                }
                Path path = this.fairy.getNavigation().createPath(candidate, 0);
                if (path != null && path.canReach())
                {
                    return candidate;
                }
            }
            return null;
        }

        @Override
        public void start()
        {
            this.runTicks = 0;
            this.fairy.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, SPEED);
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            this.runTicks++;
            Vec3 seat = Vec3.atBottomCenterOf(this.target);
            if (this.fairy.position().distanceToSqr(seat) < SETTLE_DISTANCE_SQR)
            {
                this.fairy.setPos(seat.x, seat.y, seat.z);
                this.fairy.startResting();
            }
            else if (this.fairy.getNavigation().isDone())
            {
                this.fairy.getNavigation().moveTo(seat.x, seat.y, seat.z, SPEED);
            }
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.fairy.restTicks <= 0 && this.runTicks < MAX_RUN_TICKS && !this.fairy.isOccupied();
        }

        @Override
        public void stop()
        {
            this.fairy.getNavigation().stop();
            this.target = null;
        }
    }

    /**
     * Home life: from one pocket of open air among the branches to another nearby, and sometimes
     * down onto a branch to sit a while. Every spot is checked first - open air for the whole body,
     * inside the tree, and with a real flight path to it - so a fairy never sets off for somewhere
     * it can't get to and ends up nosing against the trunk.
     */
    private static final class DriftWithinTreeGoal extends Goal
    {
        private static final double SPEED = 1.0;
        private static final int MAX_RUN_TICKS = 200;
        private static final int LOCAL_REACH = 10;
        private static final float PERCH_CHANCE = 0.3f;
        private static final int ATTEMPTS = 16;

        private final FairyEntity fairy;
        @Nullable
        private BlockPos target;
        private boolean perch;
        private int runTicks;

        DriftWithinTreeGoal(FairyEntity fairy)
        {
            this.fairy = fairy;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (!this.fairy.atHome() || this.fairy.restTicks > 0 || this.fairy.isOccupied() || !this.fairy.getNavigation().isDone()
                    || this.fairy.getRandom().nextInt(15) != 0)
            {
                return false;
            }
            this.target = pickSpot();
            return this.target != null;
        }

        @Override
        public void start()
        {
            this.runTicks = 0;
            this.fairy.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, SPEED);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.runTicks < MAX_RUN_TICKS && !this.fairy.getNavigation().isDone() && !this.fairy.isOccupied();
        }

        @Override
        public void tick()
        {
            this.runTicks++;
        }

        @Override
        public void stop()
        {
            if (this.perch && this.target != null && this.fairy.distanceToSqr(Vec3.atBottomCenterOf(this.target)) < 2.25)
            {
                this.fairy.startResting();
            }
            this.fairy.getNavigation().stop();
        }

        @Nullable
        private BlockPos pickSpot()
        {
            ServerLevel level = (ServerLevel) this.fairy.level();
            RandomSource random = this.fairy.getRandom();
            // Somehow outside the tree - back in, anywhere open.
            if (!inTree(this.fairy.blockPosition()))
            {
                this.perch = false;
                return WorldTree.randomOpenAirInTree(level, random, 20);
            }
            boolean wantPerch = random.nextFloat() < PERCH_CHANCE;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++)
            {
                BlockPos candidate = this.fairy.blockPosition().offset(
                        random.nextInt(LOCAL_REACH * 2 + 1) - LOCAL_REACH,
                        random.nextInt(10) - 4,
                        random.nextInt(LOCAL_REACH * 2 + 1) - LOCAL_REACH);
                if (!inTree(candidate) || !level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir())
                {
                    continue;
                }
                BlockPos below = candidate.below();
                boolean branchBelow = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
                // Only insist on a branch for the first half of the tries - then anywhere will do.
                if (wantPerch && !branchBelow && attempt < ATTEMPTS / 2)
                {
                    continue;
                }
                Path path = this.fairy.getNavigation().createPath(candidate, 0);
                if (path == null || !path.canReach())
                {
                    continue;
                }
                this.perch = branchBelow;
                return candidate;
            }
            return null;
        }
    }

    /**
     * A lap of the World Tree - the flight a fairy takes for the joy of it. The course is planned
     * up front as a ring of waypoints just outside the canopy, starting from wherever the fairy is
     * and going round whichever way it likes, rising and dipping twice over the lap. It flies
     * straight at each waypoint while the way is clear and finds a path round anything in the way
     * when it isn't; a waypoint it makes no headway on for a few seconds is abandoned for the next,
     * so nothing ever leaves it stuck against a branch.
     */
    private static final class CircleTheTreeGoal extends Goal
    {
        private static final int MIN_COOLDOWN = 20 * 45;
        private static final int MAX_COOLDOWN = 20 * 150;
        private static final double SPEED = 1.4;
        private static final double REACHED = 2.5;
        private static final int STUCK_TICKS = 60;
        private static final double UNDULATION = 6.0;

        private final FairyEntity fairy;
        private final List<Vec3> waypoints = new ArrayList<>();
        private int index;
        private int cooldown;
        private int stuck;
        private double best;

        CircleTheTreeGoal(FairyEntity fairy)
        {
            this.fairy = fairy;
            this.cooldown = MIN_COOLDOWN + fairy.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            if (!this.fairy.atHome() || this.fairy.restTicks > 0 || this.fairy.isOccupied())
            {
                return false;
            }
            if (--this.cooldown > 0)
            {
                return false;
            }
            this.cooldown = MIN_COOLDOWN + this.fairy.getRandom().nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
            return true;
        }

        @Override
        public void start()
        {
            planLap();
            this.index = 0;
            this.stuck = 0;
            this.best = Double.MAX_VALUE;
        }

        private void planLap()
        {
            this.waypoints.clear();
            RandomSource random = this.fairy.getRandom();
            double radius = WorldTree.maxRadius() + 6.0 + random.nextDouble() * 10.0;
            int count = 10 + random.nextInt(5);
            double direction = random.nextBoolean() ? 1.0 : -1.0;
            double startAngle = Math.atan2(this.fairy.getZ() - WorldTree.CENTER_Z, this.fairy.getX() - WorldTree.CENTER_X);
            double low = WorldTree.groundY() + 8.0;
            double high = WorldTree.topY() + 6.0;
            double base = low + UNDULATION + random.nextDouble() * Math.max(1.0, high - low - 2.0 * UNDULATION);
            for (int i = 1; i <= count; i++)
            {
                double angle = startAngle + direction * Math.PI * 2.0 * i / count;
                double y = Mth.clamp(base + Math.sin(Math.PI * 4.0 * i / count) * UNDULATION, low, high);
                this.waypoints.add(new Vec3(WorldTree.CENTER_X + Math.cos(angle) * radius, y,
                        WorldTree.CENTER_Z + Math.sin(angle) * radius));
            }
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            // A goal that asks to be ticked every tick is still only checked for whether it should
            // stop every other tick - so the tick after the last waypoint is reached can come
            // before the lap is ended. Nothing left to fly to; wait to be stopped.
            if (this.index >= this.waypoints.size())
            {
                return;
            }
            Vec3 target = this.waypoints.get(this.index);
            double distance = this.fairy.position().distanceTo(target);
            if (distance < REACHED)
            {
                advance();
                return;
            }
            if (distance < this.best - 0.1)
            {
                this.best = distance;
                this.stuck = 0;
            }
            else if (++this.stuck > STUCK_TICKS)
            {
                advance();
                return;
            }

            this.fairy.getLookControl().setLookAt(target.x, target.y, target.z);
            if (this.fairy.clearLineTo(target))
            {
                this.fairy.getNavigation().stop();
                this.fairy.getMoveControl().setWantedPosition(target.x, target.y, target.z, SPEED);
            }
            else if (this.fairy.getNavigation().isDone())
            {
                this.fairy.getNavigation().moveTo(target.x, target.y, target.z, SPEED);
            }
        }

        private void advance()
        {
            this.index++;
            this.stuck = 0;
            this.best = Double.MAX_VALUE;
            this.fairy.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.index < this.waypoints.size() && this.fairy.atHome() && !this.fairy.isOccupied();
        }

        @Override
        public void stop()
        {
            this.fairy.getNavigation().stop();
            this.waypoints.clear();
        }
    }
}
