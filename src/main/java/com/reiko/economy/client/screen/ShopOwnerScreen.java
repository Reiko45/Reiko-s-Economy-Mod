package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
import com.reiko.economy.menu.ShopOwnerMenu;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.network.SetShopPricesPayload;
import com.reiko.economy.network.ShopAccountPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owner setup, in two columns. Left: pick the item (click the gold slot), then type what players pay and what
 * the shop pays (either can be blank to turn that side off). Right: the stock and the player's inventory.
 */
public final class ShopOwnerScreen extends ShopScreenBase<ShopOwnerMenu> {
    private static final int COLUMN_WIDTH = ShopOwnerMenu.RIGHT_X - 16;
    private static final int PRICE_ROW = 44;
    private static final int SELL_PRICE_ROW = 64;
    private static final int SAVE_ROW = 84;
    private static final int ACCOUNT_ROW = 106;
    private static final int FEEDBACK_Y = 128;

    private EditBox priceBox;
    private EditBox sellPriceBox;
    private Button accountButton;

    public ShopOwnerScreen(ShopOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShopOwnerMenu.WIDTH, ShopOwnerMenu.HEIGHT);
        this.inventoryLabelX = ShopOwnerMenu.INVENTORY_X;
        this.inventoryLabelY = ShopOwnerMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        priceBox = addRenderableWidget(priceBox(PRICE_ROW, "gui.reikoeconomy.buy_price_label", menu.initialPrice()));
        sellPriceBox = addRenderableWidget(priceBox(SELL_PRICE_ROW, "gui.reikoeconomy.sell_price_label", menu.initialSellPrice()));
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.save"), button -> onEnter())
                .bounds(leftPos + 8 + COLUMN_WIDTH - 50, topPos + SAVE_ROW, 50, 20).build());
        accountButton = addRenderableWidget(Button.builder(Component.empty(),
                        button -> PacketDistributor.sendToServer(new ShopAccountPayload(menu.containerId)))
                .bounds(leftPos + 8 + COLUMN_WIDTH - 110, topPos + ACCOUNT_ROW, 110, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.shop_account_hint"))).build());
        updateAccountButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateAccountButton();
    }

    /** "You", or the shared account the shop runs from, as the client's copy of the shop says. */
    private void updateAccountButton() {
        String account = minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof ShopBlockEntity shop
                ? shop.accountName() : "";
        accountButton.setMessage(fit(account.isEmpty() ? Component.translatable("gui.reikoeconomy.shop_account_you")
                : Component.literal(account), 102));
    }

    private EditBox priceBox(int y, String label, long value) {
        EditBox box = new EditBox(font, leftPos + 8 + COLUMN_WIDTH - 66, topPos + y, 66, 16, Component.translatable(label));
        box.setMaxLength(16);
        box.setFilter(MONEY_FILTER);
        box.setValue(value > 0 ? Money.toPlainString(value) : "");
        box.setHint(Component.translatable("shop.reikoeconomy.admin.off").withColor(MUTED_COLOR));
        return box;
    }

    @Override
    protected void onEnter() {
        PacketDistributor.sendToServer(new SetShopPricesPayload(menu.containerId, priceBox.getValue(), sellPriceBox.getValue()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + ShopOwnerMenu.TEMPLATE_X, topPos + ShopOwnerMenu.TEMPLATE_Y);
        drawSlotGrid(graphics, leftPos + ShopOwnerMenu.STOCK_X, topPos + ShopOwnerMenu.STOCK_Y, 9, 3);
        drawPlayerInventory(graphics, leftPos + ShopOwnerMenu.INVENTORY_X, topPos + ShopOwnerMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        ItemStack template = menu.template();
        int textX = ShopOwnerMenu.TEMPLATE_X + 22;
        int textWidth = COLUMN_WIDTH - 22;
        if (template.isEmpty()) {
            graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.template_prompt"), textWidth),
                    textX, 18, MUTED_COLOR, false);
        } else {
            graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.bundle", template.getCount(),
                    template.getHoverName()), textWidth), textX, 18, LABEL_COLOR, false);
            graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.in_stock", stockBundles(template)), textWidth),
                    textX, 28, MUTED_COLOR, false);
        }
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.buy_price_label"), 8, PRICE_ROW + 4, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.sell_price_label"), 8, SELL_PRICE_ROW + 4, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.zero_is_off"), 8, SAVE_ROW + 6, MUTED_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.shop_account"), 8, ACCOUNT_ROW + 4, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.stock"), ShopOwnerMenu.STOCK_X, 6, LABEL_COLOR, false);
        drawFeedback(graphics, 8, FEEDBACK_Y, COLUMN_WIDTH, 4);
    }

    /** Whole bundles in the stock slots, which the client sees like any other slots. */
    private int stockBundles(ItemStack template) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.index > ShopOwnerMenu.TEMPLATE_SLOT && slot.index <= 27
                    && ItemStack.isSameItemSameComponents(slot.getItem(), template)) {
                count += slot.getItem().getCount();
            }
        }
        return count / template.getCount();
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
