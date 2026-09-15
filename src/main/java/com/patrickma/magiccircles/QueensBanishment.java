package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nobody in creative mode is suffered in the Fairy Realm. The moment one is found there - arriving by
 * portal or waystone, logging back in there, or turning to creative once already inside - the queen
 * strikes them with lightning and tells them, and them alone, why: their kind drove her people out
 * of the world above, and in her own realm it is she who rules, not them. A moment later they are
 * cast back out to the overworld (see {@link FairyPortalManager#banishToOverworld}).
 *
 * <p>Deliberately never mentioned in either book, nor anywhere else in the lore: it is something to
 * be found out.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QueensBanishment
{
    private static final int CHECK_INTERVAL_TICKS = 10;
    /** Long enough for the lightning to land and her words to be read before they are gone. */
    private static final int BANISH_DELAY_TICKS = 30;

    /** Who has been struck and warned, and the game tick they are cast out on. */
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private QueensBanishment()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        UUID id = player.getUUID();
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0 && !PENDING.containsKey(id))
        {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!player.isCreative() || !level.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            PENDING.remove(id);
            return;
        }
        long now = level.getGameTime();
        Long due = PENDING.get(id);
        if (due == null)
        {
            PENDING.put(id, now + BANISH_DELAY_TICKS);
            smite(level, player);
        }
        else if (now >= due)
        {
            PENDING.remove(id);
            FairyPortalManager.banishToOverworld(player);
        }
    }

    /** Her lightning, and her words - to them alone. The bolt is only light and thunder; nothing around them burns. */
    private static void smite(ServerLevel level, ServerPlayer player)
    {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null)
        {
            bolt.moveTo(player.getX(), player.getY(), player.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        player.sendSystemMessage(Component.translatable("fairy.magiccircles.queen.banish", FairyCourt.queenName(level))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PENDING.clear();
    }
}
