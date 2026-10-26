package com.reiko.economy.client.screen;

import com.reiko.economy.core.Money;
import com.reiko.economy.menu.TradeMenu;
import com.reiko.economy.network.ClientBalance;
import com.reiko.economy.network.TradeActionPayload;
import com.reiko.economy.network.TradeStatePayload;
import com.reiko.economy.trade.TradeRules;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The trade window. Left: what you give (item slots and a money box). Right: what you get, which updates live as
 * the other player changes it. Accept in the middle; it's locked for a moment after any change, and any change
 * clears both accepts. Closing the window cancels the trade.
 */
public final class TradeScreen extends ShopScreenBase<TradeMenu> {
    private static final int GRID_WIDTH = TradeMenu.COLUMNS * 18;
    private static final int GRID_HEIGHT = 3 * 18;
    private static final int MONEY_Y = 87;
    private static final int FEE_Y = 104;
    private static final int STATUS_Y = 116;
    private static final int FEEDBACK_Y = 128;
    private static final int BUTTON_X = 88;
    private static final int BUTTON_WIDTH = 54;
    private static final int SEND_AFTER_TICKS = 15;
    private static final int ACCEPTED_COLOR = 0xFF2E9E44;
    private static final int ACCEPTED_TEXT = 0x1B7A2B;
    private static final int CHANGED_COLOR = 0xFFE8A33A;
    /** How long changed slots stay highlighted, at least. */
    private static final long HIGHLIGHT_MILLIS = 3_000;

    private EditBox moneyBox;
    private Button acceptButton;
    /** The money box has changes the server hasn't seen yet. */
    private boolean moneyPending;
    private int idleTicks;
    /** Set while the screen fills the money box itself, so that doesn't count as typing. */
    private boolean settingMoney;
    private TradeStatePayload shownState = TradeStatePayload.EMPTY;
    /** The other player's offer as of the last revision, to spot what they changed. */
    private final List<ItemStack> theirLastItems = new ArrayList<>();
    private long theirLastMoney;
    private final boolean[] changedSlots = new boolean[TradeRules.OFFER_SLOTS];
    private boolean moneyChanged;
    private long highlightUntil;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TradeMenu.WIDTH, TradeMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        moneyBox = addRenderableWidget(new EditBox(font, leftPos + TradeMenu.MY_X, topPos + MONEY_Y, GRID_WIDTH, 14,
                Component.translatable("gui.reikoeconomy.trade.money")));
        moneyBox.setMaxLength(16);
        moneyBox.setFilter(MONEY_FILTER);
        moneyBox.setHint(Component.translatable("gui.reikoeconomy.trade.money").withColor(MUTED_COLOR));
        moneyBox.setTooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.trade.money_tooltip")));
        moneyBox.setResponder(text -> {
            if (!settingMoney) {
                moneyPending = true;
                idleTicks = 0;
            }
        });
        acceptButton = addRenderableWidget(Button.builder(Component.empty(), button -> pressAccept())
                .bounds(leftPos + BUTTON_X, topPos + 38, BUTTON_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.reikoeconomy.trade.accept_tooltip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.reikoeconomy.trade.cancel"), button -> onClose())
                .bounds(leftPos + BUTTON_X, topPos + 62, BUTTON_WIDTH, 16).build());
        syncMoneyBox();
        updateAcceptButton();
    }

    private void pressAccept() {
        if (moneyPending) {
            sendMoney(); // the offer changes, so there's a short wait before accepting
            return;
        }
        TradeStatePayload state = menu.state();
        int action = state.myAccepted() ? TradeActionPayload.UNACCEPT : TradeActionPayload.ACCEPT;
        PacketDistributor.sendToServer(new TradeActionPayload(menu.containerId, action, state.revision(), ""));
    }

    private void sendMoney() {
        moneyPending = false;
        PacketDistributor.sendToServer(new TradeActionPayload(menu.containerId, TradeActionPayload.MONEY, 0, moneyBox.getValue()));
    }

    @Override
    protected void onEnter() {
        sendMoney();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (moneyPending && ++idleTicks >= SEND_AFTER_TICKS) {
            sendMoney();
        }
        if (menu.state() != shownState) {
            TradeStatePayload before = shownState;
            shownState = menu.state();
            if (before.myAccepted() && !shownState.myAccepted()) {
                if (shownState.revision() != before.revision()) {
                    showFeedback(Component.translatable("gui.reikoeconomy.trade.changed"), false);
                } else {
                    clearFeedback(); // took back the accept, or the trade couldn't go through (that sends its own reason)
                }
            }
            if (shownState.revision() != before.revision()) {
                spotChanges(before == TradeStatePayload.EMPTY);
            }
            syncMoneyBox();
        }
        updateAcceptButton();
    }

    /**
     * Marks which of the other player's slots changed since the last revision, so a swapped item stands out.
     * The items arrive before the new state, so the slots are already up to date here.
     */
    private void spotChanges(boolean first) {
        boolean any = false;
        for (int i = 0; i < TradeRules.OFFER_SLOTS; i++) {
            ItemStack now = menu.getSlot(TradeRules.OFFER_SLOTS + i).getItem();
            ItemStack before = i < theirLastItems.size() ? theirLastItems.get(i) : ItemStack.EMPTY;
            changedSlots[i] = !first && !ItemStack.matches(before, now);
            any |= changedSlots[i];
        }
        moneyChanged = !first && shownState.theirMoney() != theirLastMoney;
        if (any || moneyChanged) {
            highlightUntil = System.currentTimeMillis() + Math.max(HIGHLIGHT_MILLIS, menu.lockLeft());
        }
        theirLastItems.clear();
        for (int i = 0; i < TradeRules.OFFER_SLOTS; i++) {
            theirLastItems.add(menu.getSlot(TradeRules.OFFER_SLOTS + i).getItem().copy());
        }
        theirLastMoney = shownState.theirMoney();
    }

    /** Shows the money the server has for this player, unless they're in the middle of changing it. */
    private void syncMoneyBox() {
        if (moneyPending) {
            return;
        }
        long offered = menu.state().myMoney();
        OptionalLong typed = moneyBox.getValue().isBlank() ? OptionalLong.of(0) : Money.parse(moneyBox.getValue());
        if (typed.isEmpty() || typed.getAsLong() != offered) {
            settingMoney = true;
            moneyBox.setValue(offered == 0 ? "" : Money.toPlainString(offered));
            settingMoney = false;
        }
    }

    private void updateAcceptButton() {
        TradeStatePayload state = menu.state();
        long lockLeft = menu.lockLeft();
        if (state.myAccepted()) {
            acceptButton.setMessage(Component.translatable("gui.reikoeconomy.trade.undo"));
            acceptButton.active = true;
        } else if (lockLeft > 0 && !moneyPending) {
            acceptButton.setMessage(Component.translatable("gui.reikoeconomy.trade.wait", (lockLeft + 999) / 1000));
            acceptButton.active = false;
        } else {
            acceptButton.setMessage(Component.translatable("gui.reikoeconomy.trade.accept"));
            acceptButton.active = moneyPending || !bothEmpty();
        }
    }

    private boolean bothEmpty() {
        if (menu.state().myMoney() > 0 || menu.state().theirMoney() > 0) {
            return false;
        }
        for (int i = 0; i < 2 * TradeRules.OFFER_SLOTS; i++) {
            if (menu.getSlot(i).hasItem()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        TradeStatePayload state = menu.state();
        drawOffer(graphics, TradeMenu.MY_X, state.myAccepted());
        drawOffer(graphics, TradeMenu.THEIR_X, state.theirAccepted());
        boolean highlight = System.currentTimeMillis() < highlightUntil;
        if (highlight) {
            for (int i = 0; i < TradeRules.OFFER_SLOTS; i++) {
                if (changedSlots[i]) {
                    int slotX = leftPos + TradeMenu.THEIR_X + (i % TradeMenu.COLUMNS) * 18;
                    int slotY = topPos + TradeMenu.GRID_Y + (i / TradeMenu.COLUMNS) * 18;
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, CHANGED_COLOR);
                }
            }
        }
        // The other player's money, in a sunken box like the one you type in
        int x = leftPos + TradeMenu.THEIR_X;
        int y = topPos + MONEY_Y;
        graphics.fill(x - 1, y - 1, x + GRID_WIDTH + 1, y + 15, highlight && moneyChanged ? CHANGED_COLOR : 0xFFA0A0A0);
        graphics.fill(x, y, x + GRID_WIDTH, y + 14, 0xFF000000);
        drawPlayerInventory(graphics, leftPos + TradeMenu.INVENTORY_X, topPos + TradeMenu.INVENTORY_Y);
    }

    /** An offer's slots, framed in green once that side has accepted. */
    private void drawOffer(GuiGraphics graphics, int gridX, boolean accepted) {
        int x = leftPos + gridX;
        int y = topPos + TradeMenu.GRID_Y;
        if (accepted) {
            graphics.fill(x - 3, y - 3, x + GRID_WIDTH + 1, y + GRID_HEIGHT + 1, ACCEPTED_COLOR);
        }
        drawSlotGrid(graphics, x, y, TradeMenu.COLUMNS, 3);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TradeStatePayload state = menu.state();
        Component balance = ClientBalance.isKnown()
                ? Component.translatable("gui.reikoeconomy.trade.bank", format(ClientBalance.get()))
                : Component.empty();
        int balanceWidth = Math.min(font.width(balance), 96);
        graphics.drawString(font, fit(balance, 96), imageWidth - 8 - balanceWidth, 6, MUTED_COLOR, false);
        graphics.drawString(font, fit(title, imageWidth - 24 - balanceWidth), 8, 6, LABEL_COLOR, false);

        graphics.drawString(font, Component.translatable("gui.reikoeconomy.trade.you_give"), TradeMenu.MY_X, 18, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.reikoeconomy.trade.you_get"), TradeMenu.THEIR_X, 18, LABEL_COLOR, false);

        Component theirMoney = state.theirMoney() > 0 ? Component.literal(format(state.theirMoney()))
                : Component.translatable("gui.reikoeconomy.trade.no_money");
        graphics.drawString(font, fit(theirMoney, GRID_WIDTH - 8), TradeMenu.THEIR_X + 4, MONEY_Y + 3,
                state.theirMoney() > 0 ? 0xE0E0E0 : 0x808080, false);

        if (state.myFee() > 0) {
            graphics.drawString(font, fit(Component.translatable("gui.reikoeconomy.trade.fee", format(state.myFee())), 100),
                    TradeMenu.MY_X, FEE_Y, MUTED_COLOR, false);
        }
        drawStatus(graphics, TradeMenu.MY_X, state.myAccepted());
        drawStatus(graphics, TradeMenu.THEIR_X, state.theirAccepted());
        drawFeedback(graphics, TradeMenu.MY_X, FEEDBACK_Y, imageWidth - 2 * TradeMenu.MY_X, 2);
    }

    private void drawStatus(GuiGraphics graphics, int x, boolean accepted) {
        Component text = Component.translatable(accepted ? "gui.reikoeconomy.trade.accepted" : "gui.reikoeconomy.trade.not_accepted");
        graphics.drawString(font, fit(text, Math.min(100, imageWidth - 8 - x)), x, STATUS_Y, accepted ? ACCEPTED_TEXT : MUTED_COLOR, false);
    }
}
