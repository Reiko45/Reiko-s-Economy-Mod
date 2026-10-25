package com.reiko.economy.client.screen;

import com.reiko.economy.block.entity.AbstractShopBlockEntity;
import com.reiko.economy.menu.AdminShopTradeMenu;
import com.reiko.economy.network.ShopTradePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Buy from and/or sell to the server's shop. A side the shop doesn't offer is greyed out. */
public final class AdminShopTradeScreen extends ShopScreenBase<AdminShopTradeMenu> {
    private static final int ITEM_X = 10;
    private static final int ITEM_Y = 22;
    private static final int BUY_ROW_Y = 58;
    private static final int SELL_ROW_Y = 100;

    private int quantity = 1;
    private Button buyButton;
    private Button sellOneButton;
    private Button sellAllButton;

    public AdminShopTradeScreen(AdminShopTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AdminShopTradeMenu.WIDTH, AdminShopTradeMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        boolean sells = menu.buyPrice() > 0;
        boolean buys = menu.sellPrice() > 0;

        Button minus = addRenderableWidget(Button.builder(Component.literal("-"), button -> changeQuantity(-1))
                .bounds(leftPos + 8, topPos + BUY_ROW_Y, 20, 20).build());
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"), button -> changeQuantity(1))
                .bounds(leftPos + 58, topPos + BUY_ROW_Y, 20, 20).build());
        buyButton = addRenderableWidget(Button.builder(Component.empty(), button -> trade(ShopTradePayload.BUY, quantity, menu.buyPrice()))
                .bounds(leftPos + 84, topPos + BUY_ROW_Y, 84, 20).build());
        minus.visible = plus.visible = buyButton.visible = sells;

        sellOneButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.sell_one"),
                button -> trade(ShopTradePayload.SELL, 1, menu.sellPrice())).bounds(leftPos + 8, topPos + SELL_ROW_Y, 70, 20).build());
        sellAllButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.sell_all"),
                button -> trade(ShopTradePayload.SELL, Integer.MAX_VALUE, menu.sellPrice())).bounds(leftPos + 84, topPos + SELL_ROW_Y, 84, 20).build());
        sellOneButton.visible = sellAllButton.visible = buys;
        updateButtons();
    }

    private void changeQuantity(int direction) {
        int step = hasShiftDown() ? 10 : 1;
        quantity = Math.max(1, Math.min(AbstractShopBlockEntity.MAX_BUNDLES_PER_TRADE, quantity + direction * step));
        updateButtons();
    }

    private void trade(int action, int bundles, long expectedPrice) {
        PacketDistributor.sendToServer(new ShopTradePayload(menu.containerId, action, bundles, expectedPrice));
    }

    private int heldBundles() {
        ItemStack template = menu.template();
        if (template.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        return count / template.getCount();
    }

    private void updateButtons() {
        buyButton.setMessage(Component.translatable("gui.reikoeconomy.buy_for", format(menu.buyPrice() * quantity)));
        int held = heldBundles();
        sellOneButton.active = sellAllButton.active = held > 0;
        sellAllButton.setMessage(Component.translatable("gui.reikoeconomy.sell_all_for", format(menu.sellPrice() * held)));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons(); // the inventory changes after each sale
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + ITEM_X, topPos + ITEM_Y);
        if (menu.buyPrice() > 0) {
            graphics.fill(leftPos + 30, topPos + BUY_ROW_Y + 1, leftPos + 56, topPos + BUY_ROW_Y + 19, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        ItemStack stack = menu.template();
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.bundle", stack.getCount(), stack.getHoverName()),
                34, 22, LABEL_COLOR, false);

        if (menu.buyPrice() > 0) {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.buy_price", format(menu.buyPrice())), 8, BUY_ROW_Y - 11, LABEL_COLOR, false);
            String amount = String.valueOf(quantity);
            graphics.drawString(font, amount, 43 - font.width(amount) / 2, BUY_ROW_Y + 6, 0xFFFFFF, true);
        } else {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.not_selling"), 8, BUY_ROW_Y - 11, MUTED_COLOR, false);
        }

        if (menu.sellPrice() > 0) {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.sell_price", format(menu.sellPrice())),
                    8, SELL_ROW_Y - 11, LABEL_COLOR, false);
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.you_have", heldBundles()),
                    8, SELL_ROW_Y + 24, MUTED_COLOR, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.not_buying"), 8, SELL_ROW_Y - 11, MUTED_COLOR, false);
        }

        graphics.drawString(font, balanceLine(), 8, 136, LABEL_COLOR, false);
        drawFeedback(graphics, 8, 148, imageWidth - 16);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack stack = menu.template();
        int x = leftPos + ITEM_X;
        int y = topPos + ITEM_Y;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }
}
