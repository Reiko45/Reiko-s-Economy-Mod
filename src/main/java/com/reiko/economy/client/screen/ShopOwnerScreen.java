package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
import com.reiko.economy.menu.ShopOwnerMenu;
import com.reiko.economy.network.SetShopPricesPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owner setup: pick the item (click the gold slot), type a price, and fill the stock slots. */
public final class ShopOwnerScreen extends ShopScreenBase<ShopOwnerMenu> {
    private EditBox priceBox;

    public ShopOwnerScreen(ShopOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShopOwnerMenu.WIDTH, ShopOwnerMenu.HEIGHT);
        this.inventoryLabelY = ShopOwnerMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        priceBox = new EditBox(font, leftPos + 66, topPos + 19, 52, 16, Component.translatable("gui.reikoeconomy.price"));
        priceBox.setMaxLength(16);
        priceBox.setFilter(MONEY_FILTER);
        priceBox.setValue(menu.initialPrice() > 0 ? Money.toPlainString(menu.initialPrice()) : "");
        addRenderableWidget(priceBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.save"), button -> onEnter())
                .bounds(leftPos + 122, topPos + 17, 46, 20).build());
    }

    @Override
    protected void onEnter() {
        PacketDistributor.sendToServer(new SetShopPricesPayload(menu.containerId, priceBox.getValue(), ""));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + ShopOwnerMenu.TEMPLATE_X, topPos + ShopOwnerMenu.TEMPLATE_Y);
        drawSlotGrid(graphics, leftPos + ShopOwnerMenu.STOCK_X, topPos + ShopOwnerMenu.STOCK_Y, 9, 3);
        drawPlayerInventory(graphics, leftPos + 8, topPos + ShopOwnerMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.price_per_bundle"), 32, 23, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.stock"), 8, ShopOwnerMenu.STOCK_Y - 11, LABEL_COLOR, false);
        drawFeedback(graphics, 8, 41, imageWidth - 16);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index == ShopOwnerMenu.TEMPLATE_SLOT
                && !hoveredSlot.hasItem()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.reikoeconomy.template_hint.title"),
                    Component.translatable("gui.reikoeconomy.template_hint.left").withColor(MUTED_COLOR),
                    Component.translatable("gui.reikoeconomy.template_hint.right").withColor(MUTED_COLOR)), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
