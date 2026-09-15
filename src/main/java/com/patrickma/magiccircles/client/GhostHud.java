package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.limbo.GhostVisibility;
import com.patrickma.magiccircles.limbo.Poltergeist;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * What a ghost sees of their own state: a thin white vignette at the edges of the screen for as
 * long as they are dead (see {@code tools/gen_ghost_vignette.py}), and - for a poltergeist - its
 * two powers laid out above the hotbar, each with its icon (see {@code
 * tools/gen_poltergeist_icons.py}), the key it is bound to, and how long it has left to gather
 * itself. Presses are sent by {@link PoltergeistInput}.
 *
 * <p>The client is never told about limbo directly, but it doesn't need to be for the vignette: a
 * ghost is a spectator on the veil team, and both of those are already synced. Nobody else is both
 * - a rite caster stays in survival, and Deathsight puts the living on the team without making them
 * spectators. Being a poltergeist, and the cooldowns, the server does send - see {@link
 * PoltergeistClientState}.
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GhostHud
{
    private static final ResourceLocation VIGNETTE =
            new ResourceLocation(MagicCircles.MOD_ID, "textures/misc/ghost_vignette.png");
    private static final float VIGNETTE_ALPHA = 0.55f;

    private static final ResourceLocation RAIN_ICON =
            new ResourceLocation(MagicCircles.MOD_ID, "textures/gui/poltergeist_rain.png");
    private static final ResourceLocation SKELETON_ICON =
            new ResourceLocation(MagicCircles.MOD_ID, "textures/gui/poltergeist_skeleton.png");
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 8;
    private static final int ICON_SIZE = 16;
    /** Lifts the slots clear of where the spectator hotbar appears when it is opened. */
    private static final int BOTTOM_MARGIN = 34;
    private static final int SLOT_FILL = 0x90000000;
    private static final int READY_BORDER = 0xFFB77BE0;
    private static final int RECHARGING_BORDER = 0xFF4A4050;
    private static final int COOLDOWN_SHADE = 0xB0000000;
    private static final int READY_LABEL = 0xE8D8F8;
    private static final int RECHARGING_LABEL = 0x8A8090;

    public static final KeyMapping CALL_RAIN = new KeyMapping("key.magiccircles.poltergeist_rain",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.magiccircles");
    public static final KeyMapping RAISE_SKELETON = new KeyMapping("key.magiccircles.poltergeist_skeleton",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.magiccircles");

    private GhostHud()
    {
    }

    public static boolean isGhost(Player player)
    {
        return player.isSpectator() && GhostVisibility.isOnVeilTeam(player);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event)
    {
        event.register(CALL_RAIN);
        event.register(RAISE_SKELETON);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event)
    {
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "ghost_vignette", GhostHud::renderVignette);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "poltergeist_abilities", GhostHud::renderAbilities);
    }

    private static void renderVignette(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Player player = Minecraft.getInstance().player;
        if (player == null || !isGhost(player))
        {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0f, 1.0f, 1.0f, VIGNETTE_ALPHA);
        graphics.blit(VIGNETTE, 0, 0, 0.0f, 0.0f, width, height, width, height);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void renderAbilities(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !isGhost(player) || !PoltergeistClientState.isActive())
        {
            return;
        }
        int left = (width - (SLOT_SIZE * 2 + SLOT_GAP)) / 2;
        int top = height - SLOT_SIZE - BOTTOM_MARGIN;
        drawSlot(graphics, mc, left, top, RAIN_ICON, CALL_RAIN,
                PoltergeistClientState.rainRemaining(partialTick), Poltergeist.RAIN_COOLDOWN_TICKS);
        drawSlot(graphics, mc, left + SLOT_SIZE + SLOT_GAP, top, SKELETON_ICON, RAISE_SKELETON,
                PoltergeistClientState.skeletonRemaining(partialTick), Poltergeist.SKELETON_COOLDOWN_TICKS);
    }

    /**
     * One power: a dark slot with its icon, bordered in violet while it is ready. While it recharges
     * the icon is shaded - the shade shrinking away as it recovers - with the seconds left over it.
     * The key it is bound to sits underneath, whatever the player has rebound it to.
     */
    private static void drawSlot(GuiGraphics graphics, Minecraft mc, int x, int y, ResourceLocation icon,
                                 KeyMapping key, float remainingTicks, int cooldownTicks)
    {
        boolean ready = remainingTicks <= 0.0f;
        int border = ready ? READY_BORDER : RECHARGING_BORDER;
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_FILL);
        graphics.fill(x, y, x + SLOT_SIZE, y + 1, border);
        graphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, border);
        graphics.fill(x, y, x + 1, y + SLOT_SIZE, border);
        graphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, border);

        int iconX = x + (SLOT_SIZE - ICON_SIZE) / 2;
        int iconY = y + (SLOT_SIZE - ICON_SIZE) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(icon, iconX, iconY, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.disableBlend();

        if (!ready)
        {
            int shaded = Mth.ceil(ICON_SIZE * Mth.clamp(remainingTicks / cooldownTicks, 0.0f, 1.0f));
            graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + shaded, COOLDOWN_SHADE);
            String seconds = String.valueOf(Mth.ceil(remainingTicks / 20.0f));
            graphics.drawString(mc.font, seconds, x + (SLOT_SIZE - mc.font.width(seconds)) / 2,
                    y + (SLOT_SIZE - mc.font.lineHeight) / 2 + 1, 0xFFFFFF, true);
        }

        Component label = key.getTranslatedKeyMessage();
        graphics.drawString(mc.font, label, x + (SLOT_SIZE - mc.font.width(label)) / 2, y + SLOT_SIZE + 2,
                ready ? READY_LABEL : RECHARGING_LABEL, true);
    }
}
