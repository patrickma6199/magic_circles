package com.patrickma.magiccircles;

import com.patrickma.magiccircles.entity.FairyEntity;
import com.patrickma.magiccircles.entity.FairyQueenEntity;
import com.patrickma.magiccircles.limbo.LimboRegistry;
import com.patrickma.magiccircles.limbo.LimboState;
import com.patrickma.magiccircles.registry.ModDimensions;
import com.patrickma.magiccircles.registry.ModEntities;
import com.patrickma.magiccircles.worldgen.WorldTree;
import com.patrickma.magiccircles.worldgen.FairyThroneRoom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Fairy Queen's court: making sure there is exactly one queen, and that there always is one.
 *
 * <p>Who reigns is saved with the world (see {@link Court}), so it survives restarts and the queen
 * being far away in an unloaded part of the tree. With someone in the Fairy Realm, the throne is
 * checked every {@value #CHECK_INTERVAL_TICKS} ticks; if it's empty - she died, or her last known
 * place is loaded and she isn't there - a fairy woman steps up to it, or failing any, a new queen
 * appears among the branches. A queen who finds another already reigning steps back down.
 *
 * <p>Every queen reigns under a name - "Zuzo, Queen of the Faye" (see {@link FairyQueenNames}) - a
 * fairy who steps up keeps her own. Though named, a queen never crosses the veil when she falls (see
 * {@code limbo/DeathLimboManager#crossesOver}): she has already transcended it, and returns straight
 * to the Wellspring.
 *
 * <p>The court also sends for the lost: now and then, a ghost that has been dead a while is drawn
 * to her from wherever it wandered - any dimension - to hear her bargain.
 *
 * <p>And it keeps the souls of the fallen. A fairy that dies does not wait by its body as other
 * creatures do: its soul goes straight to the court (see {@link #receiveSoul}) and stands in line
 * along the wall, behind the veil, until the queen has time for it. She never lets the line grow
 * past {@value #MAX_SOULS}: the moment it would, she breathes life back into whichever soul has
 * waited longest ({@link #tendSouls}).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyCourt
{
    private static final int CHECK_INTERVAL_TICKS = 20 * 10;
    private static final int SUMMON_INTERVAL_TICKS = 20 * 60;
    private static final float SUMMON_CHANCE = 0.25f;
    /** Dead at least this long before she takes an interest - a moment to be found by friends first. */
    private static final long MIN_DEAD_TICKS = 20 * 60;
    private static final long SUMMON_COOLDOWN_TICKS = 20 * 60 * 10;
    private static final int ALREADY_WITH_HER = 48;
    /** How many checks in a row she must be missing from her last known place before she is truly gone - a minute's worth. */
    private static final int QUEEN_MISSING_CHECKS = 6;
    /** A queen's death: this long, the whole realm weeps rain for her. */
    private static final int MOURNING_RAIN_TICKS = 20 * 60 * 3;

    private static int missingChecks;

    private static final Map<UUID, Long> lastSummoned = new HashMap<>();

    private FairyCourt()
    {
    }

    /** Saved with the Fairy Realm: who reigns, and where she was last seen. */
    public static final class Court extends SavedData
    {
        @Nullable
        UUID queen;
        @Nullable
        BlockPos lastSeen;
        /** What she is called - "Zuzo, Queen of the Faye" - so she can be named when she is nowhere loaded. */
        @Nullable
        String name;

        public static Court load(CompoundTag tag)
        {
            Court court = new Court();
            court.queen = tag.hasUUID("Queen") ? tag.getUUID("Queen") : null;
            court.lastSeen = tag.contains("LastSeen") ? BlockPos.of(tag.getLong("LastSeen")) : null;
            court.name = tag.contains("Name") ? tag.getString("Name") : null;
            return court;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            if (this.queen != null)
            {
                tag.putUUID("Queen", this.queen);
            }
            if (this.lastSeen != null)
            {
                tag.putLong("LastSeen", this.lastSeen.asLong());
            }
            if (this.name != null)
            {
                tag.putString("Name", this.name);
            }
            return tag;
        }
    }

    private static Court court(ServerLevel realm)
    {
        return realm.getDataStorage().computeIfAbsent(Court::load, Court::new, "magiccircles_fairy_court");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel realm = server.getLevel(ModDimensions.FAIRY_REALM);
        if (realm == null)
        {
            return;
        }
        int tick = server.getTickCount();
        if (tick % CHECK_INTERVAL_TICKS == 0 && !realm.players().isEmpty())
        {
            keepTheThrone(realm);
            tendSouls(realm);
        }
        if (tick % SUMMON_INTERVAL_TICKS == 0)
        {
            summonTheLost(server, realm);
        }
    }

    /** Makes sure the throne is held - used when the realm is first laid out (see {@code worldgen/WorldTree}). */
    public static void ensureQueen(ServerLevel realm)
    {
        keepTheThrone(realm);
    }

    private static void keepTheThrone(ServerLevel realm)
    {
        Court court = court(realm);
        if (court.queen != null)
        {
            Entity current = realm.getEntity(court.queen);
            if (current instanceof FairyQueenEntity queen && queen.isAlive())
            {
                missingChecks = 0;
                return;
            }
            // Not to be found is not the same as gone. She may only be somewhere unloaded - off on a
            // lap of the tree, say - or in a chunk whose creatures haven't finished loading yet, which
            // happens for a moment every time a chunk comes back: its blocks load before its
            // creatures do. That moment was crowning a new queen over a perfectly living one. Only
            // once the creatures at her last known place have loaded, and she has stayed missing from
            // it for a good while, is she truly gone.
            if (current != null || court.lastSeen == null
                    || !realm.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(court.lastSeen))
                    || ++missingChecks < QUEEN_MISSING_CHECKS)
            {
                return;
            }
            missingChecks = 0;
            court.queen = null;
            court.setDirty();
        }
        crown(realm, court);
    }

    private static void crown(ServerLevel realm, Court court)
    {
        List<FairyEntity> heirs = realm.getEntitiesOfClass(FairyEntity.class, WorldTree.populationCheckArea(),
                fairy -> fairy.isFemale() && !fairy.isQueen() && fairy.isAlive() && !LimboRegistry.isCreatureGhost(fairy));
        // Who reigned before - named in the welcome of the one who takes her place.
        String previous = court.name;
        FairyQueenEntity queen = new FairyQueenEntity(ModEntities.FAIRY_QUEEN.get(), realm);
        if (!heirs.isEmpty())
        {
            // She steps up: the same fairy, her own stone in her hand, now crowned.
            FairyEntity heir = heirs.get(realm.random.nextInt(heirs.size()));
            queen.moveTo(heir.getX(), heir.getY(), heir.getZ(), heir.getYRot(), 0.0f);
            queen.finalizeSpawn(realm, realm.getCurrentDifficultyAt(heir.blockPosition()), MobSpawnType.CONVERSION, null, null);
            queen.setItemSlot(EquipmentSlot.MAINHAND, heir.getMainHandItem().copy());
            if (heir.hasCustomName())
            {
                queen.setCustomName(Component.literal(FairyQueenNames.crown(heir.getCustomName().getString())));
            }
            heir.discard();
        }
        else
        {
            BlockPos spot = WorldTree.randomOpenAirInTree(realm, realm.random, 30);
            if (spot == null)
            {
                return;
            }
            queen.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, realm.random.nextFloat() * 360.0f, 0.0f);
            queen.finalizeSpawn(realm, realm.getCurrentDifficultyAt(spot), MobSpawnType.COMMAND, null, null);
        }
        realm.addFreshEntity(queen);
        court.queen = queen.getUUID();
        court.lastSeen = queen.blockPosition();
        court.name = queen.getDisplayName().getString();
        court.setDirty();

        realm.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, queen.getX(), queen.getY() + 1.0, queen.getZ(), 60, 0.6, 1.0, 0.6, 0.3);
        realm.playSound(null, queen.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0f, 1.2f);
        if (previous != null)
        {
            announce(realm, "fairy.magiccircles.court.crowned_after", Component.literal(previous), queen.getDisplayName());
        }
        else
        {
            announce(realm, "fairy.magiccircles.court.crowned", queen.getDisplayName());
        }
    }

    /** Who reigns, by name - "Zuzo, Queen of the Faye" - whether or not she is anywhere loaded. */
    public static Component queenName(ServerLevel realm)
    {
        Court court = court(realm);
        if (court.queen != null && realm.getEntity(court.queen) instanceof FairyQueenEntity queen)
        {
            return queen.getDisplayName();
        }
        return court.name != null ? Component.literal(court.name) : Component.translatable("fairy.magiccircles.dialogue.queen");
    }

    /** Every few seconds from the queen herself: where she is - and whether she is the one who reigns. */
    public static void noteQueen(ServerLevel level, FairyQueenEntity queen)
    {
        if (level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }
        Court court = court(level);
        if (court.queen == null || court.queen.equals(queen.getUUID()))
        {
            court.queen = queen.getUUID();
            court.lastSeen = queen.blockPosition();
            court.name = queen.getDisplayName().getString();
            court.setDirty();
            return;
        }
        // Another reigns already. There is only ever one - this one steps back down.
        FairyEntity fairy = new FairyEntity(ModEntities.FAIRY.get(), level);
        fairy.moveTo(queen.getX(), queen.getY(), queen.getZ(), queen.getYRot(), 0.0f);
        fairy.finalizeSpawn(level, level.getCurrentDifficultyAt(queen.blockPosition()), MobSpawnType.CONVERSION, null, null);
        fairy.setFemale(true);
        level.addFreshEntity(fairy);
        queen.discard();
    }

    public static void onQueenDied(ServerLevel level, FairyQueenEntity queen)
    {
        if (level.dimension() != ModDimensions.FAIRY_REALM)
        {
            return;
        }
        Court court = court(level);
        if (queen.getUUID().equals(court.queen))
        {
            court.queen = null;
            court.setDirty();
            level.sendParticles(ParticleTypes.END_ROD, queen.getX(), queen.getY() + 1.0, queen.getZ(), 80, 0.8, 1.2, 0.8, 0.1);
            announce(level, "fairy.magiccircles.court.fallen", queen.getDisplayName());
            FairyRealmWeather.rainFor(MOURNING_RAIN_TICKS);
        }
    }

    // ------------------------------------------------------------------
    // The souls of the fallen
    // ------------------------------------------------------------------

    private static final int MAX_SOULS = 6;
    /** On a fairy's soul: when it arrived at court, so the line keeps its order. */
    private static final String SOUL_ARRIVAL_TAG = "MagicCirclesCourtArrival";
    /** On a fairy's soul: its place in the line - see {@link #soulSpot} and {@code FairyEntity#isSoulAtCourt}. */
    public static final String SOUL_LINE_TAG = "MagicCirclesCourtLine";
    /** The line stands along the north wall, facing the throne - see {@link FairyThroneRoom#soulPlace}. */
    private static final float SOUL_YAW = FairyThroneRoom.SOUL_YAW;
    private static final int COURT_REACH = 16;

    /** A fairy has just died: its soul leaves its body where it fell and goes to stand at court. */
    public static void receiveSoul(FairyEntity fairy)
    {
        MinecraftServer server = fairy.getServer();
        ServerLevel realm = server != null ? server.getLevel(ModDimensions.FAIRY_REALM) : null;
        if (realm == null || fairy.isQueen())
        {
            return;
        }
        // Its body is of no further use - nobody leads a fairy's soul home; the queen does that.
        com.patrickma.magiccircles.entity.CreatureCorpseEntity.removeFor(fairy);
        fairy.getPersistentData().putLong(SOUL_ARRIVAL_TAG, realm.getGameTime());
        Vec3 spot = soulSpot(soulsAtCourt(realm).size());
        if (fairy.level() == realm)
        {
            fairy.teleportTo(spot.x, spot.y, spot.z);
            fairy.setYRot(SOUL_YAW);
            return;
        }
        fairy.changeDimension(realm, new net.minecraftforge.common.util.ITeleporter()
        {
            @Override
            public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity, ServerLevel destination,
                                                                             java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> defaultPortalInfo)
            {
                return new net.minecraft.world.level.portal.PortalInfo(spot, Vec3.ZERO, SOUL_YAW, 0.0f);
            }

            @Override
            public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld)
            {
                return false;
            }
        });
    }

    /** Where the {@code index}th soul in line stands: along the north wall, from the throne's end. */
    public static Vec3 soulSpot(int index)
    {
        return FairyThroneRoom.soulPlace(Math.min(index, MAX_SOULS));
    }

    private static List<FairyEntity> soulsAtCourt(ServerLevel realm)
    {
        List<FairyEntity> souls = realm.getEntitiesOfClass(FairyEntity.class,
                new net.minecraft.world.phys.AABB(FairyThroneRoom.CENTER).inflate(COURT_REACH),
                fairy -> !fairy.isQueen() && LimboRegistry.isCreatureGhost(fairy) && fairy.getPersistentData().contains(SOUL_ARRIVAL_TAG));
        souls.sort(java.util.Comparator.comparingLong(fairy -> fairy.getPersistentData().getLong(SOUL_ARRIVAL_TAG)));
        return souls;
    }

    /** Keeps the line in order, and never longer than {@value #MAX_SOULS} while a queen reigns to shorten it. */
    private static void tendSouls(ServerLevel realm)
    {
        List<FairyEntity> souls = soulsAtCourt(realm);
        if (souls.isEmpty())
        {
            return;
        }
        Court court = court(realm);
        FairyQueenEntity queen = court.queen != null && realm.getEntity(court.queen) instanceof FairyQueenEntity reigning
                && reigning.isAlive() ? reigning : null;
        while (souls.size() > MAX_SOULS && queen != null)
        {
            revive(realm, queen, souls.remove(0));
        }
        for (int i = 0; i < souls.size(); i++)
        {
            souls.get(i).getPersistentData().putInt(SOUL_LINE_TAG, i);
        }
    }

    /** Keeps a soul standing in its place in line - from {@code FairyEntity#customServerAiStep}. */
    public static void holdInLine(FairyEntity soul)
    {
        Vec3 spot = soulSpot(soul.getPersistentData().getInt(SOUL_LINE_TAG));
        soul.getNavigation().stop();
        soul.setNoGravity(false);
        soul.setYRot(SOUL_YAW);
        soul.yBodyRot = SOUL_YAW;
        if (soul.position().distanceToSqr(spot.x, soul.getY(), spot.z) > 0.09 || Math.abs(soul.getY() - spot.y) > 1.5)
        {
            soul.setPos(spot.x, spot.y, spot.z);
            soul.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** The queen gives a soul its life back, at her side. */
    private static void revive(ServerLevel realm, FairyQueenEntity queen, FairyEntity soul)
    {
        soul.getPersistentData().remove(SOUL_ARRIVAL_TAG);
        soul.getPersistentData().remove(SOUL_LINE_TAG);
        LimboRegistry.reviveCreature(soul);
        soul.setHealth(soul.getMaxHealth());
        Vec3 at = FairyThroneRoom.APPROACH;
        soul.teleportTo(at.x, at.y, at.z);
        realm.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1.0, at.z, 40, 0.5, 0.8, 0.5, 0.2);
        realm.playSound(null, queen.blockPosition(), com.patrickma.magiccircles.registry.ModSounds.FAIRY_CAST.get(),
                SoundSource.NEUTRAL, 1.0f, 0.8f);
        announce(realm, "fairy.magiccircles.court.revived", queen.getDisplayName());
    }

    private static void announce(ServerLevel realm, String key, Component... names)
    {
        for (ServerPlayer player : realm.players())
        {
            player.sendSystemMessage(Component.translatable(key, (Object[]) names).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        }
    }

    /** Draws a ghost to the queen, now and then, wherever it wandered - to hear what she has to offer. */
    private static void summonTheLost(MinecraftServer server, ServerLevel realm)
    {
        Court court = court(realm);
        if (court.queen == null || court.lastSeen == null)
        {
            return;
        }
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers()))
        {
            if (!LimboState.isGhost(player) || now - LimboState.crossedGameTime(player) < MIN_DEAD_TICKS)
            {
                continue;
            }
            if (player.level() == realm && player.blockPosition().closerThan(court.lastSeen, ALREADY_WITH_HER))
            {
                continue;
            }
            Long last = lastSummoned.get(player.getUUID());
            if ((last != null && now - last < SUMMON_COOLDOWN_TICKS) || realm.random.nextFloat() >= SUMMON_CHANCE)
            {
                continue;
            }
            lastSummoned.put(player.getUUID(), now);
            BlockPos at = court.lastSeen;
            player.teleportTo(realm, at.getX() + 2.5, at.getY() + 1.0, at.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.translatable("fairy.magiccircles.queen.summons")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        lastSummoned.clear();
        missingChecks = 0;
        FairyThroneRoom.releaseAllCourtiers();
    }
}
