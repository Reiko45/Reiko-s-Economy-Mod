package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
import com.reiko.economy.menu.AdminShopSetupMenu;
import com.reiko.economy.network.SetShopPricesPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Admin setup for a server shop: the item, what players pay to buy it, and what the shop pays to buy it back. */
public final class AdminShopSetupScreen extends ShopScreenBase<AdminShopSetupMenu> {
    private EditBox buyBox;
    private EditBox sellBox;

    public AdminShopSetupScreen(AdminShopSetupMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AdminShopSetupMenu.WIDTH, AdminShopSetupMenu.HEIGHT);
        this.inventoryLabelY = AdminShopSetupMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        buyBox = priceBox(leftPos + 96, topPos + 18, menu.initialBuyPrice(), "gui.reikoeconomy.buy_price_label");
        sellBox = priceBox(leftPos + 96, topPos + 38, menu.initialSellPrice(), "gui.reikoeconomy.sell_price_label");
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.save"), button -> onEnter())
                .bounds(leftPos + 128, topPos + 58, 40, 20).build());
    }

    private EditBox priceBox(int x, int y, long initial, String labelKey) {
        EditBox box = new EditBox(font, x, y, 72, 16, Component.translatable(labelKey));
        box.setMaxLength(16);
        box.setFilter(MONEY_FILTER);
        box.setValue(initial > 0 ? Money.toPlainString(initial) : "");
        return addRenderableWidget(box);
    }

    @Override
    protected void onEnter() {
        PacketDistributor.sendToServer(new SetShopPricesPayload(menu.containerId, buyBox.getValue(), sellBox.getValue()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + AdminShopSetupMenu.TEMPLATE_X, topPos + AdminShopSetupMenu.TEMPLATE_Y);
        drawPlayerInventory(graphics, leftPos + 8, topPos + AdminShopSetupMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.buy_price_label"), 32, 22, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.sell_price_label"), 32, 42, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.zero_is_off"), 32, 64, MUTED_COLOR, false);
        drawFeedback(graphics, 8, 84, imageWidth - 16);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index == AdminShopSetupMenu.TEMPLATE_SLOT
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
