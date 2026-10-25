package com.reiko.economy.client.screen;

import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.network.VillagerTradePayload;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.villager.VillagerPricing;
import com.reiko.economy.villager.VillagerTradeService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces vanilla's villager trading screen: the same trades, priced in money and paid by card or cash.
 *
 * <p>It sits on top of vanilla's trading menu, so the server side of trading is untouched. The menu's three
 * emerald payment slots are hidden and ignored; trades go through {@link VillagerTradePayload} instead. The
 * player's inventory stays where vanilla puts it, and the panel grows upward to make room for the payment
 * controls, so coordinates above the vanilla layout are negative.
 */
public final class VillagerTradeScreen extends ShopScreenBase<MerchantMenu> {
    private static final int WIDTH = 276;
    /** Height of vanilla's layout; the inventory slots sit in its lower right. */
    private static final int HEIGHT = 166;
    /** How far the panel reaches above vanilla's layout. */
    private static final int EXTRA = 66;
    private static final int TOP = -EXTRA;

    private static final int LIST_X = 5;
    private static final int LIST_Y = TOP + 18;
    private static final int ROW_WIDTH = 88;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 10;
    private static final int SCROLL_X = 95;
    private static final int SCROLL_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int THUMB_HEIGHT = 27;

    private static final int RIGHT_X = 108;
    private static final int RIGHT_WIDTH = 160;
    private static final int XP_Y = TOP + 16;
    private static final int DETAIL_Y = TOP + 24;
    private static final int CARD_Y = -18;
    private static final int CASH_Y = -8;
    private static final int QUANTITY_ROW = 6;
    private static final int PAY_ROW = 30;
    private static final int FEEDBACK_Y = 53;

    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow");
    private static final ResourceLocation ARROW_OUT_OF_STOCK = ResourceLocation.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");
    private static final ResourceLocation XP_BACKGROUND = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_background");
    private static final ResourceLocation XP_CURRENT = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_current");
    private static final ResourceLocation XP_RESULT = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_result");

    private static final int PRICE_COLOR = 0xFFFFFF;
    private static final int DISCOUNT_COLOR = 0x80FF20;
    private static final int MARKUP_COLOR = 0xFF8060;
    private static final int SOLD_OUT_COLOR = 0xA0A0A0;
    private static final int WARNING_COLOR = 0xB3261E;

    private int selected;
    private int scroll;
    private int quantity = 1;
    private EditBox pinBox;
    private Button minus;
    private Button plus;
    private Button cardButton;
    private Button cashButton;
    private Button otherButton;
    /** Things under the mouse that have a tooltip, collected while drawing (in panel coordinates). */
    private final List<Hotspot> hotspots = new ArrayList<>();

    private record Hotspot(int x, int y, int width, int height, ItemStack stack, @Nullable Component text) {
    }

    public VillagerTradeScreen(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        // Centre the whole panel, including the part above vanilla's layout.
        topPos = (height - (HEIGHT + EXTRA)) / 2 + EXTRA;

        minus = addRenderableWidget(Button.builder(Component.literal("-"), button -> changeQuantity(-1))
                .bounds(leftPos + RIGHT_X, topPos + QUANTITY_ROW, 20, 20).build());
        plus = addRenderableWidget(Button.builder(Component.literal("+"), button -> changeQuantity(1))
                .bounds(leftPos + RIGHT_X + 50, topPos + QUANTITY_ROW, 20, 20).build());
        pinBox = addRenderableWidget(createPinBox(leftPos + RIGHT_X + 98, topPos + QUANTITY_ROW + 2));
        cardButton = addRenderableWidget(Button.builder(Component.empty(), button -> trade(Payment.card(pinBox.getValue())))
                .bounds(leftPos + RIGHT_X, topPos + PAY_ROW, 78, 20).build());
        cashButton = addRenderableWidget(Button.builder(Component.empty(), button -> trade(Payment.cash()))
                .bounds(leftPos + RIGHT_X + 82, topPos + PAY_ROW, 78, 20).build());
        otherButton = addRenderableWidget(Button.builder(Component.empty(), button -> trade(Payment.cash()))
                .bounds(leftPos + RIGHT_X, topPos + PAY_ROW, RIGHT_WIDTH, 20).build());
        updateButtons();
    }

    // ---- state ----

    @Nullable
    private MerchantOffer selectedOffer() {
        MerchantOffers offers = menu.getOffers();
        return selected >= 0 && selected < offers.size() ? offers.get(selected) : null;
    }

    private static VillagerPricing.Terms terms(MerchantOffer offer) {
        return VillagerPricing.terms(offer, EconomyConfig.emeraldValue());
    }

    private static int stockLeft(MerchantOffer offer) {
        return Math.max(0, offer.getMaxUses() - offer.getUses());
    }

    private int maxQuantity(MerchantOffer offer) {
        return Mth.clamp(stockLeft(offer), 1, VillagerTradeService.MAX_TRADES_AT_ONCE);
    }

    /** Whether the player carries the items the villager wants for {@code times} trades. */
    private boolean hasNeeds(VillagerPricing.Terms terms, int times) {
        List<ItemStack> items = minecraft.player.getInventory().items;
        for (VillagerPricing.Need need : terms.needs()) {
            if (VillagerTradeService.countMatching(items, need.cost()::test) < need.count() * times) {
                return false;
            }
        }
        return true;
    }

    private void changeQuantity(int direction) {
        MerchantOffer offer = selectedOffer();
        if (offer == null) {
            return;
        }
        int max = maxQuantity(offer);
        quantity = hasShiftDown() ? (direction > 0 ? max : 1) : Mth.clamp(quantity + direction, 1, max);
        updateButtons();
    }

    private void select(int index) {
        if (index != selected) {
            selected = index;
            quantity = 1;
            updateButtons();
        }
    }

    private void trade(Payment payment) {
        MerchantOffer offer = selectedOffer();
        if (offer != null) {
            PacketDistributor.sendToServer(new VillagerTradePayload(menu.containerId, selected, quantity, terms(offer).net(),
                    payment.method(), payment.pin()));
        }
    }

    @Override
    protected void onEnter() {
        if (cardButton.visible && cardButton.active) {
            cardButton.onPress();
        }
    }

    private void updateButtons() {
        MerchantOffer offer = selectedOffer();
        if (offer == null) {
            minus.visible = plus.visible = pinBox.visible = cardButton.visible = cashButton.visible = otherButton.visible = false;
            return;
        }
        VillagerPricing.Terms terms = terms(offer);
        quantity = Mth.clamp(quantity, 1, maxQuantity(offer));
        boolean ready = stockLeft(offer) >= quantity && hasNeeds(terms, quantity);
        boolean purchase = terms.isPurchase();

        minus.visible = plus.visible = true;
        pinBox.visible = cardButton.visible = cashButton.visible = purchase;
        otherButton.visible = !purchase;
        if (purchase) {
            long total = terms.price() * quantity;
            cardButton.setMessage(Component.translatable("gui.reikoeconomy.pay_card", format(total)));
            cashButton.setMessage(Component.translatable("gui.reikoeconomy.pay_cash", format(total)));
            cardButton.active = ready && DebitCardItem.cardOf(carriedCard()) != null && pinBox.getValue().length() == 4;
            cashButton.active = ready && cashOnHand() >= total;
        } else {
            otherButton.setMessage(terms.isSale()
                    ? Component.translatable("villager.reikoeconomy.sell_for", format(terms.payout() * quantity))
                    : Component.translatable("villager.reikoeconomy.trade"));
            otherButton.active = ready;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons(); // offers, the inventory and the typed PIN change while the screen is open
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (button == 0 && x >= LIST_X && x < LIST_X + ROW_WIDTH && y >= LIST_Y && y < LIST_Y + SCROLL_HEIGHT) {
            int index = scroll + (int) ((y - LIST_Y) / ROW_HEIGHT);
            if (index < menu.getOffers().size()) {
                select(index);
                return true;
            }
        }
        if (button == 0 && x >= SCROLL_X && x < SCROLL_X + 6 && y >= LIST_Y && y < LIST_Y + SCROLL_HEIGHT && maxScroll() > 0) {
            scroll = Mth.clamp((int) Math.round((y - LIST_Y) / SCROLL_HEIGHT * maxScroll()), 0, maxScroll());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScroll() {
        return Math.max(0, menu.getOffers().size() - VISIBLE_ROWS);
    }

    /** Clicks above vanilla's layout are still inside the panel, so a carried item isn't thrown out. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return mouseX < guiLeft || mouseY < guiTop + TOP || mouseX >= guiLeft + imageWidth || mouseY >= guiTop + imageHeight;
    }

    // The emerald payment slots of vanilla's menu are hidden and can't be used.

    private static boolean isPaymentSlot(@Nullable Slot slot) {
        return slot != null && slot.container instanceof MerchantContainer;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (!isPaymentSlot(slot)) {
            super.slotClicked(slot, slotId, mouseButton, type);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!isPaymentSlot(slot)) {
            super.renderSlot(graphics, slot);
        }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!isPaymentSlot(slot)) {
            super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
        }
    }

    // ---- drawing ----

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos + TOP, imageWidth, HEIGHT + EXTRA);
        drawPlayerInventory(graphics, leftPos + RIGHT_X, topPos + 84);
        if (minus.visible) {
            graphics.fill(leftPos + RIGHT_X + 22, topPos + QUANTITY_ROW + 1, leftPos + RIGHT_X + 48, topPos + QUANTITY_ROW + 19, 0xFF8B8B8B);
        }
        // scroll track
        graphics.fill(leftPos + SCROLL_X - 1, topPos + LIST_Y - 1, leftPos + SCROLL_X + 7, topPos + LIST_Y + SCROLL_HEIGHT + 1, 0xFF373737);
        graphics.fill(leftPos + SCROLL_X, topPos + LIST_Y, leftPos + SCROLL_X + 6, topPos + LIST_Y + SCROLL_HEIGHT, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        hotspots.clear();
        MerchantOffers offers = menu.getOffers();

        Component trades = Component.translatable("merchant.trades");
        graphics.drawString(font, trades, LIST_X + ROW_WIDTH / 2 - font.width(trades) / 2, TOP + 6, LABEL_COLOR, false);
        Component heading = heading();
        graphics.drawString(font, heading, RIGHT_X + RIGHT_WIDTH / 2 - font.width(heading) / 2, TOP + 6, LABEL_COLOR, false);

        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        for (int row = 0; row < VISIBLE_ROWS && scroll + row < offers.size(); row++) {
            int index = scroll + row;
            drawRow(graphics, offers.get(index), index, LIST_X, LIST_Y + row * ROW_HEIGHT, mx, my);
        }
        drawScroller(graphics, offers.size());

        MerchantOffer offer = selectedOffer();
        drawExperience(graphics, offer);
        if (offer == null) {
            graphics.drawString(font, Component.translatable("villager.reikoeconomy.no_trades"), RIGHT_X, DETAIL_Y + 3, MUTED_COLOR, false);
        } else {
            drawDetails(graphics, offer);
        }
        graphics.drawString(font, fit(cardLine(carriedCard()), RIGHT_WIDTH), RIGHT_X, CARD_Y, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.cash_on_hand", format(cashOnHand())), RIGHT_X, CASH_Y, LABEL_COLOR, false);
        if (minus.visible) {
            String amount = String.valueOf(quantity);
            graphics.drawString(font, amount, RIGHT_X + 35 - font.width(amount) / 2, QUANTITY_ROW + 6, 0xFFFFFF, true);
        }
        if (pinBox.visible) {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.pin"), RIGHT_X + 76, QUANTITY_ROW + 6, LABEL_COLOR, false);
        }
        drawFeedback(graphics, RIGHT_X, FEEDBACK_Y, RIGHT_WIDTH, 3);
    }

    /** "Farmer - Apprentice", like vanilla, or just the name for a wandering trader. */
    private Component heading() {
        int level = menu.getTraderLevel();
        return level > 0 && level <= 5 && menu.showProgressBar()
                ? Component.translatable("merchant.title", title, Component.translatable("merchant.level." + level))
                : title;
    }

    private void drawRow(GuiGraphics graphics, MerchantOffer offer, int index, int x, int y, int mx, int my) {
        boolean soldOut = offer.isOutOfStock();
        boolean hovered = mx >= x && mx < x + ROW_WIDTH && my >= y && my < y + ROW_HEIGHT;
        ResourceLocation sprite = soldOut ? BUTTON_DISABLED : index == selected || hovered ? BUTTON_HIGHLIGHTED : BUTTON;
        graphics.blitSprite(sprite, x, y, ROW_WIDTH, ROW_HEIGHT);

        VillagerPricing.Terms terms = terms(offer);
        int cursor = x + 4;
        if (terms.isPurchase()) {
            String price = shortMoney(terms.price());
            long base = VillagerPricing.basePrice(offer, EconomyConfig.emeraldValue());
            int color = soldOut ? SOLD_OUT_COLOR : terms.price() < base ? DISCOUNT_COLOR : terms.price() > base ? MARKUP_COLOR : PRICE_COLOR;
            graphics.drawString(font, price, cursor, y + 6, color, true);
            if (terms.price() != base) {
                Component note = Component.translatable(terms.price() < base ? "villager.reikoeconomy.discount" : "villager.reikoeconomy.markup",
                        format(base));
                hotspots.add(new Hotspot(cursor, y + 4, font.width(price), 12, ItemStack.EMPTY, note));
            }
            cursor += font.width(price) + 3;
        }
        for (VillagerPricing.Need need : terms.needs()) {
            drawItem(graphics, need.display(), cursor, y + 2);
            cursor += 18;
        }
        graphics.blitSprite(soldOut ? ARROW_OUT_OF_STOCK : ARROW, x + 54, y + 6, 10, 9);
        if (terms.isSale()) {
            graphics.drawString(font, shortMoney(terms.payout()), x + 66, y + 6, soldOut ? SOLD_OUT_COLOR : PRICE_COLOR, true);
        } else if (!terms.goods().isEmpty()) {
            drawItem(graphics, terms.goods(), x + 68, y + 2);
        }
    }

    private void drawItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        hotspots.add(new Hotspot(x, y, 16, 16, stack, null));
    }

    private void drawScroller(GuiGraphics graphics, int offerCount) {
        int max = maxScroll();
        if (max > 0) {
            int y = LIST_Y + (SCROLL_HEIGHT - THUMB_HEIGHT) * scroll / max;
            graphics.blitSprite(SCROLLER, SCROLL_X, y, 6, THUMB_HEIGHT);
        } else {
            graphics.blitSprite(SCROLLER_DISABLED, SCROLL_X, LIST_Y, 6, THUMB_HEIGHT);
        }
    }

    /** The villager's progress to its next level, with what the chosen trade would add, as in vanilla. */
    private void drawExperience(GuiGraphics graphics, @Nullable MerchantOffer offer) {
        int level = menu.getTraderLevel();
        if (!menu.showProgressBar() || level < 1 || level >= 5) {
            return;
        }
        int barX = RIGHT_X + RIGHT_WIDTH / 2 - 51;
        graphics.blitSprite(XP_BACKGROUND, barX, XP_Y, 102, 5);
        int xp = menu.getTraderXp();
        int min = VillagerData.getMinXpPerLevel(level);
        if (xp < min || !VillagerData.canLevelUp(level)) {
            return;
        }
        float perXp = 102.0F / (VillagerData.getMaxXpPerLevel(level) - min);
        int current = Math.min(Mth.floor(perXp * (xp - min)), 102);
        graphics.blitSprite(XP_CURRENT, 102, 5, 0, 0, barX, XP_Y, 0, current, 5);
        if (offer != null && !offer.isOutOfStock()) {
            int future = Math.min(Mth.floor(offer.getXp() * quantity * perXp), 102 - current);
            if (future > 0) {
                graphics.blitSprite(XP_RESULT, 102, 5, current, 0, barX + current, XP_Y, 0, future, 5);
            }
        }
    }

    private void drawDetails(GuiGraphics graphics, MerchantOffer offer) {
        VillagerPricing.Terms terms = terms(offer);
        ItemStack icon;
        Component what;
        if (terms.isSale() && !terms.needs().isEmpty()) {
            VillagerPricing.Need sold = terms.needs().get(0);
            icon = sold.display();
            what = Component.translatable("villager.reikoeconomy.sell_line", sold.count(), sold.cost().itemStack().getHoverName());
        } else {
            icon = offer.getResult();
            what = Component.translatable(terms.isPurchase() ? "villager.reikoeconomy.buy_line" : "villager.reikoeconomy.get_line",
                    icon.getCount(), icon.getHoverName());
        }
        drawItem(graphics, icon, RIGHT_X, DETAIL_Y);
        int textX = RIGHT_X + 20;
        int textWidth = RIGHT_WIDTH - 20;
        graphics.drawString(font, fit(what, textWidth), textX, DETAIL_Y + 1, LABEL_COLOR, false);

        Component note;
        int color = MUTED_COLOR;
        int left = stockLeft(offer);
        List<VillagerPricing.Need> extra = terms.isPurchase() ? terms.needs() : terms.needs().subList(Math.min(1, terms.needs().size()), terms.needs().size());
        if (left == 0) {
            note = Component.translatable("villager.reikoeconomy.sold_out");
            color = WARNING_COLOR;
        } else if (!extra.isEmpty()) {
            VillagerPricing.Need need = extra.get(0);
            note = Component.translatable("villager.reikoeconomy.also_needs", need.count(), need.cost().itemStack().getHoverName());
            if (!hasNeeds(terms, quantity)) {
                color = WARNING_COLOR;
            }
        } else {
            note = Component.translatable("villager.reikoeconomy.left", left);
        }
        graphics.drawString(font, fit(note, textWidth), textX, DETAIL_Y + 11, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return; // vanilla's slot tooltip is already showing
        }
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        for (Hotspot spot : hotspots) {
            if (mx >= spot.x() && mx < spot.x() + spot.width() && my >= spot.y() && my < spot.y() + spot.height()) {
                if (spot.text() != null) {
                    graphics.renderTooltip(font, spot.text(), mouseX, mouseY);
                } else {
                    graphics.renderTooltip(font, spot.stack(), mouseX, mouseY);
                }
                return;
            }
        }
    }

    // ---- text helpers ----

    /** "$20" for whole amounts and "$7.50" otherwise, to fit the trade list. */
    private static String shortMoney(long amount) {
        String text = format(amount);
        return amount % 100 == 0 ? text.replace(".00", "") : text;
    }

    /** Cuts {@code text} to {@code width} pixels, ending in "..." when it doesn't fit. */
    private Component fit(Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        FormattedText cut = font.substrByWidth(text, width - font.width("..."));
        return Component.literal(cut.getString() + "...").withStyle(text.getStyle());
    }
}
