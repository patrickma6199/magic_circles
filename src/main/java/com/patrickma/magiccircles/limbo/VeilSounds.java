package com.patrickma.magiccircles.limbo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Sounds made from behind the veil, heard only from behind it.
 *
 * <p>Vanilla sends an entity's sounds - its footsteps, its bark, its hurt cry - to everyone in
 * earshot, with no regard for who can actually see it. So a dead dog nobody living could see could
 * still be heard padding around. The veil already decides who is told an entity exists (see {@link
 * Veil#visibleTo}); this sends that entity's sounds to exactly the same people, and nobody else.
 * Called from {@code mixin/EntityMixin} and {@code mixin/PlayerMixin}, in place of vanilla's
 * broadcast.
 */
public final class VeilSounds
{
    private VeilSounds()
    {
    }

    /** {@code except} is the one player vanilla would also skip - a player's own client plays their own sounds. */
    public static void play(Entity source, SoundEvent sound, float volume, float pitch, @Nullable Entity except)
    {
        if (source.isSilent() || !(source.level() instanceof ServerLevel level))
        {
            return;
        }
        double range = sound.getRange(volume);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                source.getSoundSource(), source.getX(), source.getY(), source.getZ(), volume, pitch,
                level.getRandom().nextLong());
        for (ServerPlayer listener : level.players())
        {
            if (listener != except && listener.distanceToSqr(source) <= range * range && Veil.visibleTo(source, listener))
            {
                listener.connection.send(packet);
            }
        }
    }
}
