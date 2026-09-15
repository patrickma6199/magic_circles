package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every status effect the mod adds. */
public class ModEffects
{
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MagicCircles.MOD_ID);

    /**
     * Granted by eating a Mana Wyrm, raw or cooked (see {@code ModItems#RAW_MANA_WYRM}/{@code
     * COOKED_MANA_WYRM}) - has no effect of its own beyond existing. Its only purpose is as a
     * gate: for as long as a player has this, they can right-click a Heart Core empty-handed to
     * absorb its ring into a Heartstone, and right-click with a commanded Heartstone in hand to
     * cast its spell (see {@code item/HeartstoneItem} and {@code CommandedSpellCasting}) - both
     * checked live via {@code player.hasEffect(ModEffects.BLESSED_BY_WELLSPRING)} rather than
     * anything stored on the item itself, so the ability genuinely lapses the moment the buff
     * runs out, mid-cast or not.
     */
    public static final RegistryObject<MobEffect> BLESSED_BY_WELLSPRING = EFFECTS.register("blessed_by_wellspring",
            () -> new LastingEffect(MobEffectCategory.BENEFICIAL, 0xB89CFF));

    /**
     * The mark forbidden magic leaves on whoever performs {@code limbo/RiteOfPassage} - checked
     * live via {@code player.hasEffect(...)}, the same pattern {@link #BLESSED_BY_WELLSPRING}
     * already uses, by {@code block/HeartCoreBlock#interact} to refuse ordinary spellcasting
     * ("the heartstone refuses to sing for you") and by {@code limbo/MarkedByTheDarkManager} to
     * periodically send phantoms after the marked player. No duration of its own - it doesn't
     * expire on a timer, only ever removed by {@link MarkedByTheDarkManager} once 10 continuous
     * seconds swimming in Wellspring Water is confirmed.
     */
    public static final RegistryObject<MobEffect> MARKED_BY_THE_DARK = EFFECTS.register("marked_by_the_dark",
            () -> new LastingEffect(MobEffectCategory.HARMFUL, 0x3A0A4A));

    /**
     * Sight across the veil without crossing it - from the red, green and black split ring (see
     * {@code curse/TriColourRite}, {@code curse/Deathsight}). Checked live, like the others: the
     * moment it lapses, the other side stops being sent to the caster.
     */
    public static final RegistryObject<MobEffect> DEATHSIGHT = EFFECTS.register("deathsight",
            () -> new LastingEffect(MobEffectCategory.NEUTRAL, 0x6B2FA0));

    /**
     * Owed to the Fairy Queen, for being sent back from the dead (see {@code FairyTrades#acceptBargain}).
     * What the debt costs is still to be decided - for now it is simply carried, and it doesn't expire.
     */
    public static final RegistryObject<MobEffect> INDEBTED = EFFECTS.register("indebted",
            () -> new LastingEffect(MobEffectCategory.NEUTRAL, 0xC9A24B));

    /**
     * Every effect here means something - earned, cursed or bargained for - and none of it is the
     * kind of thing a bucket of milk should wash off. Forge asks each effect what cures it; these
     * answer: nothing.
     */
    private static class LastingEffect extends MobEffect
    {
        LastingEffect(MobEffectCategory category, int color)
        {
            super(category, color);
        }

        @Override
        public java.util.List<net.minecraft.world.item.ItemStack> getCurativeItems()
        {
            return new java.util.ArrayList<>();
        }
    }

    private ModEffects()
    {
    }
}
