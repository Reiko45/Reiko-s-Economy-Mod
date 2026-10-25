package com.reiko.economy.bank;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.DebitCard;
import com.reiko.economy.item.DebitCardItem;
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

/**
 * Moves money between bank accounts and physical cash, through a debit card inserted in the ATM.
 *
 * <p>Withdrawing takes money out of the card's account and hands the person at the ATM the fewest coins and
 * bills; depositing destroys cash and adds its value to the card's account. Money is never created here: the
 * total of all balances plus all cash stays the same.
 */
public final class AtmService {
    private AtmService() {
    }

    /** Withdraws {@code amountText} (e.g. "37.41") from the card's account as cash, if it fits in the inventory. */
    public static ShopActionResult withdraw(ServerPlayer player, ItemStack card, String pin, String amountText) {
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
        if (!InventorySpace.addAll(InventorySpace.copyOf(player.getInventory().items), cash)) {
            return ShopActionResult.fail("atm.reikoeconomy.no_room");
        }
        // Check the card last, so a typo in the amount doesn't cost a PIN try.
        CardService.Authorization auth = authorize(player, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }

        MinecraftServer server = player.server;
        TransactionResult result = eco.withdraw(server, auth.account(), amount, "atm.withdraw");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        List<ItemStack> before = InventorySync.snapshot(player);
        for (ItemStack stack : cash) {
            player.getInventory().add(stack); // always fits: checked above
        }
        player.getInventory().setChanged();
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.withdrew", eco.format(amount),
                eco.format(eco.getBalance(server, auth.account()))));
    }

    /** Deposits all cash in the container (the ATM's deposit slots) into the card's account. */
    public static ShopActionResult depositFrom(ServerPlayer player, ItemStack card, String pin, Container container) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            stacks.add(container.getItem(i));
        }
        ShopActionResult result = deposit(player, card, pin, stacks);
        if (result.success()) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (CashItem.isCash(container.getItem(i))) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        return result;
    }

    /** Deposits all cash in the player's main inventory and hotbar into the card's account. */
    public static ShopActionResult depositAll(ServerPlayer player, ItemStack card, String pin) {
        Inventory inventory = player.getInventory();
        List<ItemStack> before = InventorySync.snapshot(player);
        ShopActionResult result = deposit(player, card, pin, inventory.items);
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

    /** Shows the balance of the card's account. */
    public static ShopActionResult checkBalance(ServerPlayer player, ItemStack card, String pin) {
        CardService.Authorization auth = authorize(player, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        DebitCard data = DebitCardItem.cardOf(card);
        EconomyService eco = EconomyService.INSTANCE;
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.balance", data.ownerName(), data.lastFour(),
                eco.format(eco.getBalance(player.server, auth.account()))));
    }

    /** Credits the value of the cash in {@code stacks}. The caller removes the cash only if this succeeds. */
    private static ShopActionResult deposit(ServerPlayer player, ItemStack card, String pin, List<ItemStack> stacks) {
        long total = 0;
        for (ItemStack stack : stacks) {
            total += CashItem.valueOf(stack);
        }
        if (total <= 0) {
            return ShopActionResult.fail("atm.reikoeconomy.nothing_to_deposit");
        }
        CardService.Authorization auth = authorize(player, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        MinecraftServer server = player.server;
        EconomyService eco = EconomyService.INSTANCE;
        TransactionResult result = eco.deposit(server, auth.account(), total, "atm.deposit");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.deposited", eco.format(total),
                eco.format(eco.getBalance(server, auth.account()))));
    }

    private static CardService.Authorization authorize(ServerPlayer player, ItemStack card, String pin) {
        if (DebitCardItem.cardOf(card) == null) {
            return new CardService.Authorization(null, Component.translatable("atm.reikoeconomy.insert_card"));
        }
        return CardService.authorize(player.server, card, pin);
    }
}
