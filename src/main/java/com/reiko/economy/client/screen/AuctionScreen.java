package com.reiko.economy.client.screen;

import com.reiko.economy.auction.AuctionPage;
import com.reiko.economy.auction.AuctionQuery;
import com.reiko.economy.auction.AuctionRow;
import com.reiko.economy.auction.AuctionRules;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.Money;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.menu.AuctionMenu;
import com.reiko.economy.network.AuctionActionPayload;
import com.reiko.economy.network.AuctionQueryPayload;
import com.reiko.economy.network.ClientBalance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The auction house. Four tabs share one list: Browse (everything for sale, with search, sort and pages),
 * My listings, Sell (put an item in the slot, set a price, pick buy-now or auction) and Collect (items waiting
 * for you). The panel beside the inventory shows the selected listing with its Buy, Bid or Cancel button.
 */
public final class AuctionScreen extends ShopScreenBase<AuctionMenu> {
    private static final int ROW_X = 8;
    private static final int ROW_Y = 38;
    private static final int ROW_WIDTH = 304;
    private static final int ROW_HEIGHT = 18;
    private static final int PANEL_X = 176;
    private static final int PANEL_WIDTH = 136;
    private static final int PANEL_Y = 154;
    private static final int CONTROL_Y = 194;
    private static final int FEEDBACK_Y = 218;
    private static final int REFRESH_TICKS = 100;
    private static final String[] SORT_KEYS = {"gui.reikoeconomy.auction.sort.newest", "gui.reikoeconomy.auction.sort.cheapest",
            "gui.reikoeconomy.auction.sort.priciest", "gui.reikoeconomy.auction.sort.ending"};

    private int tab = AuctionQuery.BROWSE;
    private int sort = AuctionQuery.NEWEST;
    private int page = 1;
    @Nullable
    private UUID selected;
    private int ticks;

    private Button[] tabs;
    private EditBox searchBox;
    private Button sortButton;
    private Button previousButton;
    private Button nextButton;
    private Button buyButton;
    private EditBox bidBox;
    private Button bidButton;
    private Button cancelButton;
    private EditBox priceBox;
    private Button typeButton;
    private Button hoursButton;
    private Button listButton;
    private Button collectButton;
    private boolean auctionType;
    private int hoursIndex = 2; // 24 hours

    public AuctionScreen(AuctionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AuctionMenu.WIDTH, AuctionMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        tabs = addTabs(84, 4, new String[] {"gui.reikoeconomy.auction.tab.browse", "gui.reikoeconomy.auction.tab.mine",
                "gui.reikoeconomy.auction.tab.sell", "gui.reikoeconomy.auction.tab.collect"}, new int[] {40, 58, 28, 62}, this::selectTab);

        searchBox = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 21, 140, 14, Component.translatable("gui.reikoeconomy.auction.search")));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.translatable("gui.reikoeconomy.auction.search").withColor(MUTED_COLOR));
        searchBox.setResponder(text -> {
            page = 1;
            query();
        });
        sortButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            sort = (sort + 1) % AuctionQuery.SORTS;
            page = 1;
            query();
        }).bounds(leftPos + 152, topPos + 20, 84, 16).build());
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page = Math.max(1, page - 1);
            query();
        }).bounds(leftPos + 240, topPos + 20, 16, 16).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page = Math.min(menu.page().pages(), page + 1);
            query();
        }).bounds(leftPos + 296, topPos + 20, 16, 16).build());

        // The panel: a selected listing
        buyButton = addRenderableWidget(Button.builder(Component.empty(), button -> selectedRow().ifPresent(row ->
                send(AuctionActionPayload.BUY, row.id(), row.price(), 0))).bounds(leftPos + PANEL_X, topPos + CONTROL_Y, PANEL_WIDTH, 20).build());
        bidBox = addRenderableWidget(new EditBox(font, leftPos + PANEL_X, topPos + CONTROL_Y + 2, 66, 16, Component.translatable("gui.reikoeconomy.auction.bid")));
        bidBox.setMaxLength(16);
        bidBox.setFilter(MONEY_FILTER);
        bidButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.auction.bid"), button -> bid())
                .bounds(leftPos + PANEL_X + 70, topPos + CONTROL_Y, PANEL_WIDTH - 70, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.auction.cancel"), button ->
                selectedRow().ifPresent(row -> send(AuctionActionPayload.CANCEL, row.id(), 0, 0)))
                .bounds(leftPos + PANEL_X, topPos + CONTROL_Y, PANEL_WIDTH, 20).build());

        // The panel: selling
        priceBox = addRenderableWidget(new EditBox(font, leftPos + PANEL_X, topPos + 180, 64, 16, Component.translatable("gui.reikoeconomy.price")));
        priceBox.setMaxLength(16);
        priceBox.setFilter(MONEY_FILTER);
        priceBox.setHint(Component.translatable("gui.reikoeconomy.price").withColor(MUTED_COLOR));
        typeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            auctionType = !auctionType;
            updateWidgets();
        }).bounds(leftPos + PANEL_X + 68, topPos + 178, PANEL_WIDTH - 68, 20).build());
        hoursButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            hoursIndex = (hoursIndex + 1) % AuctionRules.AUCTION_HOURS.length;
            updateWidgets();
        }).bounds(leftPos + PANEL_X, topPos + 200, 64, 20).build());
        listButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.auction.list"), button -> list())
                .bounds(leftPos + PANEL_X + 68, topPos + 200, PANEL_WIDTH - 68, 20).build());

        // The panel: collecting
        collectButton = addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.auction.collect_all"),
                button -> send(AuctionActionPayload.COLLECT, null, 0, 0)).bounds(leftPos + PANEL_X, topPos + CONTROL_Y, PANEL_WIDTH, 20).build());

        updateWidgets();
        query();
    }

    // ---- talking to the server ----

    private void selectTab(int newTab) {
        tab = newTab;
        page = 1;
        selected = null;
        updateWidgets();
        query();
    }

    private void query() {
        PacketDistributor.sendToServer(new AuctionQueryPayload(menu.containerId,
                new AuctionQuery(tab, searchBox == null ? "" : searchBox.getValue(), sort, page)));
        ticks = 0;
    }

    private void send(int action, @Nullable UUID listing, long amount, int hours) {
        PacketDistributor.sendToServer(new AuctionActionPayload(menu.containerId, action, Optional.ofNullable(listing), amount, hours));
    }

    private void bid() {
        OptionalLong amount = Money.parsePositive(bidBox.getValue());
        Optional<AuctionRow> row = selectedRow();
        if (amount.isPresent() && row.isPresent()) {
            send(AuctionActionPayload.BID, row.get().id(), amount.getAsLong(), 0);
        }
    }

    private void list() {
        OptionalLong price = Money.parsePositive(priceBox.getValue());
        if (price.isPresent()) {
            send(AuctionActionPayload.LIST, null, price.getAsLong(), auctionType ? AuctionRules.AUCTION_HOURS[hoursIndex] : 0);
        } else {
            showFeedback(Component.translatable("auction.reikoeconomy.bad_price"), false);
        }
    }

    @Override
    protected void onEnter() {
        if (getFocused() == bidBox) {
            bid();
        } else if (getFocused() == priceBox) {
            list();
        }
    }

    // ---- state ----

    private List<AuctionRow> rows() {
        AuctionPage shown = menu.page();
        return shown.tab() == tab ? shown.rows() : List.of();
    }

    private Optional<AuctionRow> selectedRow() {
        if (selected == null) {
            return Optional.empty();
        }
        return rows().stream().filter(row -> selected.equals(row.id())).findFirst();
    }

    /** Time left on a row, counting down since the page arrived. */
    private long millisLeft(AuctionRow row) {
        return Math.max(0, row.millisLeft() - menu.pageAge());
    }

    private boolean listingTab() {
        return tab == AuctionQuery.BROWSE || tab == AuctionQuery.MINE;
    }

    private void updateWidgets() {
        menu.setSellSlotShown(tab == AuctionQuery.SELL);
        AuctionPage shown = menu.page();
        page = Math.min(page, Math.max(1, shown.pages()));

        searchBox.visible = listingTab();
        sortButton.visible = listingTab();
        sortButton.setMessage(Component.translatable("gui.reikoeconomy.auction.sort", Component.translatable(SORT_KEYS[sort])));
        previousButton.visible = nextButton.visible = tab != AuctionQuery.SELL;
        previousButton.active = page > 1;
        nextButton.active = page < shown.pages();
        tabs[AuctionQuery.COLLECT].setMessage(shown.waiting() > 0
                ? Component.translatable("gui.reikoeconomy.auction.tab.collect_count", shown.waiting())
                : Component.translatable("gui.reikoeconomy.auction.tab.collect"));

        Optional<AuctionRow> row = listingTab() ? selectedRow() : Optional.empty();
        boolean own = row.map(AuctionRow::own).orElse(false);
        boolean buyable = row.isPresent() && !own && !row.get().auction();
        boolean biddable = row.isPresent() && !own && row.get().auction() && !row.get().leading();
        buyButton.visible = buyable;
        if (buyable) {
            buyButton.setMessage(Component.translatable("gui.reikoeconomy.auction.buy_for", format(row.get().price())));
            buyButton.active = !ClientBalance.isKnown() || ClientBalance.get() >= row.get().price();
        }
        bidBox.visible = bidButton.visible = biddable;
        bidButton.active = Money.parsePositive(bidBox.getValue()).isPresent();
        cancelButton.visible = row.isPresent() && own;
        cancelButton.active = row.isPresent() && !(row.get().auction() && !row.get().bidder().isEmpty());

        boolean selling = tab == AuctionQuery.SELL;
        priceBox.visible = typeButton.visible = listButton.visible = selling;
        hoursButton.visible = selling && auctionType;
        typeButton.setMessage(Component.translatable(auctionType ? "gui.reikoeconomy.auction.type.auction" : "gui.reikoeconomy.auction.type.buy_now"));
        hoursButton.setMessage(Component.translatable("gui.reikoeconomy.auction.hours", AuctionRules.AUCTION_HOURS[hoursIndex]));
        listButton.active = !menu.sellItem().isEmpty() && Money.parsePositive(priceBox.getValue()).isPresent();

        collectButton.visible = tab == AuctionQuery.COLLECT;
        collectButton.active = shown.waiting() > 0;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++ticks >= REFRESH_TICKS) {
            query(); // new listings, bids and timers
        }
        if (selected != null && listingTab() && menu.page().tab() == tab && selectedRow().isEmpty()) {
            selected = null; // sold, ended or on another page
        }
        updateWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0 && listingTab() && row < rows().size()) {
            AuctionRow clicked = rows().get(row);
            if (!clicked.id().equals(selected)) {
                selected = clicked.id();
                bidBox.setValue(Money.toPlainString(clicked.minimumBid()));
            }
            updateWidgets();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - ROW_X;
        double y = mouseY - topPos - ROW_Y;
        if (x < 0 || x >= ROW_WIDTH || y < 0 || y >= AuctionQuery.PAGE_SIZE * ROW_HEIGHT) {
            return -1;
        }
        return (int) (y / ROW_HEIGHT);
    }

    // ---- drawing ----

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        int hovered = rowAt(mouseX, mouseY);
        List<AuctionRow> rows = rows();
        if (tab != AuctionQuery.SELL) {
            for (int i = 0; i < AuctionQuery.PAGE_SIZE; i++) {
                int x = leftPos + ROW_X;
                int y = topPos + ROW_Y + i * ROW_HEIGHT;
                boolean filled = i < rows.size();
                int color = !filled ? 0xFFBDBDBD : i == hovered && listingTab() ? 0xFFDADADA : i % 2 == 0 ? 0xFFCFCFCF : 0xFFC4C4C4;
                graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT - 1, color);
                if (filled && rows.get(i).id() != null && rows.get(i).id().equals(selected)) {
                    graphics.renderOutline(x, y, ROW_WIDTH, ROW_HEIGHT - 1, 0xFFFFC83D);
                }
            }
        }
        if (tab == AuctionQuery.SELL) {
            drawHighlightSlot(graphics, leftPos + AuctionMenu.SELL_X, topPos + AuctionMenu.SELL_Y);
        }
        drawPlayerInventory(graphics, leftPos + AuctionMenu.INVENTORY_X, topPos + AuctionMenu.INVENTORY_Y);
        // A groove above the inventory and panel
        graphics.fill(leftPos + 8, topPos + 148, leftPos + imageWidth - 8, topPos + 149, 0xFF8B8B8B);
        graphics.fill(leftPos + 8, topPos + 149, leftPos + imageWidth - 8, topPos + 150, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, LABEL_COLOR, false);
        drawSelectedTab(graphics, tabs[tab]);
        AuctionPage shown = menu.page();
        if (tab != AuctionQuery.SELL) {
            Component pageText = Component.literal(page + "/" + Math.max(1, shown.pages()));
            graphics.drawString(font, pageText, 276 - font.width(pageText) / 2, 24, LABEL_COLOR, false);
        }
        if (tab == AuctionQuery.COLLECT) {
            graphics.drawString(font, Component.translatable("gui.reikoeconomy.auction.collect_header"), 8, 24, LABEL_COLOR, false);
        }

        switch (tab) {
            case AuctionQuery.SELL -> drawSellHelp(graphics, shown);
            default -> drawRows(graphics);
        }
        switch (tab) {
            case AuctionQuery.SELL -> drawSellPanel(graphics);
            case AuctionQuery.COLLECT -> drawCollectPanel(graphics, shown);
            default -> drawListingPanel(graphics);
        }
        if (tab == AuctionQuery.SELL) {
            drawFeedback(graphics, ROW_X, ROW_Y + 82, ROW_WIDTH, 2);
        } else {
            drawFeedback(graphics, PANEL_X, FEEDBACK_Y, PANEL_WIDTH, 2);
        }
    }

    private void drawRows(GuiGraphics graphics) {
        List<AuctionRow> rows = rows();
        if (rows.isEmpty()) {
            String key = switch (tab) {
                case AuctionQuery.MINE -> "gui.reikoeconomy.auction.none_mine";
                case AuctionQuery.COLLECT -> "gui.reikoeconomy.auction.none_waiting";
                default -> searchBox.getValue().isBlank() ? "gui.reikoeconomy.auction.none" : "gui.reikoeconomy.auction.none_found";
            };
            graphics.drawString(font, Component.translatable(key), ROW_X + 4, ROW_Y + 5, MUTED_COLOR, false);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            AuctionRow row = rows.get(i);
            int x = ROW_X;
            int y = ROW_Y + i * ROW_HEIGHT;
            graphics.renderItem(row.item(), x + 1, y);
            graphics.renderItemDecorations(font, row.item(), x + 1, y);
            graphics.drawString(font, fit(row.item().getHoverName(), 110), x + 20, y + 5, LABEL_COLOR, false);
            if (row.id() == null) {
                graphics.drawString(font, Component.translatable("gui.reikoeconomy.auction.ready"), x + 134, y + 5, 0x1B7A2B, false);
                continue;
            }
            int priceColor = row.leading() ? 0x1B7A2B : row.auction() ? 0x8A5A00 : LABEL_COLOR;
            graphics.drawString(font, fit(Component.literal(format(row.price())), 72), x + 134, y + 5, priceColor, false);
            Component time = row.auction() ? timeLeft(millisLeft(row)) : Component.translatable("gui.reikoeconomy.auction.buy_now_short");
            graphics.drawString(font, fit(time, 48), x + 210, y + 5, row.auction() ? LABEL_COLOR : MUTED_COLOR, false);
            Component seller = row.own() ? Component.translatable("gui.reikoeconomy.auction.you") : Component.literal(row.seller());
            graphics.drawString(font, fit(seller, 40), x + 262, y + 5, MUTED_COLOR, false);
        }
    }

    private void drawListingPanel(GuiGraphics graphics) {
        Optional<AuctionRow> selectedRow = selectedRow();
        if (selectedRow.isEmpty()) {
            drawWrapped(graphics, Component.translatable("gui.reikoeconomy.auction.pick"), PANEL_Y, MUTED_COLOR, 3);
            if (ClientBalance.isKnown()) {
                graphics.drawString(font, Component.translatable("gui.reikoeconomy.balance", format(ClientBalance.get())), PANEL_X,
                        PANEL_Y + 34, LABEL_COLOR, false);
            }
            return;
        }
        AuctionRow row = selectedRow.get();
        graphics.drawString(font, fit(describe(row.item()), PANEL_WIDTH), PANEL_X, PANEL_Y, LABEL_COLOR, false);
        Component by = row.own() ? Component.translatable("gui.reikoeconomy.auction.your_listing")
                : Component.translatable("gui.reikoeconomy.auction.by", row.seller());
        graphics.drawString(font, fit(by, PANEL_WIDTH), PANEL_X, PANEL_Y + 10, MUTED_COLOR, false);
        Component price;
        if (!row.auction()) {
            price = Component.translatable("gui.reikoeconomy.auction.price", format(row.price()));
        } else if (row.bidder().isEmpty()) {
            price = Component.translatable("gui.reikoeconomy.auction.starting_bid", format(row.price()));
        } else if (row.leading()) {
            price = Component.translatable("gui.reikoeconomy.auction.winning", format(row.price()));
        } else {
            price = Component.translatable("gui.reikoeconomy.auction.top_bid", format(row.price()), row.bidder());
        }
        graphics.drawString(font, fit(price, PANEL_WIDTH), PANEL_X, PANEL_Y + 20, row.leading() ? 0x1B7A2B : LABEL_COLOR, false);
        graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.auction.ends_in", timeLeft(millisLeft(row))), PANEL_WIDTH),
                PANEL_X, PANEL_Y + 30, MUTED_COLOR, false);
    }

    private void drawSellPanel(GuiGraphics graphics) {
        ItemStack item = menu.sellItem();
        int textX = AuctionMenu.SELL_X + 22;
        int width = PANEL_X + PANEL_WIDTH - textX;
        if (item.isEmpty()) {
            drawWrappedAt(graphics, Component.translatable("gui.reikoeconomy.auction.put_item"), textX, AuctionMenu.SELL_Y, width, MUTED_COLOR, 2);
        } else {
            graphics.drawString(font, fit(describe(item), width), textX, AuctionMenu.SELL_Y + 4, LABEL_COLOR, false);
        }
        AuctionRules rules = EconomyConfig.auctionRules();
        if (!auctionType) {
            graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.auction.lasts_days", rules.listingMillis() / 86_400_000L), 64),
                    PANEL_X, 206, MUTED_COLOR, false);
        }
        graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.auction.help.count", menu.page().mine(), rules.maxListings()),
                PANEL_WIDTH), PANEL_X, 226, MUTED_COLOR, false);
    }

    private void drawSellHelp(GuiGraphics graphics, AuctionPage shown) {
        AuctionRules rules = EconomyConfig.auctionRules();
        List<Component> help = new ArrayList<>();
        help.add(Component.translatable("gui.reikoeconomy.auction.help.how"));
        help.add(Component.translatable("gui.reikoeconomy.auction.help.buy_now", rules.listingMillis() / 86_400_000L));
        help.add(Component.translatable("gui.reikoeconomy.auction.help.auction"));
        help.add(Component.translatable("gui.reikoeconomy.auction.help.fees", format(rules.listingFee()),
                TaxRates.percentText(rules.cutBasisPoints())));
        int y = ROW_Y - 14;
        for (Component line : help) {
            for (FormattedCharSequence part : font.split(line, ROW_WIDTH)) {
                graphics.drawString(font, part, ROW_X, y, LABEL_COLOR, false);
                y += 10;
            }
            y += 2;
        }
    }

    private void drawCollectPanel(GuiGraphics graphics, AuctionPage shown) {
        drawWrapped(graphics, shown.waiting() > 0
                ? Component.translatable("gui.reikoeconomy.auction.waiting", shown.waiting())
                : Component.translatable("gui.reikoeconomy.auction.none_waiting"), PANEL_Y, LABEL_COLOR, 2);
        drawWrapped(graphics, Component.translatable("gui.reikoeconomy.auction.collect_hint"), PANEL_Y + 20, MUTED_COLOR, 2);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int y, int color, int maxLines) {
        drawWrappedAt(graphics, text, PANEL_X, y, PANEL_WIDTH, color, maxLines);
    }

    private void drawWrappedAt(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int index = tab == AuctionQuery.SELL ? -1 : rowAt(mouseX, mouseY);
        if (index >= 0 && index < rows().size()) {
            AuctionRow row = rows().get(index);
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(row.item()));
            if (row.id() != null) {
                lines.add(Component.empty());
                if (!row.auction()) {
                    lines.add(Component.translatable("gui.reikoeconomy.auction.price", format(row.price())).withStyle(ChatFormatting.GOLD));
                } else if (row.bidder().isEmpty()) {
                    lines.add(Component.translatable("gui.reikoeconomy.auction.starting_bid", format(row.price())).withStyle(ChatFormatting.GOLD));
                } else {
                    lines.add(Component.translatable("gui.reikoeconomy.auction.top_bid", format(row.price()), row.bidder()).withStyle(ChatFormatting.GOLD));
                }
                lines.add(Component.translatable("gui.reikoeconomy.auction.ends_in", timeLeft(millisLeft(row))).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.reikoeconomy.auction.by", row.seller()).withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private Component describe(ItemStack item) {
        return item.getCount() == 1 ? item.getHoverName()
                : Component.translatable("auction.reikoeconomy.stack", item.getCount(), item.getHoverName());
    }

    /** "2d 3h", "5h 12m", "12m" or "45s". */
    static Component timeLeft(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3600;
        long minutes = seconds % 3600 / 60;
        if (days > 0) {
            return Component.translatable("gui.reikoeconomy.auction.time.days", days, hours);
        }
        if (hours > 0) {
            return Component.translatable("gui.reikoeconomy.auction.time.hours", hours, minutes);
        }
        if (minutes > 0) {
            return Component.translatable("gui.reikoeconomy.auction.time.minutes", minutes);
        }
        return Component.translatable("gui.reikoeconomy.auction.time.seconds", seconds);
    }
}
