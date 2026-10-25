package com.reiko.economy.bank;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.ShopActionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Moves money between bank balances and physical cash. Withdrawing takes money out of the balance and hands
 * over the fewest coins and bills; depositing destroys the cash and adds its value to the balance. So money
 * is never created here: the total of all balances plus all cash stays the same.
 */
public final class AtmService {
    private AtmService() {
    }

    /** Withdraws {@code amountText} (e.g. "37.41") as cash, if it fits in the player's inventory. */
    public static ShopActionResult withdraw(ServerPlayer player, String amountText) {
        OptionalLong parsed = Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long amount = parsed.getAsLong();
        EconomyService eco = EconomyService.INSTANCE;
        long limit = EconomyConfig.atmMaxWithdrawal();
        if (amount > limit) {
            return ShopActionResult.fail("atm.reikoeconomy.over_limit", eco.format(limit));
        }
        List<ItemStack> cash = CashItem.makeChange(amount);
        if (!canFit(player.getInventory(), cash)) {
            return ShopActionResult.fail("atm.reikoeconomy.no_room");
        }

        MinecraftServer server = player.server;
        eco.ensureAccount(server, player.getUUID(), player.getGameProfile().getName());
        TransactionResult result = eco.withdraw(server, player.getUUID(), amount, "atm.withdraw");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        List<ItemStack> before = InventorySync.snapshot(player);
        for (ItemStack stack : cash) {
            ItemHandlerHelper.giveItemToPlayer(player, stack); // always fits: checked above
        }
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.withdrew", eco.format(amount)));
    }

    /** Deposits all cash in the container (the ATM's deposit slots). */
    public static ShopActionResult depositFrom(ServerPlayer player, Container container) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            stacks.add(container.getItem(i));
        }
        ShopActionResult result = deposit(player, stacks);
        if (result.success()) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (CashItem.isCash(container.getItem(i))) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        return result;
    }

    /** Deposits all cash in the player's main inventory and hotbar. */
    public static ShopActionResult depositAll(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> before = InventorySync.snapshot(player);
        ShopActionResult result = deposit(player, inventory.items);
        if (result.success()) {
            for (int i = 0; i < inventory.items.size(); i++) {
                if (CashItem.isCash(inventory.items.get(i))) {
                    inventory.items.set(i, ItemStack.EMPTY);
                }
            }
            inventory.setChanged();
            InventorySync.sendChanges(player, before);
        }
        return result;
    }

    /** Credits the value of the cash in {@code stacks}. The caller removes the cash only if this succeeds. */
    private static ShopActionResult deposit(ServerPlayer player, List<ItemStack> stacks) {
        long total = 0;
        for (ItemStack stack : stacks) {
            total += CashItem.valueOf(stack);
        }
        if (total <= 0) {
            return ShopActionResult.fail("atm.reikoeconomy.nothing_to_deposit");
        }
        MinecraftServer server = player.server;
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, player.getUUID(), player.getGameProfile().getName());
        TransactionResult result = eco.deposit(server, player.getUUID(), total, "atm.deposit");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.deposited", eco.format(total)));
    }

    /**
     * Whether all {@code stacks} fit in the main inventory and hotbar, topping up matching stacks first and then
     * using empty slots, the way items are normally added.
     */
    static boolean canFit(Inventory inventory, List<ItemStack> stacks) {
        List<ItemStack> simulated = new ArrayList<>(inventory.items.size());
        for (ItemStack stack : inventory.items) {
            simulated.add(stack.copy());
        }
        for (ItemStack incoming : stacks) {
            int left = incoming.getCount();
            int max = incoming.getMaxStackSize();
            for (ItemStack slot : simulated) {
                if (left > 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, incoming) && slot.getCount() < max) {
                    int added = Math.min(max - slot.getCount(), left);
                    slot.grow(added);
                    left -= added;
                }
            }
            for (int i = 0; i < simulated.size() && left > 0; i++) {
                if (simulated.get(i).isEmpty()) {
                    int added = Math.min(max, left);
                    simulated.set(i, incoming.copyWithCount(added));
                    left -= added;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }
}
