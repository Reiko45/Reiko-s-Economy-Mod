package com.reiko.economy.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.item.BankCard;
import com.reiko.economy.item.Cards;
import com.reiko.economy.network.ClientBalance;
import com.reiko.economy.shop.Payment;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Shared look and behaviour for the shop screens: a vanilla-style panel drawn in code (no texture files),
 * a status line for the server's replies, and text boxes that don't close the screen when you type "E".
 */
public abstract class ShopScreenBase<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected static final int LABEL_COLOR = 0x404040;
    protected static final int MUTED_COLOR = 0x6B6B6B;
    private static final int SUCCESS_COLOR = 0x1B7A2B;
    private static final int FAILURE_COLOR = 0xB3261E;
    private static final Pattern MONEY_INPUT = Pattern.compile("\\d{0,13}(\\.\\d{0,2})?");
    protected static final Predicate<String> MONEY_FILTER = text -> MONEY_INPUT.matcher(text).matches();
    protected static final Predicate<String> PIN_FILTER = text -> text.matches("\\d{0,4}");

    @Nullable
    private Component feedback;
    private boolean feedbackSuccess;
    /** The card the player picked to pay with, when they carry more than one. */
    @Nullable
    private UUID chosenCardId;
    @Nullable
    private Button cardSwitch;

    protected ShopScreenBase(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
    }

    /** Shows the server's reply to the last action. */
    public void showFeedback(Component message, boolean success) {
        this.feedback = message;
        this.feedbackSuccess = success;
    }

    /** Called when Enter is pressed in a text box. */
    protected void onEnter() {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    protected void drawFeedback(GuiGraphics graphics, int x, int y, int maxWidth) {
        drawFeedback(graphics, x, y, maxWidth, 2);
    }

    protected void drawFeedback(GuiGraphics graphics, int x, int y, int maxWidth, int maxLines) {
        if (feedback == null) {
            return;
        }
        int color = feedbackSuccess ? SUCCESS_COLOR : FAILURE_COLOR;
        var lines = font.split(feedback, maxWidth);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
    }

    protected Component balanceLine() {
        return ClientBalance.isKnown()
                ? Component.translatable("gui.reikoeconomy.balance", EconomyService.INSTANCE.format(ClientBalance.get()))
                : Component.empty();
    }

    protected static String format(long amount) {
        return EconomyService.INSTANCE.format(amount);
    }

    /** A 4-digit PIN box that shows stars instead of the digits. */
    protected EditBox createPinBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 34, 16, Component.translatable("gui.reikoeconomy.pin"));
        box.setMaxLength(4);
        box.setFilter(PIN_FILTER);
        box.setFormatter((text, firstChar) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        box.setHint(Component.translatable("gui.reikoeconomy.pin").withColor(MUTED_COLOR));
        return box;
    }

    /** "Debit •••• 4821 (Steve)" or "Credit •••• 1234 (Steve)", or a note that the player has no card. */
    protected Component cardLine(ItemStack card) {
        BankCard data = Cards.data(card);
        if (data == null) {
            return Component.translatable("gui.reikoeconomy.no_card");
        }
        return Component.translatable(Cards.isCredit(card) ? "gui.reikoeconomy.credit_line" : "gui.reikoeconomy.debit_line",
                data.lastFour(), data.ownerName());
    }

    /** The card that pays: the one the player picked with the switch button, or the first one they carry. */
    protected ItemStack chosenCard() {
        List<ItemStack> cards = Cards.carried(minecraft.player);
        for (ItemStack card : cards) {
            if (Cards.data(card).cardId().equals(chosenCardId)) {
                return card;
            }
        }
        return cards.isEmpty() ? ItemStack.EMPTY : cards.get(0);
    }

    /** A card payment with the chosen card and the typed PIN. */
    protected Payment cardPayment(String pin) {
        BankCard data = Cards.data(chosenCard());
        return Payment.card(pin, data == null ? null : data.cardId());
    }

    /** Adds a small button that switches between the player's cards. It only shows when they carry two or more. */
    protected void addCardSwitch(int x, int y) {
        cardSwitch = addRenderableWidget(Button.builder(Component.literal("\u21c4"), button -> nextCard())
                .bounds(x, y, 14, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.switch_card")))
                .build());
        cardSwitch.visible = Cards.carried(minecraft.player).size() > 1;
    }

    private void nextCard() {
        List<ItemStack> cards = Cards.carried(minecraft.player);
        if (cards.size() > 1) {
            int index = cards.indexOf(chosenCard());
            chosenCardId = Cards.data(cards.get((index + 1) % cards.size())).cardId();
        }
    }

    /** The card line, cut to fit {@code width} and the switch button when it shows. */
    protected void drawCardLine(GuiGraphics graphics, int x, int y, int width) {
        int room = cardSwitch != null && cardSwitch.visible ? width - 18 : width;
        graphics.drawString(font, fit(cardLine(chosenCard()), room), x, y, LABEL_COLOR, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (cardSwitch != null) {
            cardSwitch.visible = Cards.carried(minecraft.player).size() > 1;
        }
    }

    /**
     * Adds a row of small tab buttons along the top of a screen. The selected one is drawn with a gold frame by
     * {@link #drawSelectedTab}, rather than greyed out, so it doesn't look disabled.
     */
    protected Button[] addTabs(int x, int y, String[] keys, int[] widths, java.util.function.IntConsumer select) {
        Button[] tabs = new Button[keys.length];
        int left = x;
        for (int i = 0; i < keys.length; i++) {
            int tab = i;
            tabs[i] = addRenderableWidget(Button.builder(Component.translatable(keys[i]), button -> select.accept(tab))
                    .bounds(leftPos + left, topPos + y, widths[i], 12).build());
            left += widths[i] + 2;
        }
        return tabs;
    }

    /** A gold frame around the selected tab. Call from {@code renderLabels}, which draws on top of the buttons. */
    protected void drawSelectedTab(GuiGraphics graphics, Button tab) {
        if (tab.visible) {
            graphics.renderOutline(tab.getX() - leftPos - 1, tab.getY() - topPos - 1, tab.getWidth() + 2, tab.getHeight() + 2, 0xFFFFC83D);
        }
    }

    /** Cuts {@code text} to {@code width} pixels, ending in "..." when it doesn't fit. */
    protected Component fit(Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        FormattedText cut = font.substrByWidth(text, width - font.width("..."));
        return Component.literal(cut.getString() + "...").withStyle(text.getStyle());
    }

    /** Sales tax on a purchase of {@code amount}, from the server's settings (synced to the client). */
    protected static long salesTax(long amount) {
        return EconomyConfig.taxRates().salesTax(amount);
    }

    /** "Buy: $5.00 per bundle", or "Buy: $5.00 + 5% tax" when there's a sales tax. */
    protected static Component buyPriceLine(long price) {
        long basisPoints = EconomyConfig.taxRates().salesBasisPoints();
        return basisPoints > 0
                ? Component.translatable("gui.reikoeconomy.buy_price_tax", format(price), TaxRates.percentText(basisPoints))
                : Component.translatable("gui.reikoeconomy.buy_price", format(price));
    }

    /** Sets the Card and Cash buttons to the total with tax, with a tooltip breaking it down. */
    protected static void setPayButtons(Button card, Button cash, long total) {
        long tax = salesTax(total);
        card.setMessage(Component.translatable("gui.reikoeconomy.pay_card", format(total + tax)));
        cash.setMessage(Component.translatable("gui.reikoeconomy.pay_cash", format(total + tax)));
        Tooltip breakdown = tax > 0
                ? Tooltip.create(Component.translatable("gui.reikoeconomy.price_with_tax", format(total), format(tax)))
                : null;
        card.setTooltip(breakdown);
        cash.setTooltip(breakdown);
    }

    /** Value of the coins and bills the player carries, loose and in wallets. */
    protected long cashOnHand() {
        return Purse.value(minecraft.player);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (keyCode != InputConstants.KEY_ESCAPE && focused instanceof EditBox box && box.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                onEnter();
                return true;
            }
            box.keyPressed(keyCode, scanCode, modifiers);
            return true; // swallow every other key so the inventory key doesn't close the screen mid-typing
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- drawing helpers, in screen coordinates ----

    /** A vanilla-style raised grey panel. */
    protected static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + width - 2, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, 0xFF555555);
    }

    /** A sunken slot frame for an item drawn at (x, y). */
    protected static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x - 1, y - 1, x + 17, y, 0xFF373737);
        graphics.fill(x - 1, y - 1, x, y + 17, 0xFF373737);
        graphics.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        graphics.fill(x + 16, y, x + 17, y + 17, 0xFFFFFFFF);
    }

    /** A gold-framed slot, used for the "item for sale" display. */
    protected static void drawHighlightSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 3, y - 3, x + 19, y + 19, 0xFFB8860B);
        drawSlot(graphics, x, y);
    }

    protected static void drawSlotGrid(GuiGraphics graphics, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                drawSlot(graphics, x + col * 18, y + row * 18);
            }
        }
    }

    /** The player's 3 inventory rows plus the hotbar, with the top-left item at (x, y). */
    protected static void drawPlayerInventory(GuiGraphics graphics, int x, int y) {
        drawSlotGrid(graphics, x, y, 9, 3);
        drawSlotGrid(graphics, x, y + 58, 9, 1);
    }
}
