package com.patrickma.magiccircles;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.patrickma.magiccircles.limbo.LimboRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    }
}
