package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
import com.reiko.economy.menu.AtmMenu;
import com.reiko.economy.network.AtmActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Withdraw cash (type an amount or use a quick button) or deposit it (put coins and bills in the slots, or
 * deposit everything in your inventory at once).
 */
public final class AtmScreen extends ShopScreenBase<AtmMenu> {
    private static final long[] QUICK_AMOUNTS = {2_000, 5_000, 10_000}; // $20, $50, $100
    private EditBox amountBox;
    private Button depositButton;

    public AtmScreen(AtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AtmMenu.WIDTH, AtmMenu.HEIGHT);
        this.inventoryLabelY = AtmMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        amountBox = new EditBox(font, leftPos + 8, topPos + 32, 80, 16, Component.translatable("gui.reikoeconomy.atm.amount"));
        amountBox.setMaxLength(16);
        amountBox.setFilter(MONEY_FILTER);
        amountBox.setHint(Component.translatable("gui.reikoeconomy.atm.amount").withColor(MUTED_COLOR));
        addRenderableWidget(amountBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.withdraw"), button -> onEnter())
                .bounds(leftPos + 92, topPos + 30, 76, 20).build());
        for (int i = 0; i < QUICK_AMOUNTS.length; i++) {
            long amount = QUICK_AMOUNTS[i];
            addRenderableWidget(Button.builder(Component.literal(format(amount)), button -> withdraw(Money.toPlainString(amount)))
                    .bounds(leftPos + 8 + i * 54, topPos + 54, 52, 20).build());
        }
        depositButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.deposit"),
                button -> send(AtmActionPayload.DEPOSIT_SLOTS, "")).bounds(leftPos + 8, topPos + 132, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.atm.deposit_all"),
                button -> send(AtmActionPayload.DEPOSIT_ALL, "")).bounds(leftPos + 90, topPos + 132, 78, 20).build());
    }

    @Override
    protected void onEnter() {
        withdraw(amountBox.getValue());
    }

    private void withdraw(String amount) {
        send(AtmActionPayload.WITHDRAW, amount);
    }

    private void send(int action, String amount) {
        PacketDistributor.sendToServer(new AtmActionPayload(menu.containerId, action, amount));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        depositButton.active = menu.depositValue() > 0;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawSlotGrid(graphics, leftPos + 8, topPos + AtmMenu.DEPOSIT_Y, AtmMenu.DEPOSIT_SLOTS, 1);
        drawPlayerInventory(graphics, leftPos + 8, topPos + AtmMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, balanceLine(), 8, 19, LABEL_COLOR, false);
        drawFeedback(graphics, 8, 79, imageWidth - 16);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.atm.to_deposit", format(menu.depositValue())),
                8, AtmMenu.DEPOSIT_Y - 11, LABEL_COLOR, false);
    }
}
