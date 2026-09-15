package com.patrickma.magiccircles.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Vanilla's own "should this player see this entity?" re-check, on its non-public tracker class. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityInvoker
{
    @Invoker("updatePlayer")
    void magiccircles$updatePlayer(ServerPlayer player);

    @Invoker("updatePlayers")
    void magiccircles$updatePlayers(List<ServerPlayer> players);
}
