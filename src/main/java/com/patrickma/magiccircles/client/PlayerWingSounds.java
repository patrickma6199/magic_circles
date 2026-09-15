package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Starts a {@link WingFlutterSound} for every Blessed player in earshot the moment they come to a
 * hover on their wings (see {@link FairyHover}) - the local player's own included - and forgets them
 * again once the sound has let go, so the next hover starts a fresh one.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerWingSounds
{
    private static final Set<UUID> SOUNDING = new HashSet<>();
    private static ClientLevel lastLevel;

    private PlayerWingSounds()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel)
        {
            SOUNDING.clear();
            lastLevel = level;
        }
        if (level == null)
        {
            return;
        }
        for (Player player : level.players())
        {
            UUID id = player.getUUID();
            if (!SOUNDING.contains(id) && FairyHover.isHovering(player))
            {
                SOUNDING.add(id);
                WingFlutterSound.startFor(player, () -> SOUNDING.remove(id));
            }
        }
    }
}
