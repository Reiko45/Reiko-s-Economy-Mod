package com.reiko.economy.bank;

import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.WalletContents;
import com.reiko.economy.item.WalletItem;
import com.reiko.economy.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * All the cash a player carries: loose coins and bills in their inventory plus whatever is in their wallets.
 *
 * <p>Paying takes loose cash first, then cash from wallets. Cash handed to the player (change, ATM
 * withdrawals, payouts) goes into their first wallet if they carry one, so it never needs inventory room;
 * otherwise it goes into the inventory as the fewest coins and bills.
 */
public final class Purse {
    private Purse() {
    }

    /** The wallets the player carries: main hand, off hand, then the inventory in slot order. */
    public static List<ItemStack> wallets(Player player) {
        List<ItemStack> wallets = new ArrayList<>();
        addIfWallet(wallets, player.getMainHandItem());
        addIfWallet(wallets, player.getOffhandItem());
        for (ItemStack stack : player.getInventory().items) {
            addIfWallet(wallets, stack);
        }
        return wallets;
    }

    private static void addIfWallet(List<ItemStack> wallets, ItemStack stack) {
        if (stack.getItem() instanceof WalletItem && wallets.stream().noneMatch(wallet -> wallet == stack)) {
            wallets.add(stack);
        }
    }

    public static boolean hasWallet(Player player) {
        return !wallets(player).isEmpty();
    }

    public static WalletContents contents(ItemStack wallet) {
        WalletContents contents = wallet.get(ModDataComponents.WALLET.get());
        return contents == null ? WalletContents.EMPTY : contents;
    }

    public static void setContents(ItemStack wallet, WalletContents contents) {
        wallet.set(ModDataComponents.WALLET.get(), contents);
    }

    /** Every coin and bill the player carries, loose and in wallets. */
    public static Map<Denomination, Integer> count(Player player) {
        return count(player.getInventory().items, player);
    }

    /** The loose cash in {@code slots} (which may be a simulated copy of the inventory) plus the player's wallets. */
    public static Map<Denomination, Integer> count(List<ItemStack> slots, Player player) {
        Map<Denomination, Integer> pieces = new EnumMap<>(CashWallet.count(slots));
        for (ItemStack wallet : wallets(player)) {
            contents(wallet).pieces().forEach((denomination, count) -> pieces.merge(denomination, count, Integer::sum));
        }
        return pieces;
    }

    public static long value(Player player) {
        long total = CashWallet.value(player.getInventory().items);
        for (ItemStack wallet : wallets(player)) {
            total += contents(wallet).value();
        }
        return total;
    }

    /**
     * Takes as many of {@code pieces} as it can from the loose cash in {@code slots} and returns what's left,
     * which would come out of the wallets. Mutates the stacks in {@code slots}.
     */
    public static Map<Denomination, Integer> removeLoose(List<ItemStack> slots, Map<Denomination, Integer> pieces) {
        Map<Denomination, Integer> left = new EnumMap<>(Denomination.class);
        for (Map.Entry<Denomination, Integer> piece : pieces.entrySet()) {
            int wanted = piece.getValue();
            for (ItemStack stack : slots) {
                if (wanted <= 0) {
                    break;
                }
                if (stack.getItem() instanceof CashItem cash && cash.denomination() == piece.getKey()) {
                    int take = Math.min(wanted, stack.getCount());
                    stack.shrink(take);
                    wanted -= take;
                }
            }
            if (wanted > 0) {
                left.put(piece.getKey(), wanted);
            }
        }
        return left;
    }

    /** Takes {@code pieces} from the player: loose cash first, then their wallets. */
    public static void remove(Player player, Map<Denomination, Integer> pieces) {
        Map<Denomination, Integer> left = removeLoose(player.getInventory().items, pieces);
        for (ItemStack wallet : wallets(player)) {
            if (left.isEmpty()) {
                break;
            }
            WalletContents contents = contents(wallet);
            Map<Denomination, Integer> stillLeft = new EnumMap<>(Denomination.class);
            for (Map.Entry<Denomination, Integer> piece : left.entrySet()) {
                int take = Math.min(piece.getValue(), contents.count(piece.getKey()));
                contents = contents.plus(piece.getKey(), -take);
                if (piece.getValue() > take) {
                    stillLeft.put(piece.getKey(), piece.getValue() - take);
                }
            }
            setContents(wallet, contents);
            left = stillLeft;
        }
        player.getInventory().setChanged();
    }

    /** Takes every coin and bill the player carries, loose and in wallets, and returns what it was worth. */
    public static long takeAll(Player player) {
        long total = value(player);
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (CashItem.isCash(items.get(i))) {
                items.set(i, ItemStack.EMPTY);
            }
        }
        for (ItemStack wallet : wallets(player)) {
            setContents(wallet, WalletContents.EMPTY);
        }
        player.getInventory().setChanged();
        return total;
    }

    /**
     * Hands the player {@code amount} in cash: into their first wallet, or into the inventory as the fewest
     * coins and bills (anything that doesn't fit is dropped at their feet, so check {@link #fits} first).
     */
    public static void give(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        List<ItemStack> wallets = wallets(player);
        if (!wallets.isEmpty()) {
            ItemStack wallet = wallets.get(0);
            setContents(wallet, contents(wallet).plus(Denomination.breakDown(amount)));
        } else {
            for (ItemStack stack : CashItem.makeChange(amount)) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        player.getInventory().setChanged();
    }

    /**
     * Whether cash worth each of {@code amounts} could be handed to the player: always with a wallet, otherwise
     * if the coins and bills fit in {@code slots} (a simulated copy of the inventory, which this fills).
     */
    public static boolean fits(Player player, List<ItemStack> slots, long... amounts) {
        if (hasWallet(player)) {
            return true;
        }
        for (long amount : amounts) {
            if (amount > 0 && !InventorySpace.addAll(slots, CashItem.makeChange(amount))) {
                return false;
            }
        }
        return true;
    }
}
