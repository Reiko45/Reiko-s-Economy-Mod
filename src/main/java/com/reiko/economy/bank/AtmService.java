package com.reiko.economy.bank;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.item.BankCard;
import com.reiko.economy.item.Cards;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.ShopActionResult;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Moves money between bank accounts and physical cash, through a debit card inserted in the ATM.
 *
 * <p>Withdrawing takes money out of the card's account and hands the person at the ATM the fewest coins and
 * bills; depositing destroys cash and adds its value to the card's account. Money is never created here: the
 * total of all balances plus all cash stays the same.
 *
 * <p>With a credit card inserted the same buttons work the credit account instead (see {@link CreditService}):
 * withdrawing is a cash advance, depositing pays the bill, and the balance shows what's owed.
 */
public final class AtmService {
    private AtmService() {
    }

    /** Withdraws {@code amountText} (e.g. "37.41") from the card's account as cash, if it fits in the inventory. */
    public static ShopActionResult withdraw(ServerPlayer player, ItemStack card, String pin, String amountText) {
        if (Cards.isCredit(card)) {
            return CreditService.cashAdvance(player, card, pin, amountText);
        }
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
        if (!Purse.fits(player, InventorySpace.copyOf(player.getInventory().items), amount)) {
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
        Purse.give(player, amount); // fits: checked above
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.withdrew", eco.format(amount),
                eco.format(eco.getBalance(server, auth.account()))));
    }

    /** Deposits all cash in the container (the ATM's deposit slots) into the card's account. */
    public static ShopActionResult depositFrom(ServerPlayer player, ItemStack card, String pin, Container container) {
        if (Cards.isCredit(card)) {
            return CreditService.payWithCash(player, card, pin, container);
        }
        long total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            total += CashItem.valueOf(container.getItem(i));
        }
        ShopActionResult result = deposit(player, card, pin, total);
        if (result.success()) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (CashItem.isCash(container.getItem(i))) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        return result;
    }

    /** Deposits all the cash the player carries, loose and in wallets, into the card's account. */
    public static ShopActionResult depositAll(ServerPlayer player, ItemStack card, String pin) {
        if (Cards.isCredit(card)) {
            return CreditService.payWithAllCash(player, card, pin);
        }
        List<ItemStack> before = InventorySync.snapshot(player);
        ShopActionResult result = deposit(player, card, pin, Purse.value(player));
        if (result.success()) {
            Purse.takeAll(player);
            InventorySync.sendChanges(player, before);
        }
        return result;
    }

    /** Shows the balance of the card's account. */
    public static ShopActionResult checkBalance(ServerPlayer player, ItemStack card, String pin) {
        if (Cards.isCredit(card)) {
            return CreditService.status(player, card, pin);
        }
        CardService.Authorization auth = authorize(player, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        BankCard data = DebitCardItem.cardOf(card);
        EconomyService eco = EconomyService.INSTANCE;
        return ShopActionResult.ok(Component.translatable("atm.reikoeconomy.balance", data.ownerName(), data.lastFour(),
                eco.format(eco.getBalance(player.server, auth.account()))));
    }

    /** Pays a credit card bill from the cardholder's bank account. Only works with a credit card inserted. */
    public static ShopActionResult payBill(ServerPlayer player, ItemStack card, String pin, String amountText) {
        if (!Cards.isCredit(card)) {
            return ShopActionResult.fail(card.isEmpty() ? "atm.reikoeconomy.insert_card" : "atm.reikoeconomy.not_credit");
        }
        return CreditService.payFromBank(player, card, pin, amountText);
    }

    /** Credits {@code total} worth of cash. The caller removes the cash only if this succeeds. */
    private static ShopActionResult deposit(ServerPlayer player, ItemStack card, String pin, long total) {
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
