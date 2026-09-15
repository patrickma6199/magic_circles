package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.entity.FairyEntity;
import com.patrickma.magiccircles.entity.FairyQueenEntity;
import com.patrickma.magiccircles.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.function.BooleanSupplier;

/**
 * The beat of fairy wings, for as long as their wearer is in the air: one seamless loop that follows
 * them around, soft while they hover and swelling and quickening as they speed up, the way a bee's
 * buzz does. Silent the moment they land - or take the throne, for the queen.
 *
 * <p>A fairy's starts with the fairy ({@code FairyEntity#tick}) and lasts as long as it does. A
 * Blessed player is heard only while hovering (see {@link FairyHover}) - flying, they keep vanilla's
 * own elytra rush; the flutter starts as they come to a standstill ({@link PlayerWingSounds}) and
 * lets go a moment after they move on. Client-side only: a client only ever knows about the fairies
 * it is allowed to see, so a fairy behind the veil is only heard by those who can see it.
 */
public class WingFlutterSound extends AbstractTickableSoundInstance
{
    private static final float HOVER_VOLUME = 0.3f;
    private static final float QUEEN_PITCH = 0.85f;
    /** How long a player's wings stay silent before the loop lets go - it starts again with the next flight. */
    private static final int FOLDED_TICKS = 40;

    private final LivingEntity wearer;
    private final BooleanSupplier onTheWing;
    private final float pitchScale;
    /** Null for a fairy's, which never ends; a player's is let go once folded for a while. */
    private final Runnable onEnd;
    private int foldedTicks;

    private WingFlutterSound(LivingEntity wearer, BooleanSupplier onTheWing, float pitchScale, Runnable onEnd)
    {
        super(ModSounds.FAIRY_FLUTTER.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.wearer = wearer;
        this.onTheWing = onTheWing;
        this.pitchScale = pitchScale;
        this.onEnd = onEnd;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.x = wearer.getX();
        this.y = wearer.getY() + 1.0;
        this.z = wearer.getZ();
    }

    public static void startFor(FairyEntity fairy)
    {
        BooleanSupplier flying = () -> fairy.isFlying() && !(fairy instanceof FairyQueenEntity queen && queen.isSeated());
        Minecraft.getInstance().getSoundManager().queueTickingSound(
                new WingFlutterSound(fairy, flying, fairy.isQueen() ? QUEEN_PITCH : 1.0f, null));
    }

    public static void startFor(Player player, Runnable onEnd)
    {
        Minecraft.getInstance().getSoundManager().queueTickingSound(
                new WingFlutterSound(player, () -> FairyHover.isHovering(player), 1.0f, onEnd));
    }

    @Override
    public void tick()
    {
        if (this.wearer.isRemoved())
        {
            end();
            return;
        }
        this.x = this.wearer.getX();
        this.y = this.wearer.getY() + 1.0;
        this.z = this.wearer.getZ();

        float targetVolume = 0.0f;
        float targetPitch = 1.0f;
        if (this.onTheWing.getAsBoolean())
        {
            this.foldedTicks = 0;
            double dx = this.wearer.getX() - this.wearer.xo;
            double dy = this.wearer.getY() - this.wearer.yo;
            double dz = this.wearer.getZ() - this.wearer.zo;
            float speed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            targetVolume = HOVER_VOLUME + Mth.clamp(speed * 2.5f, 0.0f, 0.5f);
            targetPitch = 0.9f + Mth.clamp(speed * 1.5f, 0.0f, 0.3f);
        }
        else if (this.onEnd != null && ++this.foldedTicks > FOLDED_TICKS)
        {
            end();
            return;
        }
        this.volume += (targetVolume - this.volume) * 0.2f;
        this.pitch += (targetPitch * this.pitchScale - this.pitch) * 0.2f;
    }

    private void end()
    {
        this.stop();
        if (this.onEnd != null)
        {
            this.onEnd.run();
        }
    }

    @Override
    public boolean canStartSilent()
    {
        return true;
    }

    @Override
    public boolean canPlaySound()
    {
        return !this.wearer.isSilent();
    }

}
