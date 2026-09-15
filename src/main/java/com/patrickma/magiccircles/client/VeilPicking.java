package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.limbo.GhostVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * What your crosshair can land on when you can see across the veil.
 *
 * <p>Deathsight shows the living the other side, but they still can't touch it (see {@code
 * curse/Deathsight}) - and a ghost standing between you and a zombie used to catch your swing
 * anyway, so the zombie was never hit. Anything on the other side of the veil from you is now
 * simply not something your crosshair stops on; the swing goes straight through to whatever is on
 * your own side. It works the same way round for a rite caster, whose side is the dead's.
 *
 * <p>Purely the local player's aim, asked from {@code mixin/LivingEntityMixin} on the client only -
 * nothing about what the server lets hit what changes.
 */
public final class VeilPicking
{
    private VeilPicking()
    {
    }

    public static boolean hiddenFromLocalPlayer(Entity entity)
    {
        Player local = Minecraft.getInstance().player;
        if (local == null || entity == local || entity instanceof com.patrickma.magiccircles.entity.FairyQueenEntity)
        {
            // The Fairy Queen can be reached from either side of the veil.
            return false;
        }
        return GhostVisibility.appearsBehindVeil(entity) != GhostVisibility.appearsBehindVeil(local);
    }
}
