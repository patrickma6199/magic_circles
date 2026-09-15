package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.FairyTrades;
import com.patrickma.magiccircles.client.book.BookStyle;
import com.patrickma.magiccircles.network.FairyTradePacket;
import com.patrickma.magiccircles.network.ModNetworking;
import com.patrickma.magiccircles.network.QueenBargainPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A conversation with a fairy: who is speaking, what she says, and what she will trade you for
 * redstone - each offer with its price and a button that stays greyed out until you can pay it. It
 * sits on the Book of the Faye's own parchment and green binding, so the realm's paperwork all looks
 * of a piece.
 *
 * <p>Speaking to the Fairy Queen while you are dead is a different conversation: no wares, only her
 * bargain - your life back, in exchange for owing her.
 */
public class FairyDialogueScreen extends Screen
{
    private static final int WIDTH = 300;
    private static final int PAD = 12;
    private static final int ROW_HEIGHT = 24;
    private static final int TRADE_BUTTON_WIDTH = 64;
    private static final int PRICE_WIDTH = 56;
    private static final BookStyle STYLE = BookStyle.FAYE;
    private static final String[] FAIRY_LINES = {
            "fairy.magiccircles.dialogue.greeting.0", "fairy.magiccircles.dialogue.greeting.1",
            "fairy.magiccircles.dialogue.greeting.2", "fairy.magiccircles.dialogue.greeting.3"
    };
    private static final String[] QUEEN_LINES = {
            "fairy.magiccircles.dialogue.queen_greeting.0", "fairy.magiccircles.dialogue.queen_greeting.1"
    };
    private static final ItemStack REDSTONE = new ItemStack(Items.REDSTONE);

    private final int entityId;
    private final boolean bargain;
    /** The one thing this fairy sells - an index into {@link FairyTrades#ALL}. */
    private final int trade;
    private final Component line;
    private final List<Button> tradeButtons = new ArrayList<>();
    private List<FormattedCharSequence> lineParts = List.of();
    private Component heading = Component.empty();
    private int left;
    private int top;
    private int panelHeight;

    public FairyDialogueScreen(int entityId, boolean queen, boolean bargain, int trade)
    {
        super(Component.translatable(queen ? "fairy.magiccircles.dialogue.queen" : "fairy.magiccircles.dialogue.fairy"));
        this.entityId = entityId;
        this.bargain = bargain;
        this.trade = trade;
        String[] lines = queen ? QUEEN_LINES : FAIRY_LINES;
        this.line = Component.translatable(bargain ? "fairy.magiccircles.dialogue.bargain" : lines[new Random().nextInt(lines.length)]);
    }

    @Override
    protected void init()
    {
        // The queen is addressed by name - "Zuzo, Queen of the Faye" - the fairies stay nameless.
        Entity speaker = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(this.entityId) : null;
        this.heading = speaker != null && speaker.hasCustomName() ? speaker.getCustomName() : this.title;
        this.lineParts = this.font.split(this.line, WIDTH - PAD * 2);
        int body = this.bargain ? 28 : (hasTrade() ? ROW_HEIGHT : 0) + 16;
        this.panelHeight = PAD + 14 + this.lineParts.size() * 10 + 10 + body + 26 + PAD;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;

        int y = contentTop();
        this.tradeButtons.clear();
        if (this.bargain)
        {
            this.addRenderableWidget(Button.builder(Component.translatable("fairy.magiccircles.dialogue.accept"), button -> {
                ModNetworking.CHANNEL.sendToServer(new QueenBargainPacket(this.entityId));
                this.onClose();
            }).bounds(this.left + WIDTH / 2 - 108, y + 2, 104, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("fairy.magiccircles.dialogue.not_yet"),
                    button -> this.onClose()).bounds(this.left + WIDTH / 2 + 4, y + 2, 104, 20).build());
        }
        else
        {
            if (hasTrade())
            {
                Button button = Button.builder(Component.translatable("fairy.magiccircles.dialogue.trade"),
                                pressed -> ModNetworking.CHANNEL.sendToServer(new FairyTradePacket(this.entityId, this.trade)))
                        .bounds(this.left + WIDTH - PAD - TRADE_BUTTON_WIDTH, y + 2, TRADE_BUTTON_WIDTH, 20)
                        .build();
                this.tradeButtons.add(this.addRenderableWidget(button));
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("fairy.magiccircles.dialogue.farewell"),
                button -> this.onClose()).bounds(this.left + WIDTH / 2 - 40, this.top + this.panelHeight - PAD - 20, 80, 20).build());
        refreshButtons();
    }

    private boolean hasTrade()
    {
        return this.trade >= 0 && this.trade < FairyTrades.ALL.size();
    }

    private int contentTop()
    {
        return this.top + PAD + 14 + this.lineParts.size() * 10 + 10;
    }

    @Override
    public void tick()
    {
        super.tick();
        Entity fairy = this.minecraft == null || this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.entityId);
        // She flew off, or you walked away - the conversation is over.
        if (fairy == null || !fairy.isAlive() || this.minecraft.player == null
                || fairy.distanceTo(this.minecraft.player) > FairyTrades.TALK_RANGE + 2.0)
        {
            this.onClose();
            return;
        }
        refreshButtons();
    }

    private void refreshButtons()
    {
        if (this.minecraft == null || this.minecraft.player == null)
        {
            return;
        }
        int held = FairyTrades.redstoneHeld(this.minecraft.player);
        for (Button button : this.tradeButtons)
        {
            button.active = held >= FairyTrades.ALL.get(this.trade).cost();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        graphics.fill(this.left - 3, this.top - 3, this.left + WIDTH + 3, this.top + this.panelHeight + 3, STYLE.borderDark());
        graphics.fill(this.left - 2, this.top - 2, this.left + WIDTH + 2, this.top + this.panelHeight + 2, STYLE.borderMid());
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + this.panelHeight, STYLE.page());

        graphics.drawString(this.font, this.heading, this.left + PAD, this.top + PAD, STYLE.heading(), false);
        int y = this.top + PAD + 14;
        for (FormattedCharSequence part : this.lineParts)
        {
            graphics.drawString(this.font, part, this.left + PAD, y, STYLE.ink(), false);
            y += 10;
        }

        if (!this.bargain)
        {
            int rows = contentTop();
            if (hasTrade())
            {
                FairyTrades.Trade offer = FairyTrades.ALL.get(this.trade);
                ItemStack offered = offer.result().get();
                graphics.renderItem(offered, this.left + PAD, rows + 4);
                graphics.renderItemDecorations(this.font, offered, this.left + PAD, rows + 4);
                graphics.drawString(this.font, offered.getHoverName(), this.left + PAD + 22, rows + 8, STYLE.ink(), false);
                int priceX = this.left + WIDTH - PAD - TRADE_BUTTON_WIDTH - PRICE_WIDTH;
                graphics.renderItem(REDSTONE, priceX, rows + 4);
                graphics.drawString(this.font, "× " + offer.cost(), priceX + 18, rows + 8, STYLE.ink(), false);
            }
            int held = this.minecraft == null || this.minecraft.player == null ? 0 : FairyTrades.redstoneHeld(this.minecraft.player);
            graphics.drawString(this.font, Component.translatable("fairy.magiccircles.dialogue.carrying", held),
                    this.left + PAD, rows + (hasTrade() ? ROW_HEIGHT : 0) + 4, STYLE.faint(), false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
