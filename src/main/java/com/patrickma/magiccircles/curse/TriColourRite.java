package com.patrickma.magiccircles.curse;

import com.patrickma.magiccircles.block.RuneColor;
import com.patrickma.magiccircles.item.AthameItem;
import com.patrickma.magiccircles.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * The split rings: six runes of black, and the other six shared three-and-three between two
 * colours, in any arrangement.
 *
 * <p>Where a curse follows the blood on the knife to someone else, these are worked on the caster -
 * and paid for exactly the same way ({@link DarkRites#payThePrice}). Each pairing is two of the
 * Wellspring's gifts combined and turned inward. New ones are just new entries here; the ring
 * shape and the price are shared.
 */
public enum TriColourRite
{
    /**
     * Red and Green - Sylvaine's mercy and the Gleaner's fortune, both turned to looking. The caster
     * sees everything behind the veil without crossing it: not in the realm, unable to fly there,
     * unable to touch it, and untouchable by it. See {@link Deathsight}.
     */
    DEATHSIGHT(RuneColor.RED, RuneColor.GREEN, "deathsight");

    private static final int BLACK_RUNES = 6;
    private static final int PARTNER_RUNES = 3;
    /** Three minutes of looking across. */
    private static final int DEATHSIGHT_TICKS = 20 * 60 * 3;

    private final RuneColor first;
    private final RuneColor second;
    private final String key;

    TriColourRite(RuneColor first, RuneColor second, String key)
    {
        this.first = first;
        this.second = second;
        this.key = key;
    }

    /** The split ring this is, or null - six black plus exactly three each of one pairing's colours. */
    public static TriColourRite match(Map<RuneColor, Integer> counts)
    {
        if (counts.size() != 3 || counts.getOrDefault(RuneColor.BLACK, 0) != BLACK_RUNES)
        {
            return null;
        }
        for (TriColourRite rite : values())
        {
            if (counts.getOrDefault(rite.first, 0) == PARTNER_RUNES
                    && counts.getOrDefault(rite.second, 0) == PARTNER_RUNES)
            {
                return rite;
            }
        }
        return null;
    }

    public boolean cast(ServerLevel level, Player caster, BlockPos center, ItemStack athame)
    {
        DarkRites.payThePrice(level, caster, center);
        AthameItem.sign(athame, caster);

        switch (this)
        {
            case DEATHSIGHT -> caster.addEffect(new MobEffectInstance(ModEffects.DEATHSIGHT.get(),
                    DEATHSIGHT_TICKS, 0, false, false, true));
        }

        caster.displayClientMessage(Component.translatable("rite.magiccircles." + key + ".cast")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        return true;
    }
}
