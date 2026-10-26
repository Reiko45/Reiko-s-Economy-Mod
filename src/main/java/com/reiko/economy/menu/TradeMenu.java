package com.reiko.economy.menu;

import com.reiko.economy.network.TradeStatePayload;
import com.reiko.economy.registry.ModMenus;
import com.reiko.economy.trade.TradeRules;
import com.reiko.economy.trade.TradeService;
import com.reiko.economy.trade.TradeSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One player's trade window: their own offer slots, the other player's offer (shown, not touchable) and their
 * inventory. Items put in the offer slots live in the {@link TradeSession} until the trade ends. The money and
 * accepts aren't slots; the server sends them as a {@link TradeStatePayload} whenever they change.
 */
public final class TradeMenu extends ShopMenuBase {
    public static final int WIDTH = 230;
    public static final int HEIGHT = 236;
    public static final int COLUMNS = 4;
    public static final int MY_X = 12;
    public static final int THEIR_X = 146;
    public static final int GRID_Y = 29;
    public static final int INVENTORY_X = 34;
    public static final int INVENTORY_Y = 152;
    private static final int THEIR_FIRST = TradeRules.OFFER_SLOTS;
    private static final int INVENTORY_FIRST = 2 * TradeRules.OFFER_SLOTS;

    private final Player player;
    /** Server only. */
    @Nullable
    private final TradeSession session;
    /** Server: the session version this player's screen last heard about. */
    private int sentVersion = -1;
    /** Server: set while the window is closing, so the trade doesn't try to close it again. */
    private boolean closing;
    /** Client: the latest money and accepts from the server, and when they arrived. */
    private TradeStatePayload state = TradeStatePayload.EMPTY;
    private long stateReceivedAt = System.currentTimeMillis();

    /** Client side. */
    public TradeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainer(TradeRules.OFFER_SLOTS), new SimpleContainer(TradeRules.OFFER_SLOTS));
    }

    /** Server side. */
    public TradeMenu(int containerId, Inventory inventory, TradeSession session) {
        this(containerId, inventory, session, session.side(inventory.player).items(),
                session.other(session.side(inventory.player)).items());
    }

    private TradeMenu(int containerId, Inventory inventory, @Nullable TradeSession session, Container mine, Container theirs) {
        super(ModMenus.TRADE.get(), containerId);
        this.player = inventory.player;
        this.session = session;
        for (int i = 0; i < TradeRules.OFFER_SLOTS; i++) {
            addSlot(new Slot(mine, i, MY_X + (i % COLUMNS) * 18, GRID_Y + (i / COLUMNS) * 18));
        }
        for (int i = 0; i < TradeRules.OFFER_SLOTS; i++) {
            addSlot(new GhostSlot(theirs, i, THEIR_X + (i % COLUMNS) * 18, GRID_Y + (i / COLUMNS) * 18));
        }
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
    }

    @Nullable
    public TradeSession session() {
        return session;
    }

    public boolean isClosing() {
        return closing;
    }

    public TradeStatePayload state() {
        return state;
    }

    public void setState(TradeStatePayload state) {
        this.state = state;
        this.stateReceivedAt = System.currentTimeMillis();
    }

    /** Client: milliseconds left before accepting is allowed again. */
    public long lockLeft() {
        return Math.max(0, state.lockMillis() - (System.currentTimeMillis() - stateReceivedAt));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges(); // items first, so the screen has them when the new state arrives
        if (session != null && player instanceof ServerPlayer serverPlayer && session.version() != sentVersion && !session.finished()) {
            sentVersion = session.version();
            TradeService.sendState(serverPlayer, this, session);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= THEIR_FIRST && index < INVENTORY_FIRST) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < THEIR_FIRST) {
            if (!moveItemStackTo(stack, INVENTORY_FIRST, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, THEIR_FIRST, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return session == null || !session.finished(); // distance and the like are checked by TradeService.tick
    }

    @Override
    public void removed(Player player) {
        closing = true;
        super.removed(player);
        if (session != null && player instanceof ServerPlayer serverPlayer) {
            TradeService.closed(serverPlayer, session);
        }
    }
}
