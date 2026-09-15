package com.patrickma.magiccircles.curse;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * One curse, holding. Bound to the blade that laid it rather than to the caster - see {@link
 * CurseRegistry} for why, and for how it comes undone.
 */
public final class ActiveCurse
{
    private final CurseKind kind;
    private final UUID victim;
    private final UUID caster;
    private final UUID bladeId;
    private final ResourceKey<Level> dimension;
    private final BlockPos anchor;

    public ActiveCurse(CurseKind kind, UUID victim, UUID caster, UUID bladeId,
                       ResourceKey<Level> dimension, BlockPos anchor)
    {
        this.kind = kind;
        this.victim = victim;
        this.caster = caster;
        this.bladeId = bladeId;
        this.dimension = dimension;
        this.anchor = anchor;
    }

    public CurseKind kind()
    {
        return kind;
    }

    public UUID victim()
    {
        return victim;
    }

    public UUID caster()
    {
        return caster;
    }

    public UUID bladeId()
    {
        return bladeId;
    }

    public ResourceKey<Level> dimension()
    {
        return dimension;
    }

    /** Where the ring was drawn - the prison's own centre, and the spot everything else is measured from. */
    public BlockPos anchor()
    {
        return anchor;
    }

    public ServerLevel level(MinecraftServer server)
    {
        return server.getLevel(dimension);
    }

    public LivingEntity findVictim(MinecraftServer server)
    {
        ServerLevel level = level(server);
        if (level == null)
        {
            return null;
        }
        Entity entity = level.getEntity(victim);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** Undoes whatever the curse was physically holding in place. Called once, when the blade is lost. */
    public void lift(MinecraftServer server)
    {
        if (kind == CurseKind.IMPRISONMENT)
        {
            ImprisonmentCurse.tearDownPrison(this, server);
        }
        LivingEntity target = findVictim(server);
        if (target != null)
        {
            target.sendSystemMessage(Component.translatable("curse.magiccircles.lifted"));
        }
    }
}
