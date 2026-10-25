package com.reiko.economy.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.DebitCard;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.network.ClientBalance;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

    /** "Card •••• 4821 (Steve)" for the card the player would pay with, or a note that they have none. */
    protected Component cardLine(ItemStack card) {
        DebitCard data = DebitCardItem.cardOf(card);
        return data == null
                ? Component.translatable("gui.reikoeconomy.no_card")
                : Component.translatable("gui.reikoeconomy.card_line", data.lastFour(), data.ownerName());
    }

    protected ItemStack carriedCard() {
        return DebitCardItem.findCard(minecraft.player);
    }

    /** Value of the coins and bills in the player's inventory. */
    protected long cashOnHand() {
        long total = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            total += CashItem.valueOf(stack);
        }
        return total;
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
