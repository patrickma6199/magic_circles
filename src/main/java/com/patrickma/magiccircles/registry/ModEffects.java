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
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xB89CFF)
            {
            });

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
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x3A0A4A)
            {
            });

    private ModEffects()
    {
    }
}
