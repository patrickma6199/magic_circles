package com.patrickma.magiccircles;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.patrickma.magiccircles.limbo.LimboRegistry;
import com.patrickma.magiccircles.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /revivify <player>} - pulls someone back out of the realm of the dead exactly as the
 * Ferryman would have, half health and half hunger and all. An operator's way out of a stranded
 * ghost, which matters because the ordinary way home depends on a Ferryman entity still existing.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModCommands
{
    private static final int PERMISSION_LEVEL = 2;

    private ModCommands()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        LiteralArgumentBuilder<CommandSourceStack> revivify = Commands.literal("revivify")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context ->
                        {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            boolean revived = LimboRegistry.revive(target);
                            if (revived)
                            {
                                context.getSource().sendSuccess(() -> Component.translatable(
                                        "commands.magiccircles.revivify.success", target.getDisplayName()), true);
                                target.displayClientMessage(
                                        Component.translatable("commands.magiccircles.revivify.revived"), false);
                            }
                            else
                            {
                                context.getSource().sendFailure(Component.translatable(
                                        "commands.magiccircles.revivify.not_dead", target.getDisplayName()));
                            }
                            return revived ? 1 : 0;
                        }));

        event.getDispatcher().register(revivify);

        event.getDispatcher().register(Commands.literal("magickit")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(context -> giveKit(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> giveKit(context.getSource(),
                                EntityArgument.getPlayer(context, "player")))));
    }

    /**
     * {@code /magickit [player]} - a testing loadout. Everything needed to exercise the mod end to
     * end without mining for it first: gear to survive on, and one of each thing the rituals
     * actually consume.
     *
     * <p>Anything that doesn't fit in the inventory is dropped at their feet rather than silently
     * discarded, which matters because this is a full set of armour plus a dozen-odd stacks.
     */
    private static int giveKit(CommandSourceStack source, ServerPlayer target)
    {
        List<ItemStack> kit = new ArrayList<>(List.of(
                new ItemStack(Items.NETHERITE_HELMET),
                new ItemStack(Items.NETHERITE_CHESTPLATE),
                new ItemStack(Items.NETHERITE_LEGGINGS),
                new ItemStack(Items.NETHERITE_BOOTS),
                new ItemStack(Items.NETHERITE_SWORD),
                new ItemStack(Items.NETHERITE_PICKAXE),
                new ItemStack(Items.COOKED_PORKCHOP, 64),
                new ItemStack(Items.TORCH, 64),
                new ItemStack(ModItems.LOST_WAYSTONE.get()),
                new ItemStack(ModItems.CHALK.get()),
                new ItemStack(ModItems.GOLD_CHALK.get()),
                new ItemStack(ModItems.PURPLE_CHALK.get()),
                new ItemStack(ModItems.RED_CHALK.get()),
                new ItemStack(ModItems.GREEN_CHALK.get()),
                new ItemStack(ModItems.BLACK_CHALK.get()),
                new ItemStack(ModItems.FAIRY_HORN.get()),
                new ItemStack(ModItems.ATHAME.get()),
                new ItemStack(ModItems.COOKED_MANA_WYRM.get(), 10)));

        // Two heartstones, each already holding a full thousand - an empty one is no use for
        // testing anything, and the summoning rite needs exactly that much.
        for (int i = 0; i < 2; i++)
        {
            ItemStack heartstone = new ItemStack(ModItems.HEARTSTONE.get());
            heartstone.getOrCreateTag().putInt("Mana", FULL_HEARTSTONE_MANA);
            kit.add(heartstone);
        }

        for (ItemStack stack : kit)
        {
            if (!target.getInventory().add(stack))
            {
                target.drop(stack, false);
            }
        }

        source.sendSuccess(() -> Component.translatable("commands.magiccircles.magickit.success",
                target.getDisplayName()), true);
        return 1;
    }

    /** What the Heartstone loot table calls full, and what {@code curse/SummoningRite} demands. */
    private static final int FULL_HEARTSTONE_MANA = 1000;
}
