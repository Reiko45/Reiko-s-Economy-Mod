package com.reiko.economy.menu;

import com.reiko.economy.bank.Purse;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.WalletContents;
import com.reiko.economy.item.WalletItem;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.registry.ModMenus;
import com.reiko.economy.shop.ShopActionResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The open wallet: a slot that drops cash into the wallet, and the player's inventory. Taking cash out goes
 * through {@code WalletActionPayload}. The wallet itself stays in the hand it was opened from and can't be moved
 * while it's open.
 */
public final class WalletMenu extends ShopMenuBase {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 166;
    public static final int ADD_X = 152;
    public static final int ADD_Y = 18;
    public static final int INVENTORY_Y = 84;
    /** Pass as the denomination to take everything out. */
    public static final int ALL = -1;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer addSlot = new SimpleContainer(1);

    /** Client constructor: the server says which hand holds the wallet. */
    public WalletMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readEnum(InteractionHand.class));
    }

    public WalletMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.WALLET.get(), containerId);
        this.player = inventory.player;
        this.hand = hand;
        addSlot(new Slot(addSlot, 0, ADD_X, ADD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CashItem.isCash(stack);
            }

            /** Cash put here goes straight into the wallet. */
            @Override
            public void set(ItemStack stack) {
                if (stack.getItem() instanceof CashItem cash && wallet().getItem() instanceof WalletItem) {
                    Purse.setContents(wallet(), Purse.contents(wallet()).plus(cash.denomination(), stack.getCount()));
                    super.set(ItemStack.EMPTY);
                } else {
                    super.set(stack);
                }
            }
        });
        addPlayerInventory(inventory, 8, INVENTORY_Y);
    }

    /** The open wallet. */
    public ItemStack wallet() {
        return player.getItemInHand(hand);
    }

    public WalletContents contents() {
        return Purse.contents(wallet());
    }

    /**
     * Moves cash from the wallet into the inventory: {@code count} of one denomination (by ordinal), or
     * everything with {@link #ALL}. Takes what fits and says so if the inventory fills up.
     */
    public ShopActionResult take(ServerPlayer player, int denomination, int count) {
        ItemStack wallet = wallet();
        if (!(wallet.getItem() instanceof WalletItem) || denomination >= Denomination.values().length || (denomination < 0 && denomination != ALL)) {
            return ShopActionResult.fail(Component.empty());
        }
        WalletContents contents = Purse.contents(wallet);
        Denomination[] all = Denomination.values();
        boolean full = false;
        for (int i = all.length - 1; i >= 0 && !full; i--) {
            if (denomination != ALL && i != denomination) {
                continue;
            }
            int wanted = denomination == ALL ? contents.count(all[i]) : Math.min(Math.max(0, count), contents.count(all[i]));
            // Only take out what fits. (Inventory.add on its own would delete the rest for a creative player.)
            ItemStack piece = new ItemStack(ModItems.cash(all[i]));
            int moved = Math.min(wanted, room(player.getInventory(), piece));
            for (int left = moved; left > 0; left -= 64) {
                player.getInventory().add(piece.copyWithCount(Math.min(64, left)));
            }
            contents = contents.plus(all[i], -moved);
            full = moved < wanted;
        }
        Purse.setContents(wallet, contents);
        player.getInventory().setChanged();
        return full ? ShopActionResult.fail("wallet.reikoeconomy.inventory_full") : ShopActionResult.ok(Component.empty());
    }

    /** How many of {@code item} fit in the main inventory and hotbar. */
    private static int room(Inventory inventory, ItemStack item) {
        int room = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) {
                room += item.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(stack, item)) {
                room += Math.max(0, item.getMaxStackSize() - stack.getCount());
            }
        }
        return room;
    }

    /** Shift-clicking cash in the inventory puts the whole stack into the wallet. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index > 0 && slot.getItem().getItem() instanceof CashItem cash && wallet().getItem() instanceof WalletItem) {
            Purse.setContents(wallet(), Purse.contents(wallet()).plus(cash.denomination(), slot.getItem().getCount()));
            slot.setByPlayer(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    /** The open wallet can't be picked up, swapped or thrown while it's open. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId).getItem() == wallet()) {
            return;
        }
        if (clickType == ClickType.SWAP && (hand == InteractionHand.MAIN_HAND ? button == player.getInventory().selected : button == 40)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, addSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        return wallet().getItem() instanceof WalletItem;
    }
}
