package com.reiko.economy.menu;

import com.reiko.economy.auction.AuctionPage;
import com.reiko.economy.auction.AuctionQuery;
import com.reiko.economy.registry.ModMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The auction house screen's menu: one slot for the item to sell, and the player's inventory. The listings
 * themselves aren't slots; the client asks for a page ({@code AuctionQueryPayload}) and the server sends it back
 * ({@code AuctionPagePayload}). Anything left in the sell slot goes back to the player on close.
 */
public final class AuctionMenu extends ShopMenuBase {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;
    public static final int SELL_SLOT = 0;
    public static final int SELL_X = 180;
    public static final int SELL_Y = 156;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 156;

    private final SimpleContainer sell = new SimpleContainer(1);
    /** Server: what the screen last asked for, to refresh it after a change. */
    private AuctionQuery query = AuctionQuery.DEFAULT;
    /** Client: the page being shown, and when it arrived. */
    private AuctionPage page = AuctionPage.EMPTY;
    private long pageReceivedAt = System.currentTimeMillis();
    /** Client: the sell slot only shows on the Sell tab. */
    private boolean sellSlotShown;

    public AuctionMenu(int containerId, Inventory inventory) {
        super(ModMenus.AUCTION.get(), containerId);
        addSlot(new Slot(sell, 0, SELL_X, SELL_Y) {
            @Override
            public boolean isActive() {
                return sellTabOpen(inventory.player);
            }
        });
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public ItemStack sellItem() {
        return sell.getItem(0);
    }

    /** Empties the sell slot after the item was listed. */
    public void clearSellItem() {
        sell.setItem(0, ItemStack.EMPTY);
        broadcastChanges();
    }

    public AuctionQuery query() {
        return query;
    }

    public void setQuery(AuctionQuery query) {
        this.query = query;
    }

    public AuctionPage page() {
        return page;
    }

    /** Milliseconds since the page arrived, for counting timers down. */
    public long pageAge() {
        return System.currentTimeMillis() - pageReceivedAt;
    }

    public void setPage(AuctionPage page) {
        this.page = page;
        this.pageReceivedAt = System.currentTimeMillis();
    }

    public void setSellSlotShown(boolean shown) {
        this.sellSlotShown = shown;
    }

    /** Whether the Sell tab, and so the sell slot, is showing: as the screen says, or as its last query said. */
    private boolean sellTabOpen(Player player) {
        return player.level().isClientSide ? sellSlotShown : query.tab() == AuctionQuery.SELL;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == SELL_SLOT) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!sellTabOpen(player) || !moveItemStackTo(stack, SELL_SLOT, SELL_SLOT + 1, false)) {
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
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, sell);
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // usable from anywhere with /ah
    }
}
