package com.reiko.economy.client.screen;

import com.reiko.economy.block.entity.AbstractShopBlockEntity;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.menu.ShopBuyMenu;
import com.reiko.economy.network.ShopTradePayload;
import com.reiko.economy.shop.Payment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A customer's view of a player shop: choose how many bundles, then pay by card (with PIN) or cash. */
public final class ShopBuyScreen extends ShopScreenBase<ShopBuyMenu> {
    private static final int ITEM_X = 10;
    private static final int ITEM_Y = 22;
    private static final int QUANTITY_ROW = 84;
    private static final int PAY_ROW = 108;

    private int quantity = 1;
    private EditBox pinBox;
    private Button cardButton;
    private Button cashButton;

    public ShopBuyScreen(ShopBuyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShopBuyMenu.WIDTH, ShopBuyMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("-"), button -> changeQuantity(-1))
                .bounds(leftPos + 8, topPos + QUANTITY_ROW, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> changeQuantity(1))
                .bounds(leftPos + 58, topPos + QUANTITY_ROW, 20, 20).build());
        pinBox = addRenderableWidget(createPinBox(leftPos + 110, topPos + QUANTITY_ROW + 2));
        cardButton = addRenderableWidget(Button.builder(Component.empty(), button -> buy(Payment.card(pinBox.getValue())))
                .bounds(leftPos + 8, topPos + PAY_ROW, 78, 20).build());
        cashButton = addRenderableWidget(Button.builder(Component.empty(), button -> buy(Payment.cash()))
                .bounds(leftPos + 90, topPos + PAY_ROW, 78, 20).build());
        updateButtons();
    }

    private void changeQuantity(int direction) {
        int step = hasShiftDown() ? 10 : 1;
        quantity = Math.max(1, Math.min(AbstractShopBlockEntity.MAX_BUNDLES_PER_TRADE, quantity + direction * step));
        updateButtons();
    }

    private void buy(Payment payment) {
        PacketDistributor.sendToServer(new ShopTradePayload(menu.containerId, ShopTradePayload.BUY, quantity, menu.price(),
                payment.method(), payment.pin()));
    }

    @Override
    protected void onEnter() {
        if (cardButton.active) {
            buy(Payment.card(pinBox.getValue()));
        }
    }

    private void updateButtons() {
        long total = menu.price() * quantity;
        boolean inStock = menu.availableBundles() >= quantity;
        cardButton.setMessage(Component.translatable("gui.reikoeconomy.pay_card", format(total)));
        cashButton.setMessage(Component.translatable("gui.reikoeconomy.pay_cash", format(total)));
        cardButton.active = inStock && DebitCardItem.cardOf(carriedCard()) != null && pinBox.getValue().length() == 4;
        cashButton.active = inStock && cashOnHand() >= total;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons(); // stock, cash and the typed PIN change while the screen is open
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawHighlightSlot(graphics, leftPos + ITEM_X, topPos + ITEM_Y);
        graphics.fill(leftPos + 30, topPos + QUANTITY_ROW + 1, leftPos + 56, topPos + QUANTITY_ROW + 19, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        ItemStack stack = menu.template();
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.bundle", stack.getCount(), stack.getHoverName()),
                34, 22, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.each_bundle", format(menu.price())), 34, 33, MUTED_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.in_stock", menu.availableBundles()), 8, 48, LABEL_COLOR, false);
        graphics.drawString(font, cardLine(carriedCard()), 8, 59, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.cash_on_hand", format(cashOnHand())), 8, 70, LABEL_COLOR, false);
        String amount = String.valueOf(quantity);
        graphics.drawString(font, amount, 43 - font.width(amount) / 2, QUANTITY_ROW + 6, 0xFFFFFF, true);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.pin"), 88, QUANTITY_ROW + 6, LABEL_COLOR, false);
        drawFeedback(graphics, 8, 133, imageWidth - 16, 3);
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
