package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
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
 * Insert a debit card and type its PIN to withdraw cash, deposit it, or check the balance. Without a card,
 * type the PIN you want and press "New card" to get one for your own account.
 */
public final class AtmScreen extends ShopScreenBase<AtmMenu> {
    private static final long[] QUICK_AMOUNTS = {2_000, 5_000, 10_000}; // $20, $50, $100
    private static final int PIN_ROW = 40;
    private static final int WITHDRAW_ROW = 62;
    private static final int QUICK_ROW = 84;
    private static final int DEPOSIT_ROW = 159;

    private EditBox pinBox;
    private EditBox amountBox;
    private Button depositButton;

    public AtmScreen(AtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AtmMenu.WIDTH, AtmMenu.HEIGHT);
        this.inventoryLabelY = AtmMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        pinBox = addRenderableWidget(createPinBox(leftPos + 28, topPos + PIN_ROW + 2));
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.balance"),
                button -> send(AtmActionPayload.CHECK_BALANCE, "")).bounds(leftPos + 66, topPos + PIN_ROW, 50, 20).build());
        Button newCard = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.new_card"),
                button -> send(AtmActionPayload.NEW_CARD, "")).bounds(leftPos + 118, topPos + PIN_ROW, 50, 20).build());
        newCard.setTooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.atm.new_card_hint")));

        amountBox = new EditBox(font, leftPos + 8, topPos + WITHDRAW_ROW + 2, 80, 16, Component.translatable("gui.reikoeconomy.atm.amount"));
        amountBox.setMaxLength(16);
        amountBox.setFilter(MONEY_FILTER);
        amountBox.setHint(Component.translatable("gui.reikoeconomy.atm.amount").withColor(MUTED_COLOR));
        addRenderableWidget(amountBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.withdraw"), button -> withdraw(amountBox.getValue()))
                .bounds(leftPos + 92, topPos + WITHDRAW_ROW, 76, 20).build());
        for (int i = 0; i < QUICK_AMOUNTS.length; i++) {
            long amount = QUICK_AMOUNTS[i];
            addRenderableWidget(Button.builder(Component.literal(format(amount)), button -> withdraw(Money.toPlainString(amount)))
                    .bounds(leftPos + 8 + i * 54, topPos + QUICK_ROW, 52, 20).build());
        }
        depositButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.deposit"),
                button -> send(AtmActionPayload.DEPOSIT_SLOTS, "")).bounds(leftPos + 8, topPos + DEPOSIT_ROW, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.deposit_all"),
                button -> send(AtmActionPayload.DEPOSIT_ALL, "")).bounds(leftPos + 90, topPos + DEPOSIT_ROW, 78, 20).build());
    }

    @Override
    protected void onEnter() {
        if (getFocused() == amountBox) {
            withdraw(amountBox.getValue());
        } else {
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
        depositButton.active = menu.depositValue() > 0;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + AtmMenu.CARD_X, topPos + AtmMenu.CARD_Y);
        drawSlotGrid(graphics, leftPos + 8, topPos + AtmMenu.DEPOSIT_Y, AtmMenu.DEPOSIT_SLOTS, 1);
        drawPlayerInventory(graphics, leftPos + 8, topPos + AtmMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 32, titleLabelY, LABEL_COLOR, false);
        Component cardText = menu.card().isEmpty()
                ? Component.translatable("gui.reikoeconomy.atm.insert_card")
                : cardLine(menu.card());
        graphics.drawString(font, cardText, 32, 22, menu.card().isEmpty() ? MUTED_COLOR : LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.pin"), 8, PIN_ROW + 6, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
        drawFeedback(graphics, 8, 107, imageWidth - 16);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.atm.to_deposit", format(menu.depositValue())),
                8, AtmMenu.DEPOSIT_Y - 11, LABEL_COLOR, false);
    }
}
