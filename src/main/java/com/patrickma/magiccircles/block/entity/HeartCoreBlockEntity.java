package com.patrickma.magiccircles.block.entity;

import com.patrickma.magiccircles.FairyPortalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.client.ClientCircleWisps;
import com.patrickma.magiccircles.client.ClientHeartWisps;
import com.patrickma.magiccircles.client.ShieldRingWisps;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import com.patrickma.magiccircles.item.HeartstoneItem;
import com.patrickma.magiccircles.registry.ModBlockEntities;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.ritual.HeartSpell;
import com.patrickma.magiccircles.ritual.MagicCircleRitual;
import com.patrickma.magiccircles.ritual.PortalRitual;
import com.patrickma.magiccircles.ritual.ShieldShapeDetector;
import com.patrickma.magiccircles.ritual.WispChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sits where a magic circle's center rune used to be once a Heartstone is used on it (see
 * {@link com.patrickma.magiccircles.item.HeartstoneItem}). Holds the mana that came from that
 * Heartstone, and - as long as its circle's ring of 12 is still intact - takes over the
 * circle's wisps from {@link MagicCircleBlockEntity} and tightens them into an orbit around
 * itself, instead of them wandering loosely over the whole circle. If the ring breaks, the
 * orbit stops; if this block itself is broken (see
 * {@link com.patrickma.magiccircles.block.HeartCoreBlock}), it turns back into a rune of
 * whatever color it originally was - and its wisps go back to wandering the circle - plus a
 * Heartstone item carrying the same mana.
 *
 * <p>Which spell a Fairy Horn use casts depends entirely on the ring's color composition (see
 * {@code HeartCoreBlock#interact}, {@link MagicCircleRitual#detectSpell},
 * {@code HeartSpell}) - unless the ring is actually a {@code PortalRitual} pattern instead, in
 * which case a Fairy Horn use opens a Fairy Realm portal (see {@link #openWaterPortal}) rather than
 * casting any ring-color spell at all. The spells that run over time (storm, shield, the
 * tempest ward, the mana font, the vigor buff) are ticked server-side in {@link #serverTick}
 * and covered by {@link #isSpellActive()}, which also drives {@link ClientHeartWisps}' 6 white
 * "something is happening here" wisps; the rest are one-shot effects with a brief "burst" wisp
 * channel that settles back to a normal orbit on its own.
 */
public class HeartCoreBlockEntity extends BlockEntity
{
    private static final int RING_CHECK_INTERVAL = 10;

    private static final int STORM_SPELL_DURATION_TICKS = 20 * 30;
    private static final int LIGHTNING_MIN_INTERVAL_TICKS = 30;
    private static final int LIGHTNING_MAX_INTERVAL_TICKS = 70;
    // The circle's footprint reaches out to about 2.24 blocks from center (see
    // MagicCircleRitual) - strikes land well outside that ring, never inside it.
    private static final double STRIKE_MIN_RADIUS = 4.0;
    private static final double STRIKE_MAX_RADIUS = 9.0;

    // Shield's mana cost is per-hit-point of damage actually absorbed (1:1), not time-based
    // anymore - a full HeartstoneItem#MAX_MANA (1000) pool takes exactly 1000 hit points of
    // damage before the shield drops, shared across every orb belonging to this heart (each orb's
    // own ShieldOrbEntity#hurt reports its own hit straight to #drainShieldMana - a creeper
    // exploding against several orbs at once drains all of those hits from the same pool, not
    // 1000 per orb).
    private static final double SHIELD_RADIUS = 7.0;

    /** The plain sphere shield's own radius - see {@code client/ShieldRingWisps}, which traces exactly this when a shield has no custom outer wall shape. */
    public static double shieldRadius()
    {
        return SHIELD_RADIUS;
    }
    private static final int SHIELD_NODE_COUNT = 420;
    private static final double SHIELD_REPEL_PADDING = 0.5;
    // A custom outer-shape box's total height (top to bottom) grows with the 2D area the outer
    // loop encloses - a bigger drawn shape makes a taller box - but through its square root, not
    // the raw area directly: area grows with the *square* of a shape's linear size, so scaling
    // height by area 1-to-1 made even a modestly-sized loop (a few hundred enclosed cells) come
    // out clamped all the way to the max height, reading as absurdly tall for what was actually
    // drawn. Scaling by the square root instead ties height to roughly the shape's own radius,
    // which is what "proportional to the area, but much slower" actually looks like in practice.
    // Still clamped to something sane either way: an absurdly large loop shouldn't try to build a
    // box thousands of blocks tall, and an absurdly small one still needs enough headroom to
    // actually stand inside.
    private static final double SHIELD_WALL_HEIGHT_SCALE = 1.5;
    private static final int SHIELD_WALL_MIN_HEIGHT = 6;
    private static final int SHIELD_WALL_MAX_HEIGHT = 250;
    // Orbs are spaced out vertically at roughly this many blocks apart per column - deliberately
    // *less* than one orb's own visual width (each cube is ~1.4-1.6 blocks across, see
    // ShieldOrbModel's own doc comment) so consecutive orbs' edges actually touch/overlap instead
    // of leaving a visible gap between them. 3.0 (this constant's old value) left about a
    // block and a half of open space between each pair - clearly separated floating cubes, not a
    // wall - since it was tuned only to avoid "a handful of separate rings" (spacing far too
    // coarse to look continuous at any density) without ever checking against the orb's own size.
    private static final double SHIELD_WALL_TARGET_ORB_SPACING = 1.3;
    // A hard safety cap on the side columns' *total* orb count regardless of spacing - only
    // reached by a wall tall and wide enough that the target spacing above would otherwise spawn
    // an unreasonable number of entities; it falls back to sparser spacing (shrinking both column
    // count and per-column density together - see #spawnWallOrbs) rather than lag. Raised well
    // above its old 6000 now that the target spacing itself is over twice as dense - at the old
    // cap, most walls would have immediately fallen back to sparser-than-intended anyway.
    private static final long SHIELD_WALL_MAX_TOTAL_ORBS = 40000;
    // How many cells tickWallBoundary's side push scans outward before giving up - comfortably
    // more than the wall footprint is ever actually thick in one direction (normally 1 cell,
    // occasionally 2 where ShieldShapeDetector#closeDiagonalGaps added a corner cell), so a push
    // reliably lands the entity fully clear of the wall in one teleport.
    private static final int WALL_THICKNESS_SCAN_LIMIT = 6;
    // Split between the flat top and flat bottom "lids" - each one a sparse grid across the
    // shape's bounding rectangle (an approximation of its true interior, same spirit as
    // tickWallBoundary's side-push already being convexity-only-ish - see its doc comment).
    private static final int SHIELD_WALL_LID_MAX_ORBS = 400;

    // Shared by Flowers and Verdant Harvest - a random compass direction and distance (not a
    // uniform square) so results read as naturally scattered, closer and farther from the
    // circle, rather than a "band" at one fixed distance/height.
    private static final double GROWTH_MIN_RADIUS = 2.0;
    private static final double GROWTH_MAX_RADIUS = 12.0;
    private static final int GROWTH_ATTEMPTS = 50;
    // Half-size of MagicCircleRitual's 5x5 ring footprint - growAround must never place anything
    // inside this square, corners included. GROWTH_MIN_RADIUS alone doesn't guarantee that: a
    // polar radius of exactly GROWTH_MIN_RADIUS (2.0) can still round to one of the footprint's
    // own corners (distance ~2.83 from center) at a diagonal-ish angle, which is exactly what
    // was happening - a flower landing on a corner fills the one cell hasCompleteRing requires
    // to stay air, making the ring that just cast this spell unable to be recast afterward.
    private static final int CIRCLE_FOOTPRINT_HALF_SIZE = 2;
    private static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
            Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.CORNFLOWER,
    };

    private static final double HEAL_RADIUS = 20.0;
    private static final int HEAL_MAX_WISP_TARGETS = 12;

    private static final int XP_ORB_COUNT = 12;
    private static final int XP_PER_ORB = 7;
    private static final double XP_SCATTER_RADIUS = 3.0;

    private static final double CLEANSING_RADIUS = 12.0;

    private static final int TEMPEST_WARD_DURATION_TICKS = 20 * 45;
    private static final int TEMPEST_STRIKE_MIN_INTERVAL_TICKS = 40;
    private static final int TEMPEST_STRIKE_MAX_INTERVAL_TICKS = 100;

    private static final int MANA_FONT_DURATION_TICKS = 20 * 60;
    private static final int MANA_FONT_INTERVAL_TICKS = 20 * 3;
    private static final int MANA_FONT_AMOUNT = 15;

    private static final int BLOOM_REGEN_DURATION_TICKS = 20 * 10;

    private static final Block[] CROPS = { Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES };
    private static final int VERDANT_XP_ORB_COUNT = 4;

    private static final double VITAL_SURGE_RADIUS = 12.0;
    private static final int VITAL_SURGE_DRAIN_INTERVAL_TICKS = 20 * 60;
    private static final int VITAL_SURGE_DRAIN_AMOUNT = 50;
    private static final int VITAL_SURGE_REAPPLY_INTERVAL_TICKS = 20 * 5;
    private static final int VITAL_SURGE_EFFECT_DURATION_TICKS = 20 * 8;

    public static final int PORTAL_MANA_COST = 500;

    // How long a one-shot spell's "burst" wisp channel (OUTWARD/DOWN/TO_PLAYER/TO_TARGETS)
    // shows before easing back to a normal orbit - purely cosmetic either way.
    private static final int ONE_SHOT_CHANNEL_TICKS = 60;

    // Every spell's actual world effect (flowers appearing, weather starting, orbs spawning,
    // buffs applying, ...) waits this long after casting - one second, long enough for the
    // wisps that start flying the instant the spell is cast (see each spell's own method) to
    // visibly be "on their way" to wherever the effect will land before it actually appears,
    // rather than the effect popping in before the wisps have gone anywhere. Applied uniformly
    // rather than per-spell so nothing here compounds with another spell-specific wait - this
    // replaces what used to be a separate, shorter (10-tick) XP-only delay.
    private static final int SPELL_EFFECT_DELAY_TICKS = 20;

    private static final int[] NO_TARGETS = new int[0];

    private int mana;
    private int age;
    private boolean ringComplete;
    private RuneColor centerColor = RuneColor.BLUE;
    private WispChannel wispChannel = WispChannel.ORBIT;
    private int oneShotChannelTicksRemaining;
    private int[] wispTargetEntityIds = NO_TARGETS;

    private int stormTicksRemaining;
    private int nextLightningIn;

    private boolean shieldActive;
    // The player who cast Shield/Tempest Ward - the only one who can walk through the
    // boundary freely (see #tickShieldBoundary). Null means "nobody's exempt" (e.g. loaded
    // from an old save with no recorded owner) rather than "everybody's exempt".
    @Nullable
    private UUID shieldOwnerUuid;
    // Non-null only when Shield specifically (not Tempest Ward) found a valid outer Purple
    // wall shape to use instead of its usual sphere - see ShieldShapeDetector. Y is always 0 -
    // only the X/Z column matters from here on (see #startShieldSpell), the box's actual height
    // range lives in shieldWallBottomY/shieldWallTopY below.
    @Nullable
    private Set<BlockPos> shieldWallFootprint;
    // The box's flat bottom and flat top - both computed once, at cast time, from the outer
    // loop's average rune height and its enclosed area (see #startShieldSpell). Meaningless
    // unless shieldWallFootprint is also non-null.
    private int shieldWallBottomY;
    private int shieldWallTopY;

    private int tempestTicksRemaining;
    private int tempestNextStrikeIn;
    @Nullable
    private UUID tempestOwnerUuid;

    private int manaFontTicksRemaining;
    private int manaFontNextTickIn;

    // Every spell schedules its actual effect through here (see #scheduleEffect) rather than
    // applying it directly - deliberately never saved to NBT, the same way
    // oneShotChannelTicksRemaining above isn't: a one-second window is short enough that losing
    // a pending effect to an exceedingly rare server crash/restart is an acceptable loss, not
    // worth the complexity of serializing arbitrary pending actions.
    private final List<PendingEffect> pendingEffects = new ArrayList<>();

    private boolean vitalSurgeActive;
    private int vitalSurgeDrainCountdown;
    private int vitalSurgeReapplyCountdown;

    // True once mana has dropped below what even the cheapest spell needs (see
    // HeartSpell#minManaCost) - drives both the ring wisps giving up on orbiting this heart
    // (ClientCircleWisps) and the heart's own white wisps visibly falling into the ground
    // (ClientHeartWisps), rather than either just quietly doing nothing forever.
    private boolean manaDepleted;

    public HeartCoreBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.HEART_CORE.get(), pos, state);
    }

    public int getMana()
    {
        return mana;
    }

    public void setMana(int mana)
    {
        this.mana = Mth.clamp(mana, 0, HeartstoneItem.MAX_MANA);
        setChanged();
        updateManaDepleted();
    }

    /**
     * Zeroes out any leftover mana too small for even the cheapest spell to ever use, and flips
     * {@link #manaDepleted} on the transition either way - crossing below the threshold (wisps
     * give up and fall) or back above it (a future refill, e.g. once Mana Font is actually
     * castable, brings them back) - rather than every single tick the mana happens to sit below
     * it, which would otherwise re-trigger the "just ran dry" animation repeatedly.
     */
    private void updateManaDepleted()
    {
        boolean shouldBeDepleted = mana < HeartSpell.minManaCost();
        if (shouldBeDepleted == manaDepleted)
        {
            return;
        }
        manaDepleted = shouldBeDepleted;
        if (manaDepleted)
        {
            mana = 0;
        }
        syncToClient();
    }

    /** Which color rune this heart replaced - see {@code HeartCoreBlock#destroy}, which reverts to a rune of this color. */
    public RuneColor centerColor()
    {
        return centerColor;
    }

    public void setCenterColor(RuneColor centerColor)
    {
        this.centerColor = centerColor;
        setChanged();
    }

    public int getAge()
    {
        return age;
    }

    /**
     * The reverse of {@link com.patrickma.magiccircles.item.HeartstoneItem#useOn}: instead of a
     * blank Heartstone consuming a complete ring to become this Heart Core, this Heart Core (with
     * its ring still complete and nothing currently running on it) consumes its own ring and
     * itself to become a *commanded* Heartstone - carrying whatever spell that ring's color
     * composition currently reads as - handed straight into {@code hand}. Only reachable while
     * the player has {@link com.patrickma.magiccircles.registry.ModEffects#BLESSED_BY_WELLSPRING}
     * (checked by the caller, {@code HeartCoreBlock#interact}) and empty-handed.
     *
     * <p>{@link HeartSpell#MANA_FONT} refuses here specifically - charging a heart's own mana
     * from a nearby Wellspring makes no sense once there's no heart left to charge, so it's the
     * one spell that can never be commanded this way (a commanded Heartstone can never hold it).
     */
    public InteractionResult absorbRingIntoHeartstone(ServerLevel level, Player player, InteractionHand hand)
    {
        if (isSpellActive())
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.spell_active").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!MagicCircleRitual.hasCompleteRing(level, worldPosition))
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.ring_broken").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        HeartSpell spell = MagicCircleRitual.detectSpell(level, worldPosition);
        if (spell == null)
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.spell_not_recognized").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (spell == HeartSpell.MANA_FONT)
        {
            player.displayClientMessage(Component.translatable("block.magiccircles.heart_core.not_valid_to_command").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        int mana = getMana();
        BlockPos pos = worldPosition.immutable();
        UUID playerId = player.getUUID();

        // The wisps' own head start, same idea SPELL_EFFECT_DELAY_TICKS gives every other spell -
        // a burst aimed roughly at the heart's own floating height (worldPosition + 2, not + 1 -
        // the heart visually floats noticeably higher than one block up) from every rune position
        // at once, so the ring visibly "empties into" the heart before it actually vanishes.
        //
        // Level#sendParticles' xDist/yDist/zDist parameters only ever act as a *directed velocity*
        // when count is exactly 0 (ClientPacketListener#handleParticleEvent: count==0 sends one
        // particle with velocity = maxSpeed * (xDist,yDist,zDist); any nonzero count instead
        // randomizes position/velocity independently of those three, ignoring the direction
        // entirely). An earlier version passed count=1 here, which silently discarded the aimed
        // velocity below and gave every particle a small random jitter instead - the actual cause
        // of "the wisps aren't going into the heartstone."
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5);
        for (int[] offset : MagicCircleRitual.RING_OFFSETS)
        {
            double rx = pos.getX() + 0.5 + offset[0];
            double rz = pos.getZ() + 0.5 + offset[1];
            double ry = pos.getY() + 0.3;
            Vec3 velocity = target.subtract(rx, ry, rz).scale(0.05);
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(centerColor.wispColor(), 1.0f),
                    rx, ry, rz, 0, velocity.x, velocity.y, velocity.z, 1.0);
        }
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);

        scheduleEffect(lvl ->
        {
            Player absorbingPlayer = lvl.getServer().getPlayerList().getPlayer(playerId);
            for (int[] offset : MagicCircleRitual.RING_OFFSETS)
            {
                lvl.setBlockAndUpdate(pos.offset(offset[0], 0, offset[1]), Blocks.AIR.defaultBlockState());
            }
            lvl.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            BlockPos topPos = pos.above();
            if (lvl.getBlockState(topPos).is(com.patrickma.magiccircles.registry.ModBlocks.HEART_CORE_TOP.get()))
            {
                lvl.setBlockAndUpdate(topPos, Blocks.AIR.defaultBlockState());
            }
            removeShieldOrbs(lvl, pos);

            ItemStack stack = new ItemStack(com.patrickma.magiccircles.registry.ModItems.HEARTSTONE.get());
            HeartstoneItem.setMana(stack, mana);
            HeartstoneItem.setCommandedSpell(stack, spell);
            if (absorbingPlayer != null)
            {
                if (!absorbingPlayer.getInventory().add(stack))
                {
                    absorbingPlayer.drop(stack, false);
                }
                lvl.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
            }
        });

        return InteractionResult.CONSUME;
    }

    public boolean isSpellActive()
    {
        return activeSpellCount() > 0;
    }

    /**
     * How many of this heart's 5 prolonged spells are currently running - 0, 1, or 2 in
     * practice, since {@code HeartCoreBlock#interact} refuses a third once two are already
     * active. Nothing about any of these 5 spells' own state depends on any of the others' -
     * each has always had its own dedicated fields/timers rather than sharing one "current
     * spell" slot - which is exactly what makes running two of them at once safe to allow.
     */
    public int activeSpellCount()
    {
        int count = 0;
        if (shieldActive) count++;
        if (tempestTicksRemaining > 0) count++;
        if (stormTicksRemaining > 0) count++;
        if (manaFontTicksRemaining > 0) count++;
        if (vitalSurgeActive) count++;
        return count;
    }

    /** True once a *second* prolonged spell has joined a first - drives the smaller, second ring of white wisps (see {@link com.patrickma.magiccircles.client.ClientHeartWisps#tickSecondary}). */
    public boolean isSecondSpellActive()
    {
        return activeSpellCount() >= 2;
    }

    /** Whether {@code spell} specifically (not just "some prolonged spell") is currently running on this heart - {@code false} for a one-shot spell, which never has "active" state to check. */
    public boolean isSpellTypeActive(HeartSpell spell)
    {
        return switch (spell)
        {
            case SHIELD -> shieldActive;
            case TEMPEST_WARD -> tempestTicksRemaining > 0;
            case STORM -> stormTicksRemaining > 0;
            case MANA_FONT -> manaFontTicksRemaining > 0;
            case VITAL_SURGE -> vitalSurgeActive;
            default -> false;
        };
    }

    /** Called from {@code HeartCoreBlock#interact} once mana and ring completeness are already confirmed. */
    public void startStormSpell(ServerLevel level)
    {
        setWispChannel(WispChannel.UP);
        scheduleEffect(lvl ->
        {
            stormTicksRemaining = STORM_SPELL_DURATION_TICKS;
            nextLightningIn = 0;
            lvl.setWeatherParameters(0, STORM_SPELL_DURATION_TICKS, true, true);
            // setWispChannel already synced the client once, before stormTicksRemaining (and so
            // isSpellActive()) actually flipped true - without a second sync here, once this
            // delayed effect is what actually flips it, ClientHeartWisps' white ring would never
            // find out this spell is active at all.
            setChanged();
            syncToClient();
        });
    }

    /**
     * Spawns the shield boundary and starts its mana drain. Runs until mana runs out or the
     * shield is otherwise removed. Normally a sphere, unless {@code caster} also drew a second,
     * larger enclosed loop of Purple runes somewhere outside the ring (see
     * {@link ShieldShapeDetector}) - if one's found, and it isn't touching the inner ring
     * itself, the shield becomes a flat-topped-and-bottomed box following that shape's X/Z
     * footprint instead, sized by {@link #findValidWallShape}. Either way, only {@code caster}
     * can walk through it freely - see {@link #tickShieldBoundary}.
     */
    public void startShieldSpell(ServerLevel level, Player caster)
    {
        setWispChannel(WispChannel.TRACE_SHIELD);
        scheduleEffect(lvl ->
        {
            shieldActive = true;
            shieldOwnerUuid = caster.getUUID();
            ShieldShapeDetector.WallShape wallShape = findValidWallShape(lvl);
            spawnShieldOrbs(lvl, shieldWallFootprint, shieldWallBottomY, shieldWallTopY, wallShape);
            setChanged();
            syncToClient();
        });
    }

    private void endShieldSpell(ServerLevel level)
    {
        shieldActive = false;
        shieldWallFootprint = null;
        removeShieldOrbs(level, worldPosition);
        refreshWispChannelAfterSpellEnd();
    }

    /**
     * The ring wisps' channel only ever means "is at least one prolonged spell active" - so
     * ending one of possibly two shouldn't drop straight to {@code ORBIT} the way it safely
     * could back when only one spell could ever run at a time. Falls back to {@code UP} if
     * another prolonged spell is still going, all the way to {@code ORBIT} only if this was
     * the last one.
     */
    private void refreshWispChannelAfterSpellEnd()
    {
        setWispChannel(isSpellActive() ? WispChannel.UP : WispChannel.ORBIT);
    }

    /**
     * Looks for a valid outer Purple loop and, if one's found, sets {@link #shieldWallFootprint}
     * (normalized to Y 0 - only the X/Z column matters from here on) and computes the box's flat
     * {@link #shieldWallBottomY}/{@link #shieldWallTopY}: centered on the average height of the
     * loop's own runes (so a loop drawn on a slope still gets one level box, not a terrain-
     * following one), with a total span equal to {@value #SHIELD_WALL_HEIGHT_SCALE} times the
     * *square root* of the loop's enclosed 2D area (roughly its own radius, not its raw area -
     * see {@link #SHIELD_WALL_HEIGHT_SCALE}'s own doc comment for why), clamped to
     * {@value #SHIELD_WALL_MIN_HEIGHT}-{@value #SHIELD_WALL_MAX_HEIGHT} blocks and then kept
     * inside the world's actual build range. Leaves all three fields {@code null}/0 (the sphere
     * fallback) if there's no valid loop.
     */
    @Nullable
    private ShieldShapeDetector.WallShape findValidWallShape(ServerLevel level)
    {
        ShieldShapeDetector.WallShape shape = ShieldShapeDetector.findOuterWallShape(level, worldPosition);
        if (shape == null || ShieldShapeDetector.touchesInnerRing(worldPosition, shape.wallCells))
        {
            shieldWallFootprint = null;
            return null;
        }

        long ySum = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        Set<BlockPos> footprint = new HashSet<>();
        for (BlockPos cell : shape.wallCells)
        {
            ySum += cell.getY();
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minZ = Math.min(minZ, cell.getZ());
            maxZ = Math.max(maxZ, cell.getZ());
            footprint.add(new BlockPos(cell.getX(), 0, cell.getZ()));
        }
        int baseY = (int) Math.round(ySum / (double) shape.wallCells.size());

        int rawHeight = (int) Math.round(SHIELD_WALL_HEIGHT_SCALE * Math.sqrt(shape.area));
        int height = Mth.clamp(rawHeight, SHIELD_WALL_MIN_HEIGHT, SHIELD_WALL_MAX_HEIGHT);
        int halfUp = height / 2;
        int halfDown = height - halfUp;
        int worldMin = level.getMinBuildHeight();
        int worldMax = level.getMaxBuildHeight() - 1;
        int topY = Mth.clamp(baseY + halfUp, worldMin, worldMax);
        int bottomY = Mth.clamp(baseY - halfDown, worldMin, worldMax);

        shieldWallFootprint = footprint;
        shieldWallBottomY = bottomY;
        shieldWallTopY = topY;
        return shape;
    }

    /**
     * Blue+Purple combo: the same unbreakable orb boundary as {@link #startShieldSpell}, but
     * always a sphere (an outer wall shape is only ever looked for by the plain Shield spell -
     * a combo ring isn't "the established magic circle of purple chalk" this is meant to
     * extend), for a fixed 45-second duration rather than a mana drain, and offensive rather
     * than purely passive - while it's up, it periodically strikes lightning at a random
     * hostile mob caught within the sphere's radius.
     */
    public void startTempestWardSpell(ServerLevel level, Player caster)
    {
        setWispChannel(WispChannel.FROM_HEART);
        scheduleEffect(lvl ->
        {
            tempestTicksRemaining = TEMPEST_WARD_DURATION_TICKS;
            tempestNextStrikeIn = 0;
            tempestOwnerUuid = caster.getUUID();
            spawnShieldOrbs(lvl, null, 0, 0, null);
            setChanged();
            syncToClient();
        });
    }

    private void endTempestWardSpell(ServerLevel level)
    {
        tempestTicksRemaining = 0;
        removeShieldOrbs(level, worldPosition);
        refreshWispChannelAfterSpellEnd();
    }

    private void spawnShieldOrbs(ServerLevel level, @Nullable Set<BlockPos> wallFootprint, int bottomY, int topY, @Nullable ShieldShapeDetector.WallShape wallShape)
    {
        if (wallFootprint != null && !wallFootprint.isEmpty())
        {
            spawnWallOrbs(level, wallFootprint, bottomY, topY, wallShape);
        }
        else
        {
            Vec3 center = shieldCenter();
            for (Vec3 point : fibonacciSphere(SHIELD_NODE_COUNT, SHIELD_RADIUS))
            {
                ShieldOrbEntity orb = new ShieldOrbEntity(ModEntities.SHIELD_ORB.get(), level);
                orb.setPos(center.x + point.x, center.y + point.y, center.z + point.z);
                orb.setOwnerPos(worldPosition);
                level.addFreshEntity(orb);
            }
        }
    }

    /**
     * One column of orbs per wall-footprint cell, evenly spaced from the flat bottom up to the
     * flat top, plus a sparse grid of orbs across each of those two planes (over the footprint's
     * bounding rectangle) so the "flat top and bottom enclosing the player" actually reads as a
     * ceiling and floor rather than just an implied one.
     */
    private void spawnWallOrbs(ServerLevel level, Set<BlockPos> wallFootprint, int bottomY, int topY, @Nullable ShieldShapeDetector.WallShape wallShape)
    {
        int[] span = ensureMinWallSpan(bottomY, topY);
        bottomY = span[0];
        topY = span[1];
        int wallHeight = Math.max(1, topY - bottomY);
        int perColumn = Math.max(3, (int) Math.ceil(wallHeight / SHIELD_WALL_TARGET_ORB_SPACING));
        int columnStride = 1;
        long totalOrbs = (long) perColumn * wallFootprint.size();
        if (totalOrbs > SHIELD_WALL_MAX_TOTAL_ORBS)
        {
            // Shrink *both* how many columns get used and how many orbs sit in each one, by the
            // same factor - not just perColumn alone, which for a big/wide wall could collapse
            // it all the way down to its own floor of 3 while every single footprint cell still
            // got its own column. Three points spanning the *entire* height, repeated at every
            // column, is exactly "a handful of separate rings" instead of a wall - shrinking the
            // column count too means what's left stays proportioned like a wall, just a coarser
            // one, rather than the vertical dimension alone being sacrificed to stay in budget.
            double scale = Math.sqrt(totalOrbs / (double) SHIELD_WALL_MAX_TOTAL_ORBS);
            perColumn = Math.max(3, (int) Math.round(perColumn / scale));
            columnStride = Math.max(1, (int) Math.round(scale));
        }
        // Spread perColumn+1 orbs evenly from the flat bottom to the flat top - not a fixed
        // 1-block spacing, which only ever covered the bottom perColumn blocks of a wall taller
        // than that and left the rest of the height completely open.
        double spacing = wallHeight / ((double) perColumn);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int columnIndex = 0;
        for (BlockPos cell : wallFootprint)
        {
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minZ = Math.min(minZ, cell.getZ());
            maxZ = Math.max(maxZ, cell.getZ());

            if (columnIndex++ % columnStride != 0)
            {
                continue;
            }
            for (int i = 0; i <= perColumn; i++)
            {
                double y = bottomY + 0.5 + i * spacing;
                ShieldOrbEntity orb = new ShieldOrbEntity(ModEntities.SHIELD_ORB.get(), level);
                orb.setPos(cell.getX() + 0.5, y, cell.getZ() + 0.5);
                orb.setOwnerPos(worldPosition);
                level.addFreshEntity(orb);
            }
        }

        spawnWallLidOrbs(level, minX, maxX, minZ, maxZ, bottomY, wallShape);
        spawnWallLidOrbs(level, minX, maxX, minZ, maxZ, topY, wallShape);
    }

    /**
     * A sparse grid of orbs at a fixed Y, budgeted so a huge shape still spawns a bounded number
     * of them. Walks {@code wallShape}'s actual enclosed X/Z columns rather than the bounding
     * rectangle's full grid whenever a shape is available (an outer wall footprint) - for
     * anything but a perfect square loop, the rectangle covers real ground well outside the rune
     * outline the player actually drew, which is exactly why the lid used to look like it didn't
     * follow the runes at all. Sphere shields ({@code wallShape == null}) never call this at all
     * (see {@link #spawnShieldOrbs}), so the rectangle fallback only matters if this is ever
     * reused without a shape.
     */
    private void spawnWallLidOrbs(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int y, @Nullable ShieldShapeDetector.WallShape wallShape)
    {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        long area = (long) width * depth;
        int budget = SHIELD_WALL_LID_MAX_ORBS / 2;
        int spacing = Math.max(1, (int) Math.sqrt(area / (double) budget));

        for (int x = minX; x <= maxX; x += spacing)
        {
            for (int z = minZ; z <= maxZ; z += spacing)
            {
                if (wallShape != null && !wallShape.containsColumn(x, z))
                {
                    continue;
                }
                ShieldOrbEntity orb = new ShieldOrbEntity(ModEntities.SHIELD_ORB.get(), level);
                orb.setPos(x + 0.5, y + 0.5, z + 0.5);
                orb.setOwnerPos(worldPosition);
                level.addFreshEntity(orb);
            }
        }
    }

    private Vec3 shieldCenter()
    {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5);
    }

    /**
     * Whether {@code pos} currently falls inside this heart's own active Shield/Tempest Ward
     * boundary (sphere or wall shape, whichever's actually running) - used by {@code
     * ShieldLightningProtection} to stop lightning from reaching anyone standing under the
     * shield. Lightning doesn't work like an arrow or a punch: {@code LightningBolt} damages
     * everything in an area around where it strikes directly, with no collision against {@link
     * ShieldOrbEntity}'s own hitboxes involved at all, so the orb wall (which stops literally
     * everything else) was never actually in a position to block it - this is a second, explicit
     * check for exactly that one case, not a duplicate of the orb collision that's already there
     * for everything else. Deliberately a bit generous (the wall-shape branch counts being
     * exactly on a wall cell as "shielded" too, not just strictly inside) - a false positive here
     * just means lightning fizzles half a block early, not that safety is compromised.
     */
    public boolean isPositionShielded(Vec3 pos)
    {
        boolean shieldRunning = shieldActive;
        boolean tempestRunning = tempestTicksRemaining > 0;
        if (!shieldRunning && !tempestRunning)
        {
            return false;
        }

        Set<BlockPos> wallFootprint = shieldRunning ? shieldWallFootprint : null;
        if (wallFootprint != null && !wallFootprint.isEmpty())
        {
            int[] span = ensureMinWallSpan(shieldWallBottomY, shieldWallTopY);
            int bottomY = span[0];
            int topY = span[1];
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos cell : wallFootprint)
            {
                minX = Math.min(minX, cell.getX());
                maxX = Math.max(maxX, cell.getX());
                minZ = Math.min(minZ, cell.getZ());
                maxZ = Math.max(maxZ, cell.getZ());
            }
            int floorX = Mth.floor(pos.x);
            int floorZ = Mth.floor(pos.z);
            return floorX >= minX && floorX <= maxX && floorZ >= minZ && floorZ <= maxZ
                    && pos.y >= bottomY && pos.y <= topY;
        }
        else
        {
            return shieldCenter().distanceTo(pos) < SHIELD_RADIUS;
        }
    }

    /**
     * Actively pushes back any non-owner {@link LivingEntity} it finds inside the boundary,
     * every tick - the actual "unbreakable wall" mechanism, since {@link ShieldOrbEntity} is
     * purely cosmetic now (see its doc comment for why hard collision can't tell owner from
     * anyone else). Approximates a push straight away from the circle's center, in the
     * horizontal plane - exactly correct for the sphere, and a reasonable approximation for a
     * custom wall shape too as long as it's roughly convex (a simple circle/square drawn in
     * Purple chalk, say) - a deliberately simple heuristic rather than a true point-in-polygon
     * push, given how large an arbitrary player-drawn shape could be.
     */
    private static void tickShieldBoundary(ServerLevel level, HeartCoreBlockEntity blockEntity)
    {
        boolean shieldRunning = blockEntity.shieldActive;
        boolean tempestRunning = blockEntity.tempestTicksRemaining > 0;
        if (!shieldRunning && !tempestRunning)
        {
            return;
        }

        UUID ownerUuid = shieldRunning ? blockEntity.shieldOwnerUuid : blockEntity.tempestOwnerUuid;
        Set<BlockPos> wallFootprint = shieldRunning ? blockEntity.shieldWallFootprint : null;
        BlockPos center = blockEntity.worldPosition;

        if (wallFootprint != null && !wallFootprint.isEmpty())
        {
            tickWallBoundary(level, wallFootprint, blockEntity.shieldWallBottomY, blockEntity.shieldWallTopY, ownerUuid);
        }
        else
        {
            tickSphereBoundary(level, center, ownerUuid);
        }
    }

    /**
     * Who the boundary simply doesn't apply to: the caster, and any harmless creature. "Passive
     * mobs (not monsters) can remain inside the shield without being harmed or kicked out" - a
     * shelter that flings your own cows through the wall isn't much of a shelter. Players other
     * than the caster are still pushed out; only mobs get the pass, since that's what was asked
     * for and the shield's whole point against another player is that it's in the way.
     */
    private static boolean ignoresShieldBoundary(LivingEntity entity, @Nullable UUID ownerUuid)
    {
        if (ownerUuid != null && entity.getUUID().equals(ownerUuid))
        {
            return true;
        }
        return !(entity instanceof Player) && !(entity instanceof Enemy);
    }

    private static void tickSphereBoundary(ServerLevel level, BlockPos center, @Nullable UUID ownerUuid)
    {
        Vec3 mid = new Vec3(center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5);
        AABB box = new AABB(center).inflate(SHIELD_RADIUS + 2.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box))
        {
            if (ignoresShieldBoundary(entity, ownerUuid))
            {
                continue;
            }
            Vec3 pos = entity.position();
            Vec3 offset = pos.subtract(mid);
            double distance = offset.length();
            if (distance < SHIELD_RADIUS && distance > 1.0E-4)
            {
                Vec3 pushed = mid.add(offset.scale((SHIELD_RADIUS + SHIELD_REPEL_PADDING) / distance));
                entity.teleportTo(pushed.x, pushed.y, pushed.z);
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * Two independent checks, either of which can push an entity back in: the flat top/bottom
     * "lid" (enforced across the whole footprint's bounding rectangle - a ceiling or floor only
     * means something if it covers the interior, not just the boundary ring), and the side wall
     * itself (enforced only at the boundary ring's own X/Z columns, same as the sphere's radius
     * check - the interior is otherwise completely open to move through). The bounding rectangle
     * is an approximation of the shape's true interior, same convexity caveat as the side push
     * below - a very concave loop gets a lid slightly bigger than its actual enclosed area.
     */
    /**
     * Guarantees at least {@value #SHIELD_WALL_MIN_HEIGHT} blocks between the two, expanding
     * symmetrically if not - a last line of defense against a degenerate (equal or inverted)
     * pair collapsing the whole box down to a useless sliver, regardless of how that pair ended
     * up degenerate in the first place (a stale shield cast before this box system existed and
     * loaded back with these fields defaulting to 0 is the one known way that can happen, but
     * this holds regardless of the cause).
     */
    private static int[] ensureMinWallSpan(int bottomY, int topY)
    {
        int height = topY - bottomY;
        if (height >= SHIELD_WALL_MIN_HEIGHT)
        {
            return new int[] {bottomY, topY};
        }
        int deficit = SHIELD_WALL_MIN_HEIGHT - height;
        int expandDown = deficit / 2;
        int expandUp = deficit - expandDown;
        return new int[] {bottomY - expandDown, topY + expandUp};
    }

    private static void tickWallBoundary(ServerLevel level, Set<BlockPos> wallFootprint, int rawBottomY, int rawTopY, @Nullable UUID ownerUuid)
    {
        int[] span = ensureMinWallSpan(rawBottomY, rawTopY);
        int bottomY = span[0];
        int topY = span[1];
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : wallFootprint)
        {
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minZ = Math.min(minZ, cell.getZ());
            maxZ = Math.max(maxZ, cell.getZ());
        }
        AABB box = new AABB(minX, bottomY, minZ, maxX + 1, topY + 1, maxZ + 1).inflate(2.0);

        double cx = (minX + maxX + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box))
        {
            if (ignoresShieldBoundary(entity, ownerUuid))
            {
                continue;
            }

            int floorX = Mth.floor(entity.getX());
            int floorZ = Mth.floor(entity.getZ());
            boolean withinFootprintBounds = floorX >= minX && floorX <= maxX && floorZ >= minZ && floorZ <= maxZ;

            if (withinFootprintBounds && entity.getY() > topY)
            {
                entity.teleportTo(entity.getX(), topY - 0.05, entity.getZ());
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x, Math.min(0.0, motion.y), motion.z);
                continue;
            }
            if (withinFootprintBounds && entity.getY() < bottomY)
            {
                entity.teleportTo(entity.getX(), bottomY + 0.05, entity.getZ());
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x, Math.max(0.0, motion.y), motion.z);
                continue;
            }

            BlockPos feetColumn = new BlockPos(floorX, 0, floorZ);
            if (!wallFootprint.contains(feetColumn))
            {
                continue;
            }
            double dx = entity.getX() - cx;
            double dz = entity.getZ() - cz;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist < 1.0E-4)
            {
                dx = 1.0;
                horizontalDist = 1.0;
            }
            double dirX = dx / horizontalDist;
            double dirZ = dz / horizontalDist;

            // Step outward one cell at a time, away from center, until landing on ground that
            // *isn't* also a wall column - rather than a single fixed-distance nudge, which
            // could leave an entity still standing in a wall cell (and so still fair game to
            // shove further in next tick) wherever the wall happens to be more than 1 cell
            // thick in that direction - a diagonal corner closeDiagonalGaps just added a second
            // cell to, say. A fast-moving entity could also simply outrun a small fixed nudge,
            // net-migrating inward tick over tick; landing squarely past the wall in one go
            // rules both of those out regardless of the wall's local thickness or the entity's
            // own speed.
            double pushedX = entity.getX();
            double pushedZ = entity.getZ();
            for (int step = 1; step <= WALL_THICKNESS_SCAN_LIMIT; step++)
            {
                pushedX = entity.getX() + dirX * step;
                pushedZ = entity.getZ() + dirZ * step;
                BlockPos candidate = new BlockPos(Mth.floor(pushedX), 0, Mth.floor(pushedZ));
                if (!wallFootprint.contains(candidate))
                {
                    break;
                }
            }
            entity.teleportTo(pushedX, entity.getY(), pushedZ);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static Vec3[] fibonacciSphere(int count, double radius)
    {
        Vec3[] points = new Vec3[count];
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < count; i++)
        {
            double y = 1.0 - (i / (double) (count - 1)) * 2.0;
            double ringRadius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            points[i] = new Vec3(Math.cos(theta) * ringRadius * radius, y * radius, Math.sin(theta) * ringRadius * radius);
        }
        return points;
    }

    /**
     * Wide and tall enough to catch a custom wall shape's orbs too, which can sit far outside
     * the sphere's own radius (anywhere within the outer shape's footprint, and anywhere in the
     * world's build range vertically, since the box's own flat top/bottom is computed per-shield
     * rather than one shared constant) - a plain sphere-sized box would silently leave those
     * behind. Filtering by {@code ownerPos} rather than distance means the wider box costs
     * nothing extra for the common (sphere) case; it's just cheap insurance for the other one.
     */
    public static void removeShieldOrbs(ServerLevel level, BlockPos ownerPos)
    {
        AABB box = new AABB(ownerPos).inflate(150.0, 0, 150.0)
                .expandTowards(0, level.getMaxBuildHeight() - ownerPos.getY(), 0)
                .expandTowards(0, level.getMinBuildHeight() - ownerPos.getY(), 0);
        List<ShieldOrbEntity> orbs = level.getEntitiesOfClass(ShieldOrbEntity.class, box, orb -> orb.getOwnerPos().equals(ownerPos));
        orbs.forEach(orb -> orb.discard());
    }

    /**
     * Converts the (already-validated, see {@code HeartCoreBlock#interact}) water pit around
     * this ring into portal water and builds a mirrored crossing point in the Fairy Realm - see
     * {@link FairyPortalManager}. Called once the ring is confirmed to be a {@code PortalRitual}
     * pattern with a full water pit, and {@value #PORTAL_MANA_COST} mana has already been spent.
     * A lightning strike lands right on the Heart Core itself as the portal forms - purely
     * cosmetic (the Heart Core is immune to explosion/fire the same way every other block here
     * is unharmed by lightning in the Fairy Realm, and this strike isn't even in that dimension),
     * just a dramatic flourish for "a portal is tearing open right here."
     */
    public void openWaterPortal(ServerLevel level, Player caster)
    {
        if (caster instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
        {
            FairyPortalManager.openWaterPortal(level, worldPosition, centerColor, serverPlayer);
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null)
        {
            bolt.setVisualOnly(true);
            bolt.moveTo(Vec3.atBottomCenterOf(worldPosition));
            level.addFreshEntity(bolt);
        }
        startOneShotChannel(WispChannel.OUTWARD);
    }

    /** One-shot: grows a scattering of flowers on grass around the circle. */
    public void castFlowerSpell(ServerLevel level)
    {
        startOneShotChannel(WispChannel.OUTWARD);
        scheduleEffect(lvl -> growAround(lvl, Blocks.GRASS_BLOCK, () -> FLOWERS[lvl.random.nextInt(FLOWERS.length)].defaultBlockState()));
    }

    /**
     * Every spell's actual world effect goes through here rather than running immediately -
     * called only after the caller has already started its wisps flying (see each spell's own
     * {@code cast*Spell}/{@code start*Spell} method), so the wisps get a head start rather than
     * the effect appearing at the same instant they do. See {@link #SPELL_EFFECT_DELAY_TICKS}
     * and {@link #tickPendingEffects}.
     */
    private void scheduleEffect(java.util.function.Consumer<ServerLevel> effect)
    {
        pendingEffects.add(new PendingEffect(SPELL_EFFECT_DELAY_TICKS, effect));
    }

    /**
     * Shared by {@link #castFlowerSpell} and {@link #castVerdantHarvestSpell}: tries
     * {@value #GROWTH_ATTEMPTS} random points scattered between {@value #GROWTH_MIN_RADIUS} and
     * {@value #GROWTH_MAX_RADIUS} blocks of the circle (a random compass direction and
     * distance, not a fixed-radius square), finds each column's *actual* surface height rather
     * than assuming it matches the heart's own Y, and places a block on top wherever that
     * surface matches {@code surfaceBlock}.
     */
    private void growAround(ServerLevel level, Block surfaceBlock, java.util.function.Supplier<BlockState> stateToPlace)
    {
        RandomSource random = level.random;
        for (int i = 0; i < GROWTH_ATTEMPTS; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = Mth.lerp(random.nextDouble(), GROWTH_MIN_RADIUS, GROWTH_MAX_RADIUS);
            int x = worldPosition.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = worldPosition.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (Math.abs(x - worldPosition.getX()) <= CIRCLE_FOOTPRINT_HALF_SIZE
                    && Math.abs(z - worldPosition.getZ()) <= CIRCLE_FOOTPRINT_HALF_SIZE)
            {
                continue;
            }
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, surfaceY - 1, z);
            if (level.getBlockState(ground).is(surfaceBlock) && level.getBlockState(ground.above()).isAir())
            {
                level.setBlockAndUpdate(ground.above(), stateToPlace.get());
            }
        }
    }

    /** One-shot: fully heals every friendly creature (players, animals, villagers - not hostile mobs) nearby. */
    public void castHealSpell(ServerLevel level)
    {
        List<LivingEntity> targets = findHealTargets(level);
        startTargetedChannel(targets);
        scheduleEffect(lvl ->
        {
            healEntities(targets, true);
            lvl.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f);
        });
    }

    /**
     * Every {@link LivingEntity} within {@value #HEAL_RADIUS} blocks that isn't an
     * {@link Enemy} (covers both {@code Monster} subclasses and non-Monster hostiles like
     * slimes) - selected immediately, at cast time, so the caller can point wisps at them right
     * away, even though the actual healing (see {@link #healEntities}) doesn't happen until the
     * spell's delayed effect fires.
     */
    private List<LivingEntity> findHealTargets(ServerLevel level)
    {
        AABB box = new AABB(worldPosition).inflate(HEAL_RADIUS);
        return new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof Enemy)));
    }

    /**
     * Heals every still-{@link LivingEntity#isAlive} entity in {@code targets} to full - a
     * target selected back at cast time by {@link #findHealTargets} could have died or wandered
     * off by the time this actually runs, hence the liveness check rather than trusting the list
     * as given.
     */
    private void healEntities(List<LivingEntity> targets, boolean alsoRegenPlayers)
    {
        for (LivingEntity entity : targets)
        {
            if (!entity.isAlive())
            {
                continue;
            }
            entity.heal(entity.getMaxHealth());
            if (alsoRegenPlayers && entity instanceof Player)
            {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BLOOM_REGEN_DURATION_TICKS, 1));
            }
        }
    }

    /** One-shot: scatters experience orbs around the circle. */
    public void castXpSpell(ServerLevel level)
    {
        startOneShotChannel(WispChannel.TO_PLAYER);
        scheduleEffect(lvl -> spawnXpOrbs(lvl, XP_ORB_COUNT, XP_PER_ORB, XP_SCATTER_RADIUS));
    }

    private void spawnXpOrbs(ServerLevel level, int count, int valuePerOrb, double scatterRadius)
    {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++)
        {
            double x = worldPosition.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.0 * scatterRadius;
            double z = worldPosition.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.0 * scatterRadius;
            ExperienceOrb orb = new ExperienceOrb(level, x, worldPosition.getY() + 1.0, z, valuePerOrb);
            level.addFreshEntity(orb);
        }
    }

    /** Blue+Red combo, one-shot: strips negative potion effects and douses fire on everyone nearby. */
    public void castCleansingRainSpell(ServerLevel level)
    {
        startOneShotChannel(WispChannel.OUTWARD);
        scheduleEffect(lvl ->
        {
            AABB box = new AABB(worldPosition).inflate(CLEANSING_RADIUS);
            for (LivingEntity entity : lvl.getEntitiesOfClass(LivingEntity.class, box))
            {
                for (MobEffectInstance effect : List.copyOf(entity.getActiveEffects()))
                {
                    if (!effect.getEffect().isBeneficial())
                    {
                        entity.removeEffect(effect.getEffect());
                    }
                }
                if (entity.isOnFire())
                {
                    entity.clearFire();
                }
            }
        });
    }

    /**
     * Purple+Green combo: no upfront cost beyond the minimum needed to bother starting -
     * regenerates the heart's own mana over 60 seconds instead of spending any. Gated on
     * Wellspring proximity by the caller (see {@code HeartCoreBlock#interact}) rather than
     * here, since that check needs the ring's position, which the caller already has.
     */
    public void startManaFontSpell(ServerLevel level)
    {
        setWispChannel(WispChannel.DOWN);
        scheduleEffect(lvl ->
        {
            manaFontTicksRemaining = MANA_FONT_DURATION_TICKS;
            manaFontNextTickIn = MANA_FONT_INTERVAL_TICKS;
            setChanged();
            syncToClient();
        });
    }

    /** Gold+Red combo, one-shot: flowers and a full heal together, plus a short Regeneration buff for players. */
    public void castBloomOfLifeSpell(ServerLevel level)
    {
        List<LivingEntity> targets = findHealTargets(level);
        startTargetedChannel(targets);
        scheduleEffect(lvl ->
        {
            growAround(lvl, Blocks.GRASS_BLOCK, () -> FLOWERS[lvl.random.nextInt(FLOWERS.length)].defaultBlockState());
            healEntities(targets, true);
            lvl.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f);
        });
    }

    /** Gold+Green combo, one-shot: grows mature crops on nearby farmland instead of decorative flowers. */
    public void castVerdantHarvestSpell(ServerLevel level)
    {
        startOneShotChannel(WispChannel.OUTWARD);
        scheduleEffect(lvl ->
        {
            growAround(lvl, Blocks.FARMLAND, () -> CROPS[lvl.random.nextInt(CROPS.length)].defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
            spawnXpOrbs(lvl, VERDANT_XP_ORB_COUNT, XP_PER_ORB, XP_SCATTER_RADIUS);
        });
    }

    /**
     * Red+Green combo: a combat buff (Strength, Speed, Absorption) for every nearby player -
     * now an ongoing effect rather than a one-shot, refreshed every 5 seconds for as long as
     * mana holds out, draining the heart exactly like the Shield spell (50 mana/minute).
     */
    public void startVitalSurgeSpell(ServerLevel level)
    {
        setWispChannel(WispChannel.FROM_HEART);
        scheduleEffect(lvl ->
        {
            vitalSurgeActive = true;
            vitalSurgeDrainCountdown = VITAL_SURGE_DRAIN_INTERVAL_TICKS;
            vitalSurgeReapplyCountdown = 0;
            setChanged();
            syncToClient();
        });
    }

    private void endVitalSurgeSpell()
    {
        vitalSurgeActive = false;
        refreshWispChannelAfterSpellEnd();
    }

    private void applyVitalSurgeBuffs(ServerLevel level)
    {
        AABB box = new AABB(worldPosition).inflate(VITAL_SURGE_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, box))
        {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, VITAL_SURGE_EFFECT_DURATION_TICKS, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, VITAL_SURGE_EFFECT_DURATION_TICKS, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, VITAL_SURGE_EFFECT_DURATION_TICKS, 1));
        }
    }

    private void startOneShotChannel(WispChannel channel)
    {
        wispTargetEntityIds = NO_TARGETS;
        setWispChannel(channel);
        oneShotChannelTicksRemaining = ONE_SHOT_CHANNEL_TICKS;
    }

    /** Same as {@link #startOneShotChannel}, but each wisp flies to one of the given entities instead of a shared destination. */
    private void startTargetedChannel(List<LivingEntity> targets)
    {
        wispTargetEntityIds = targets.stream()
                .limit(HEAL_MAX_WISP_TARGETS)
                .mapToInt(Entity::getId)
                .toArray();
        setWispChannel(WispChannel.TO_TARGETS);
        oneShotChannelTicksRemaining = ONE_SHOT_CHANNEL_TICKS;
    }

    /** Where this circle's wisps fly while channeling a spell - {@link WispChannel#ORBIT} when idle. Synced to the client. */
    public WispChannel getWispChannel()
    {
        return wispChannel;
    }

    public void setWispChannel(WispChannel channel)
    {
        this.wispChannel = channel;
        setChanged();
        syncToClient();
    }

    private void syncToClient()
    {
        if (level != null && !level.isClientSide)
        {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HeartCoreBlockEntity blockEntity)
    {
        blockEntity.age++;
        if (blockEntity.age % RING_CHECK_INTERVAL == 0)
        {
            boolean wasComplete = blockEntity.ringComplete;
            // A portal border (PortalRitual) is a real, valid magic circle in its own right -
            // it just isn't MagicCircleRitual's own rounded shape - so it gets the same "wisps
            // orbit while the pattern is intact" treatment any other complete ring does, not
            // just the temporary burst openWaterPortal fires off when the spell actually casts.
            blockEntity.ringComplete = MagicCircleRitual.hasCompleteRing(level, pos) || PortalRitual.matchesBorder(level, pos);
            if (wasComplete && !blockEntity.ringComplete)
            {
                ClientCircleWisps.remove(pos);
            }
        }

        if (blockEntity.ringComplete)
        {
            ClientCircleWisps.tick(level, pos, blockEntity.wispChannel, blockEntity.wispTargetEntityIds, blockEntity.manaDepletedSynced,
                    blockEntity.shieldWallFootprintSynced, blockEntity.shieldWallBottomYSynced, blockEntity.shieldWallTopYSynced);
        }
        ClientHeartWisps.tick(level, pos, blockEntity.spellActiveSynced, blockEntity.manaDepletedSynced);
        ClientHeartWisps.tickSecondary(level, pos, blockEntity.secondSpellActiveSynced, blockEntity.manaDepletedSynced);
        ShieldRingWisps.tick(level, pos, blockEntity.shieldActiveSynced, blockEntity.shieldWallFootprintSynced,
                blockEntity.shieldWallBottomYSynced, blockEntity.shieldWallTopYSynced);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HeartCoreBlockEntity blockEntity)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        tickOneShotChannel(blockEntity);
        tickPendingEffects(serverLevel, blockEntity);
        tickTempestWard(serverLevel, pos, blockEntity);
        tickShieldBoundary(serverLevel, blockEntity);
        tickManaFont(blockEntity);
        tickVitalSurge(serverLevel, blockEntity);
        tickStorm(serverLevel, pos, blockEntity);
        tickRingIntegrity(serverLevel, pos, blockEntity);
    }

    /**
     * Breaking the ring - actually destroying a rune, not just repainting one (repainting keeps
     * every position a real {@code MagicCircleBlock}, so {@link MagicCircleRitual#hasCompleteRing}
     * still sees it as complete - see {@code startShieldSpell}'s doc comment on why that's
     * exactly what lets a second spell be cast mid-shield) - cancels every prolonged spell this
     * heart has running, the same as if its mana had simply run out. The portal is checked
     * separately here too: it isn't one of this heart's own duration fields, it's tracked
     * externally in {@code FairyPortalManager}, keyed by this heart's own position, so a broken
     * 5x5 border closes it the same way completing the round trip normally would.
     */
    private static void tickRingIntegrity(ServerLevel level, BlockPos pos, HeartCoreBlockEntity blockEntity)
    {
        if (level.getGameTime() % RING_CHECK_INTERVAL != 0)
        {
            return;
        }
        if (blockEntity.isSpellActive() && !MagicCircleRitual.hasCompleteRing(level, pos))
        {
            blockEntity.cancelAllActiveSpells(level);
        }
        FairyPortalManager.closeIfBorderBroken(level, pos);
    }

    private void cancelAllActiveSpells(ServerLevel level)
    {
        if (shieldActive)
        {
            endShieldSpell(level);
        }
        if (tempestTicksRemaining > 0)
        {
            endTempestWardSpell(level);
        }
        if (stormTicksRemaining > 0)
        {
            stormTicksRemaining = 0;
            level.setWeatherParameters(0, 0, false, false);
            refreshWispChannelAfterSpellEnd();
        }
        if (manaFontTicksRemaining > 0)
        {
            manaFontTicksRemaining = 0;
            refreshWispChannelAfterSpellEnd();
        }
        if (vitalSurgeActive)
        {
            endVitalSurgeSpell();
        }
    }

    private static void tickOneShotChannel(HeartCoreBlockEntity blockEntity)
    {
        if (blockEntity.oneShotChannelTicksRemaining > 0)
        {
            blockEntity.oneShotChannelTicksRemaining--;
            if (blockEntity.oneShotChannelTicksRemaining == 0 && !blockEntity.isSpellActive())
            {
                blockEntity.setWispChannel(WispChannel.ORBIT);
            }
        }
    }

    /** Counts down and fires every spell's own {@link #scheduleEffect}-delayed effect - see {@link #SPELL_EFFECT_DELAY_TICKS}. */
    private static void tickPendingEffects(ServerLevel level, HeartCoreBlockEntity blockEntity)
    {
        if (blockEntity.pendingEffects.isEmpty())
        {
            return;
        }
        Iterator<PendingEffect> iterator = blockEntity.pendingEffects.iterator();
        while (iterator.hasNext())
        {
            PendingEffect pending = iterator.next();
            pending.ticksRemaining--;
            if (pending.ticksRemaining <= 0)
            {
                pending.effect.accept(level);
                iterator.remove();
            }
        }
    }

    /**
     * The shield's actual cost - called by {@link com.patrickma.magiccircles.entity.ShieldOrbEntity#hurt}
     * on every single hit any of this heart's orbs takes, not on a timer (see the field-level doc
     * comment above the old {@code SHIELD_DRAIN_INTERVAL_TICKS}/{@code SHIELD_DRAIN_AMOUNT}
     * constants this replaced). 1 mana per 1 hit point of damage, rounded up so even a
     * fractional-damage hit still costs something rather than draining nothing - shared across
     * every orb this heart owns, since they all call into this same method/pool: a creeper
     * exploding against three orbs in the same tick drains three separate hits from this one
     * heart's mana, not from three independent budgets.
     */
    public void drainShieldMana(ServerLevel level, float damageAmount)
    {
        if (!shieldActive || damageAmount <= 0.0f)
        {
            return;
        }
        int cost = Math.max(1, Math.round(damageAmount));
        if (mana <= cost)
        {
            setMana(0);
            endShieldSpell(level);
        }
        else
        {
            setMana(mana - cost);
        }
        setChanged();
        syncToClient();
    }

    private static void tickTempestWard(ServerLevel level, BlockPos pos, HeartCoreBlockEntity blockEntity)
    {
        if (blockEntity.tempestTicksRemaining <= 0)
        {
            return;
        }
        blockEntity.tempestTicksRemaining--;
        if (blockEntity.tempestTicksRemaining <= 0)
        {
            blockEntity.endTempestWardSpell(level);
            return;
        }
        if (blockEntity.tempestNextStrikeIn <= 0)
        {
            strikeNearestHostile(level, blockEntity.shieldCenter());
            blockEntity.tempestNextStrikeIn = TEMPEST_STRIKE_MIN_INTERVAL_TICKS
                    + level.random.nextInt(TEMPEST_STRIKE_MAX_INTERVAL_TICKS - TEMPEST_STRIKE_MIN_INTERVAL_TICKS + 1);
        }
        else
        {
            blockEntity.tempestNextStrikeIn--;
        }
    }

    private static void strikeNearestHostile(ServerLevel level, Vec3 center)
    {
        AABB box = new AABB(center, center).inflate(SHIELD_RADIUS);
        List<Monster> hostiles = level.getEntitiesOfClass(Monster.class, box);
        if (hostiles.isEmpty())
        {
            return;
        }
        Monster target = hostiles.get(level.random.nextInt(hostiles.size()));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null)
        {
            bolt.moveTo(target.position());
            level.addFreshEntity(bolt);
        }
    }

    private static void tickManaFont(HeartCoreBlockEntity blockEntity)
    {
        if (blockEntity.manaFontTicksRemaining <= 0)
        {
            return;
        }
        blockEntity.manaFontTicksRemaining--;
        blockEntity.manaFontNextTickIn--;
        if (blockEntity.manaFontNextTickIn <= 0)
        {
            blockEntity.setMana(blockEntity.mana + MANA_FONT_AMOUNT);
            blockEntity.manaFontNextTickIn = MANA_FONT_INTERVAL_TICKS;
        }
        if (blockEntity.manaFontTicksRemaining <= 0)
        {
            blockEntity.refreshWispChannelAfterSpellEnd();
        }
    }

    private static void tickVitalSurge(ServerLevel level, HeartCoreBlockEntity blockEntity)
    {
        if (!blockEntity.vitalSurgeActive)
        {
            return;
        }
        blockEntity.vitalSurgeDrainCountdown--;
        if (blockEntity.vitalSurgeDrainCountdown <= 0)
        {
            if (blockEntity.mana < VITAL_SURGE_DRAIN_AMOUNT)
            {
                blockEntity.setMana(0);
                blockEntity.endVitalSurgeSpell();
                return;
            }
            blockEntity.setMana(blockEntity.mana - VITAL_SURGE_DRAIN_AMOUNT);
            blockEntity.vitalSurgeDrainCountdown = VITAL_SURGE_DRAIN_INTERVAL_TICKS;
        }

        blockEntity.vitalSurgeReapplyCountdown--;
        if (blockEntity.vitalSurgeReapplyCountdown <= 0)
        {
            blockEntity.applyVitalSurgeBuffs(level);
            blockEntity.vitalSurgeReapplyCountdown = VITAL_SURGE_REAPPLY_INTERVAL_TICKS;
        }
    }

    private static void tickStorm(ServerLevel level, BlockPos pos, HeartCoreBlockEntity blockEntity)
    {
        if (blockEntity.stormTicksRemaining <= 0)
        {
            return;
        }

        blockEntity.stormTicksRemaining--;
        if (blockEntity.stormTicksRemaining <= 0)
        {
            level.setWeatherParameters(0, 0, false, false);
            blockEntity.refreshWispChannelAfterSpellEnd();
            return;
        }

        if (blockEntity.nextLightningIn <= 0)
        {
            strikeLightningAround(level, pos);
            blockEntity.nextLightningIn = LIGHTNING_MIN_INTERVAL_TICKS
                    + level.random.nextInt(LIGHTNING_MAX_INTERVAL_TICKS - LIGHTNING_MIN_INTERVAL_TICKS + 1);
        }
        else
        {
            blockEntity.nextLightningIn--;
        }
    }

    private static void strikeLightningAround(ServerLevel level, BlockPos center)
    {
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = Mth.lerp(random.nextDouble(), STRIKE_MIN_RADIUS, STRIKE_MAX_RADIUS);
        int strikeX = Mth.floor(center.getX() + 0.5 + Math.cos(angle) * radius);
        int strikeZ = Mth.floor(center.getZ() + 0.5 + Math.sin(angle) * radius);
        int strikeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, strikeX, strikeZ);

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null)
        {
            bolt.moveTo(Vec3.atBottomCenterOf(new BlockPos(strikeX, strikeY, strikeZ)));
            level.addFreshEntity(bolt);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("Mana", mana);
        tag.putInt("StormTicksRemaining", stormTicksRemaining);
        tag.putString("CenterColor", centerColor.getSerializedName());
        tag.putString("WispChannel", wispChannel.name());
        tag.putBoolean("ShieldActive", shieldActive);
        if (shieldOwnerUuid != null)
        {
            tag.putUUID("ShieldOwner", shieldOwnerUuid);
        }
        if (shieldWallFootprint != null)
        {
            long[] packed = shieldWallFootprint.stream().mapToLong(BlockPos::asLong).toArray();
            tag.putLongArray("ShieldWallFootprint", packed);
            tag.putInt("ShieldWallBottomY", shieldWallBottomY);
            tag.putInt("ShieldWallTopY", shieldWallTopY);
        }
        tag.putInt("TempestTicksRemaining", tempestTicksRemaining);
        tag.putInt("TempestNextStrikeIn", tempestNextStrikeIn);
        if (tempestOwnerUuid != null)
        {
            tag.putUUID("TempestOwner", tempestOwnerUuid);
        }
        tag.putInt("ManaFontTicksRemaining", manaFontTicksRemaining);
        tag.putInt("ManaFontNextTickIn", manaFontNextTickIn);
        tag.putBoolean("VitalSurgeActive", vitalSurgeActive);
        tag.putInt("VitalSurgeDrainCountdown", vitalSurgeDrainCountdown);
        tag.putInt("VitalSurgeReapplyCountdown", vitalSurgeReapplyCountdown);
        tag.putBoolean("ManaDepleted", manaDepleted);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        mana = tag.getInt("Mana");
        stormTicksRemaining = tag.getInt("StormTicksRemaining");
        centerColor = readColor(tag);
        wispChannel = readChannel(tag);
        shieldActive = tag.getBoolean("ShieldActive");
        shieldOwnerUuid = tag.hasUUID("ShieldOwner") ? tag.getUUID("ShieldOwner") : null;
        if (tag.contains("ShieldWallFootprint"))
        {
            shieldWallFootprint = new HashSet<>();
            for (long packed : tag.getLongArray("ShieldWallFootprint"))
            {
                shieldWallFootprint.add(BlockPos.of(packed));
            }
            shieldWallBottomY = tag.getInt("ShieldWallBottomY");
            shieldWallTopY = tag.getInt("ShieldWallTopY");
        }
        else
        {
            shieldWallFootprint = null;
        }
        tempestTicksRemaining = tag.getInt("TempestTicksRemaining");
        tempestNextStrikeIn = tag.getInt("TempestNextStrikeIn");
        tempestOwnerUuid = tag.hasUUID("TempestOwner") ? tag.getUUID("TempestOwner") : null;
        manaFontTicksRemaining = tag.getInt("ManaFontTicksRemaining");
        manaFontNextTickIn = tag.getInt("ManaFontNextTickIn");
        vitalSurgeActive = tag.getBoolean("VitalSurgeActive");
        vitalSurgeDrainCountdown = tag.getInt("VitalSurgeDrainCountdown");
        vitalSurgeReapplyCountdown = tag.getInt("VitalSurgeReapplyCountdown");
        manaDepleted = tag.getBoolean("ManaDepleted");
    }

    // Client-only mirrors of server-only fields, kept current via getUpdateTag/handleUpdateTag -
    // none of the fields isSpellActive()/manaDepleted read are ever themselves synced to an
    // already-loaded client, only saved/loaded on the server, so the client needs its own copies.
    private boolean spellActiveSynced;
    private boolean secondSpellActiveSynced;
    private boolean manaDepletedSynced;
    private boolean manaFontActiveSynced;

    /**
     * Only the wisp channel/targets and the spell-active/mana-depleted flags need to reach an
     * already-loaded client live (mana/spell progress otherwise only matter server-side) - see
     * {@link #setWispChannel}/{@link #syncToClient} for what triggers sending this.
     */
    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("WispChannel", wispChannel.name());
        tag.putIntArray("WispTargets", wispTargetEntityIds);
        tag.putBoolean("SpellActive", isSpellActive());
        tag.putBoolean("SecondSpellActive", isSecondSpellActive());
        tag.putBoolean("ManaDepleted", manaDepleted);
        tag.putBoolean("ManaFontActive", manaFontTicksRemaining > 0);
        tag.putBoolean("ShieldActive", shieldActive);
        // See ShieldRingWisps - it needs the wall's own real footprint/height to trace the
        // shield's actual extent rather than always assuming the plain sphere.
        if (shieldWallFootprint != null)
        {
            long[] packed = new long[shieldWallFootprint.size()];
            int i = 0;
            for (BlockPos cell : shieldWallFootprint)
            {
                packed[i++] = cell.asLong();
            }
            tag.putLongArray("ShieldWallFootprintSync", packed);
            tag.putInt("ShieldWallBottomYSync", shieldWallBottomY);
            tag.putInt("ShieldWallTopYSync", shieldWallTopY);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag)
    {
        wispChannel = readChannel(tag);
        wispTargetEntityIds = tag.contains("WispTargets") ? tag.getIntArray("WispTargets") : NO_TARGETS;
        spellActiveSynced = tag.getBoolean("SpellActive");
        secondSpellActiveSynced = tag.getBoolean("SecondSpellActive");
        manaDepletedSynced = tag.getBoolean("ManaDepleted");
        manaFontActiveSynced = tag.getBoolean("ManaFontActive");
        shieldActiveSynced = tag.getBoolean("ShieldActive");
        if (tag.contains("ShieldWallFootprintSync"))
        {
            Set<BlockPos> footprint = new HashSet<>();
            for (long packed : tag.getLongArray("ShieldWallFootprintSync"))
            {
                footprint.add(BlockPos.of(packed));
            }
            shieldWallFootprintSynced = footprint;
            shieldWallBottomYSynced = tag.getInt("ShieldWallBottomYSync");
            shieldWallTopYSynced = tag.getInt("ShieldWallTopYSync");
        }
        else
        {
            shieldWallFootprintSynced = null;
        }
    }

    private boolean shieldActiveSynced;
    @Nullable
    private Set<BlockPos> shieldWallFootprintSynced;
    private int shieldWallBottomYSynced;
    private int shieldWallTopYSynced;

    /** Client-side mirror of {@link #shieldActive} - see {@code ShieldRingWisps}, which flies purple wisps around the shield's own real extent (opposite direction to {@code ClientHeartWisps}' white ones) for as long as this is true, whether Shield resolved to its sphere or an outer wall shape. */
    public boolean isShieldActiveSynced()
    {
        return shieldActiveSynced;
    }

    /** Client-side mirror of {@link #shieldWallFootprint} - {@code null} means the plain sphere (see {@link #shieldWallBottomYSynced}/{@link #shieldWallTopYSynced} for the matching Y bounds when this isn't null). */
    @Nullable
    public Set<BlockPos> getShieldWallFootprintSynced()
    {
        return shieldWallFootprintSynced;
    }

    public int getShieldWallBottomYSynced()
    {
        return shieldWallBottomYSynced;
    }

    public int getShieldWallTopYSynced()
    {
        return shieldWallTopYSynced;
    }

    /** Client-side mirror of "Mana Font currently running" - see {@code WellspringWisps} for the one thing that reads this. */
    public boolean isManaFontActiveSynced()
    {
        return manaFontActiveSynced;
    }

    /** Client-side mirror of {@link #manaDepleted} - see {@code HeartCoreBlockEntityRenderer} for the dark/shrunk/non-glowing look this drives. */
    public boolean isManaDepletedSynced()
    {
        return manaDepletedSynced;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Forge's default {@code onDataPacket} (from {@code IForgeBlockEntity}, confirmed by
     * decompiling it rather than assumed) calls {@code load(tag)}, not
     * {@link #handleUpdateTag}, when a live update packet arrives - only the very first sync
     * (a chunk coming into view) goes through {@code handleUpdateTag} directly. Without this
     * override, every *subsequent* {@link #syncToClient} push (a spell starting or ending,
     * mid-game) silently fell back to {@code load()}, which never read "SpellActive" or
     * "WispTargets" at all - only "WispChannel" happened to work, and only because it's
     * (redundantly) also read in {@link #load}. This is what made the white heart wisps never
     * actually move: {@link #spellActiveSynced} was permanently stuck at its default
     * {@code false} on the client, no matter what was actually happening server-side.
     */
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet)
    {
        handleUpdateTag(packet.getTag());
    }

    private static WispChannel readChannel(CompoundTag tag)
    {
        try
        {
            return WispChannel.valueOf(tag.getString("WispChannel"));
        }
        catch (IllegalArgumentException e)
        {
            return WispChannel.ORBIT;
        }
    }

    private static RuneColor readColor(CompoundTag tag)
    {
        String name = tag.getString("CenterColor");
        for (RuneColor color : RuneColor.values())
        {
            if (color.getSerializedName().equals(name))
            {
                return color;
            }
        }
        return RuneColor.BLUE;
    }

    /** One spell's world effect, waiting out {@link #SPELL_EFFECT_DELAY_TICKS} before it runs - see {@link #scheduleEffect}. */
    private static final class PendingEffect
    {
        int ticksRemaining;
        final java.util.function.Consumer<ServerLevel> effect;

        PendingEffect(int ticksRemaining, java.util.function.Consumer<ServerLevel> effect)
        {
            this.ticksRemaining = ticksRemaining;
            this.effect = effect;
        }
    }
}
