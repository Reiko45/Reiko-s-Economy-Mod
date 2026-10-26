package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
import com.reiko.economy.item.Cards;
import com.reiko.economy.menu.AtmMenu;
import com.reiko.economy.network.AtmActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ATM. With no card inserted, type the PIN you want and get a new debit or credit card. With a debit card
 * inserted, type its PIN to withdraw, deposit or check the balance. With a credit card inserted, the same
 * buttons take a cash advance, pay the bill in cash or show what's owed, and "Pay bill" pays it from the bank.
 */
public final class AtmScreen extends ShopScreenBase<AtmMenu> {
    private static final long[] QUICK_AMOUNTS = {2_000, 5_000, 10_000}; // $20, $50, $100
    private static final int PIN_ROW = 38;
    private static final int WITHDRAW_ROW = 60;
    private static final int QUICK_ROW = 82;
    private static final int FEEDBACK_Y = 106;
    private static final int FEEDBACK_LINES = 5;
    private static final int COLUMN_WIDTH = 160;
    private static final int DEPOSIT_ROW = AtmMenu.DEPOSIT_Y + 21;

    private EditBox pinBox;
    private EditBox amountBox;
    private Button balanceButton;
    private Button payBillButton;
    private Button newDebitButton;
    private Button newCreditButton;
    private Button withdrawButton;
    private Button depositButton;
    private Button depositAllButton;
    private Boolean showingCredit;

    public AtmScreen(AtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AtmMenu.WIDTH, AtmMenu.HEIGHT);
        this.inventoryLabelX = AtmMenu.RIGHT_X;
        this.inventoryLabelY = AtmMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        pinBox = addRenderableWidget(createPinBox(leftPos + 28, topPos + PIN_ROW + 2));

        // With a card inserted
        balanceButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.balance"),
                button -> send(AtmActionPayload.CHECK_BALANCE, "")).bounds(leftPos + 66, topPos + PIN_ROW, 50, 20).build());
        payBillButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.pay_bill"),
                button -> send(AtmActionPayload.PAY_BILL, amountBox.getValue())).bounds(leftPos + 118, topPos + PIN_ROW, 50, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.atm.pay_bill_hint"))).build());
        // With no card inserted
        newDebitButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.debit"),
                button -> send(AtmActionPayload.NEW_CARD, "")).bounds(leftPos + 66, topPos + PIN_ROW, 50, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.atm.new_card_hint"))).build());
        newCreditButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.credit"),
                button -> send(AtmActionPayload.NEW_CREDIT_CARD, "")).bounds(leftPos + 118, topPos + PIN_ROW, 50, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.atm.new_credit_hint"))).build());

        amountBox = new EditBox(font, leftPos + 8, topPos + WITHDRAW_ROW + 2, 80, 16, Component.translatable("gui.reikoeconomy.atm.amount"));
        amountBox.setMaxLength(16);
        amountBox.setFilter(MONEY_FILTER);
        amountBox.setHint(Component.translatable("gui.reikoeconomy.atm.amount").withColor(MUTED_COLOR));
        addRenderableWidget(amountBox);
        withdrawButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.withdraw"),
                button -> withdraw(amountBox.getValue())).bounds(leftPos + 92, topPos + WITHDRAW_ROW, 76, 20).build());
        for (int i = 0; i < QUICK_AMOUNTS.length; i++) {
            long amount = QUICK_AMOUNTS[i];
            addRenderableWidget(Button.builder(Component.literal(format(amount)), button -> withdraw(Money.toPlainString(amount)))
                    .bounds(leftPos + 8 + i * 54, topPos + QUICK_ROW, 52, 20).build());
        }
        depositButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.deposit"),
                button -> send(AtmActionPayload.DEPOSIT_SLOTS, "")).bounds(leftPos + AtmMenu.RIGHT_X, topPos + DEPOSIT_ROW, 80, 20).build());
        depositAllButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.deposit_all"),
                button -> send(AtmActionPayload.DEPOSIT_ALL, "")).bounds(leftPos + AtmMenu.RIGHT_X + 82, topPos + DEPOSIT_ROW, 80, 20).build());
        showingCredit = null;
        updateButtons();
    }

    @Override
    protected void onEnter() {
        if (getFocused() == amountBox) {
            withdraw(amountBox.getValue());
        } else if (!menu.card().isEmpty()) {
            send(AtmActionPayload.CHECK_BALANCE, "");
        }
    }

    private void withdraw(String amount) {
        send(AtmActionPayload.WITHDRAW, amount);
    }

    private void send(int action, String amount) {
        PacketDistributor.sendToServer(new AtmActionPayload(menu.containerId, action, amount, pinBox.getValue()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    /** Card services without a card, account services with one; credit cards relabel the money buttons. */
    private void updateButtons() {
        boolean hasCard = !menu.card().isEmpty();
        boolean credit = Cards.isCredit(menu.card());
        balanceButton.visible = hasCard;
        payBillButton.visible = credit;
        newDebitButton.visible = newCreditButton.visible = !hasCard;
        depositButton.active = menu.depositValue() > 0;
        if (showingCredit == null || showingCredit != credit) {
            showingCredit = credit;
            withdrawButton.setMessage(Component.translatable(credit ? "gui.reikoeconomy.atm.cash_advance" : "gui.reikoeconomy.atm.withdraw"));
            withdrawButton.setTooltip(credit ? Tooltip.create(Component.translatable("gui.reikoeconomy.atm.cash_advance_hint")) : null);
            depositButton.setMessage(Component.translatable(credit ? "gui.reikoeconomy.atm.pay_cash" : "gui.reikoeconomy.atm.deposit"));
            depositAllButton.setMessage(Component.translatable(credit ? "gui.reikoeconomy.atm.pay_all_cash" : "gui.reikoeconomy.atm.deposit_all"));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + AtmMenu.CARD_X, topPos + AtmMenu.CARD_Y);
        drawSlotGrid(graphics, leftPos + AtmMenu.RIGHT_X, topPos + AtmMenu.DEPOSIT_Y, AtmMenu.DEPOSIT_SLOTS, 1);
        drawPlayerInventory(graphics, leftPos + AtmMenu.RIGHT_X, topPos + AtmMenu.INVENTORY_Y);
        // A groove between the two columns
        int dividerX = leftPos + AtmMenu.RIGHT_X - 7;
        graphics.fill(dividerX, topPos + 6, dividerX + 1, topPos + imageHeight - 6, 0xFF8B8B8B);
        graphics.fill(dividerX + 1, topPos + 6, dividerX + 2, topPos + imageHeight - 6, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 32, titleLabelY, LABEL_COLOR, false);
        boolean hasCard = !menu.card().isEmpty();
        Component cardText = hasCard ? fit(cardLine(menu.card()), COLUMN_WIDTH - 24) : Component.translatable("gui.reikoeconomy.atm.insert_card");
        graphics.drawString(font, cardText, 32, 22, hasCard ? LABEL_COLOR : MUTED_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.pin"), 8, PIN_ROW + 6, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
        drawFeedback(graphics, 8, FEEDBACK_Y, COLUMN_WIDTH, FEEDBACK_LINES);
        String toDeposit = Cards.isCredit(menu.card()) ? "gui.reikoeconomy.atm.to_pay" : "gui.reikoeconomy.atm.to_deposit";
        graphics.drawString(font, Component.translatable(toDeposit, format(menu.depositValue())), AtmMenu.RIGHT_X, AtmMenu.DEPOSIT_Y - 11, LABEL_COLOR, false);
    }
}
