package com.reiko.economy.client.screen;

import com.reiko.economy.block.entity.AbstractShopBlockEntity;
import com.reiko.economy.menu.ShopBuyMenu;
import com.reiko.economy.network.ShopTradePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A customer's view of a player shop: choose how many bundles and buy. */
public final class ShopBuyScreen extends ShopScreenBase<ShopBuyMenu> {
    private static final int ITEM_X = 10;
    private static final int ITEM_Y = 22;

    private int quantity = 1;
    private Button buyButton;

    public ShopBuyScreen(ShopBuyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShopBuyMenu.WIDTH, ShopBuyMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("-"), button -> changeQuantity(-1))
                .bounds(leftPos + 8, topPos + 74, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> changeQuantity(1))
                .bounds(leftPos + 58, topPos + 74, 20, 20).build());
        buyButton = addRenderableWidget(Button.builder(Component.empty(), button -> buy())
                .bounds(leftPos + 84, topPos + 74, 84, 20).build());
        updateBuyButton();
    }

    private void changeQuantity(int direction) {
        int step = hasShiftDown() ? 10 : 1;
        quantity = Math.max(1, Math.min(AbstractShopBlockEntity.MAX_BUNDLES_PER_TRADE, quantity + direction * step));
        updateBuyButton();
    }

    private void buy() {
        PacketDistributor.sendToServer(new ShopTradePayload(menu.containerId, ShopTradePayload.BUY, quantity, menu.price()));
    }

    private void updateBuyButton() {
        buyButton.setMessage(Component.translatable("gui.reikoeconomy.buy_for", format(menu.price() * quantity)));
        buyButton.active = menu.availableBundles() >= quantity;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateBuyButton(); // stock can change while the screen is open
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + ITEM_X, topPos + ITEM_Y);
        graphics.fill(leftPos + 30, topPos + 75, leftPos + 56, topPos + 93, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        ItemStack stack = menu.template();
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.bundle", stack.getCount(), stack.getHoverName()),
                34, 22, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.each_bundle", format(menu.price())), 34, 33, MUTED_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.in_stock", menu.availableBundles()), 8, 48, LABEL_COLOR, false);
        graphics.drawString(font, balanceLine(), 8, 59, LABEL_COLOR, false);
        String amount = String.valueOf(quantity);
        graphics.drawString(font, amount, 43 - font.width(amount) / 2, 80, 0xFFFFFF, true);
        drawFeedback(graphics, 8, 99, imageWidth - 16);
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
