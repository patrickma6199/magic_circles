package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The "Magic Circles" tab in the creative inventory. Add new items to it in {@code displayItems}. */
public class ModCreativeTabs
{
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MagicCircles.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAGIC_CIRCLES_TAB = CREATIVE_MODE_TABS.register("magic_circles_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.magiccircles"))
                    .icon(() -> ModItems.ARCANE_DUST.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ARCANE_DUST.get());
                        output.accept(ModItems.THELIA_DUST.get());
                        output.accept(ModItems.CHALK.get());
                        output.accept(ModItems.GOLD_CHALK.get());
                        output.accept(ModItems.PURPLE_CHALK.get());
                        output.accept(ModItems.RED_CHALK.get());
                        output.accept(ModItems.GREEN_CHALK.get());
                        output.accept(ModItems.BLACK_CHALK.get());
                        output.accept(ModItems.ATHAME.get());
                        output.accept(ModItems.HEARTSTONE.get());
                        output.accept(ModItems.FAIRY_HORN.get());
                        output.accept(ModItems.FAIRY_FOSSIL_ORE.get());
                        output.accept(ModItems.LOST_WAYSTONE.get());
                        output.accept(ModItems.LIVING_WOOD_LOG.get());
                        output.accept(ModItems.LIVING_WOOD_LEAVES.get());
                        output.accept(ModItems.PIXIE_SPAWN_EGG.get());
                        output.accept(ModItems.DREAM_ELK_SPAWN_EGG.get());
                        output.accept(ModItems.RAW_MANA_WYRM.get());
                        output.accept(ModItems.COOKED_MANA_WYRM.get());
                        output.accept(ModItems.BOOK_OF_THE_FAYE.get());
                    })
                    .build());
}
