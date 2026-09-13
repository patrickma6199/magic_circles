package com.patrickma.magiccircles.limbo;

import com.patrickma.magiccircles.entity.FerrymanEntity;
import com.patrickma.magiccircles.entity.PlayerCorpseEntity;
import com.patrickma.magiccircles.registry.ModEffects;
import com.patrickma.magiccircles.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Forbidden magic's own ritual - right-click the center of a complete Black-chalk ring with an
 * Athame (see {@code item/AthameItem}) to trigger this. "You are drained of half your health and a
 * ferryman spawns and kills you in one hit but you keep your items, experience, hunger, and health
 * and enter the other side."
 *
 * <p>Crossing over here is the same state an ordinary death produces - invisible to the living,
 * flying, walking through walls (see {@link LimboRegistry#ghostifyPlayer}) - with two deliberate
 * differences. The caster keeps everything they are carrying, because the phantoms that hunt a
 * Marked player are meant to be fought off; and for the same reason they stay damageable, unlike an
 * ordinary ghost, so that fight is real. The body they step out of stays behind exactly where the
 * rite was performed, and is where they end up again when the Ferryman is dismissed.
 */
public final class RiteOfPassage
{
    private static final float HEALTH_COST_FRACTION = 0.5f;

    private RiteOfPassage()
    {
    }

    public static void begin(ServerLevel level, Player playerRaw, BlockPos ringCenter)
    {
        if (!(playerRaw instanceof ServerPlayer player) || LimboState.isInLimbo(player))
        {
            return;
        }

        // The "killing blow" - the cost the rite asks for, without this being an actual death:
        // health is simply set, never dropped to zero and revived.
        player.setHealth(Math.max(1.0f, player.getHealth() * HEALTH_COST_FRACTION));
        level.playSound(null, ringCenter, SoundEvents.WITHER_HURT, player.getSoundSource(), 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.02);

        FerrymanEntity ferryman = new FerrymanEntity(ModEntities.FERRYMAN.get(), level);
        ferryman.moveTo(player.getX() + 1.0, player.getY(), player.getZ(), player.getYRot(), 0.0f);
        ferryman.setCasterUuid(player.getUUID());
        // He is behind the veil by his very type, so the living are never sent him at all; this is
        // only so he renders translucent to those who can see him, like everything else over there.
        ferryman.addEffect(GhostVisibility.invisibility());
        GhostVisibility.join(ferryman);
        level.addFreshEntity(ferryman);

        // The body left behind. Nothing is captured into it - the caster keeps their whole
        // inventory - so this is purely the shell they walked out of, and the spot they return to.
        PlayerCorpseEntity body = new PlayerCorpseEntity(ModEntities.PLAYER_CORPSE.get(), level);
        body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        body.captureFrom(player, false);
        level.addFreshEntity(body);

        player.addEffect(new MobEffectInstance(ModEffects.MARKED_BY_THE_DARK.get(), Integer.MAX_VALUE, 0, false, false));
        LimboState.beginRite(player, ferryman.getUUID(), body.getUUID(), level.dimension(), level.getGameTime());
        LimboRegistry.ghostifyPlayer(player, true);
        player.teleportTo(player.getX(), player.getY() + 2.0, player.getZ());
    }
}
