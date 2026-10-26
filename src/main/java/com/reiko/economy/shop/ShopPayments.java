package com.reiko.economy.shop;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.CashWallet;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.CashPlan;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxService;
import com.reiko.economy.item.Cards;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Takes a shop payment by card or cash. Everything is checked before anything moves: the card and PIN, or that
 * the buyer has enough cash and room for their change.
 */
public final class ShopPayments {
    private ShopPayments() {
    }

    /**
     * Whether the payment went through, the message if it didn't, the change handed back for cash, whether it
     * went on a credit card, and the sales tax included.
     */
    public record Charge(boolean success, Component message, long change, boolean onCredit, long tax) {
        public static Charge paid(long change, long tax) {
            return new Charge(true, Component.empty(), change, false, tax);
        }

        static Charge failed(Component message) {
            return new Charge(false, message, 0, false, 0);
        }

        static Charge failed(String key, Object... args) {
            return failed(Component.translatable(key, args));
        }
    }

    /**
     * Charges {@code buyer} {@code total} plus {@code tax}. The total is paid to {@code payee}'s account, or
     * leaves the economy when {@code payee} is null (the server shop); the tax always leaves the economy.
     *
     * <ul>
     *   <li>Card: the chosen card in the buyer's inventory, with its PIN. A debit card pays from the card's bank
     *       account and a credit card puts it on the card's bill (either may belong to someone who lent it).</li>
     *   <li>Cash: coins and bills from the buyer's inventory, with change back. The payee is credited the price.</li>
     * </ul>
     */
    public static Charge charge(ServerPlayer buyer, Payment payment, long total, long tax, @Nullable UUID payee, String reason,
                                Component description) {
        MinecraftServer server = buyer.server;
        EconomyService eco = EconomyService.INSTANCE;
        if (payment.method() == Payment.CARD) {
            return chargeCard(buyer, payment, total, tax, payee, reason, description);
        }
        if (payment.method() == Payment.CASH) {
            long due = total + tax;
            Optional<CashPlan.Plan> plan = CashWallet.planPayment(buyer, due);
            if (plan.isEmpty()) {
                long onHand = Purse.value(buyer);
                return onHand < due
                        ? Charge.failed("payment.reikoeconomy.not_enough_cash", eco.format(onHand))
                        : Charge.failed("payment.reikoeconomy.no_room_for_change");
            }
            if (payee != null) {
                // Credit the seller first: if another mod cancels the deposit, the buyer keeps their cash.
                TransactionResult result = eco.deposit(server, payee, total, reason + "_cash");
                if (!result.isSuccess()) {
                    return Charge.failed(Component.translatable(result.translationKey()));
                }
            }
            CashWallet.pay(buyer, plan.get());
            TaxService.collected(server, TaxKind.SALES, tax); // the tax was in the cash handed over
            return Charge.paid(plan.get().change(), tax);
        }
        return Charge.failed("payment.reikoeconomy.unknown_method");
    }

    /**
     * Charges the chosen card {@code total} plus {@code tax}: a debit card's bank account, or a credit card's bill.
     *
     * @param description what was bought, for the credit card's itemized bill
     */
    public static Charge chargeCard(ServerPlayer buyer, Payment payment, long total, long tax, @Nullable UUID payee,
                                    String reason, Component description) {
        MinecraftServer server = buyer.server;
        ItemStack card = Cards.find(buyer, payment.cardId());
        if (card.isEmpty()) {
            return Charge.failed("card.reikoeconomy.no_card");
        }
        if (Cards.isCredit(card)) {
            ShopActionResult result = CreditService.charge(server, card, payment.pin(), total, tax, payee, reason, description);
            return result.success() ? new Charge(true, Component.empty(), 0, true, tax) : Charge.failed(result.message());
        }
        CardService.Authorization auth = CardService.authorize(server, card, payment.pin());
        if (!auth.ok()) {
            return Charge.failed(auth.message());
        }
        EconomyService eco = EconomyService.INSTANCE;
        if (eco.getBalance(server, auth.account()) < total + tax) {
            return Charge.failed(Component.translatable(TransactionResult.INSUFFICIENT_FUNDS.translationKey()));
        }
        TransactionResult result = payee != null
                ? eco.transfer(server, auth.account(), payee, total, reason)
                : eco.withdraw(server, auth.account(), total, reason);
        if (!result.isSuccess()) {
            return Charge.failed(Component.translatable(result.translationKey()));
        }
        // The balance was checked above; only another mod cancelling the withdrawal could stop the tax.
        TaxService.take(server, auth.account(), TaxKind.SALES, tax);
        return Charge.paid(0, tax);
    }

    /** "Bought 4 Diamond for $21.00, including $1.00 tax." or, with no tax, "Bought 4 Diamond for $20.00." */
    public static Component boughtLine(int count, Component item, long total, long tax) {
        EconomyService eco = EconomyService.INSTANCE;
        return tax > 0
                ? Component.translatable("shop.reikoeconomy.bought_tax", count, item, eco.format(total + tax), eco.format(tax))
                : Component.translatable("shop.reikoeconomy.bought", count, item, eco.format(total));
    }

    /** The bought line plus how it was paid and any change. */
    public static Component receipt(Component bought, Payment payment, Charge charge) {
        EconomyService eco = EconomyService.INSTANCE;
        Component how = payment.method() == Payment.CARD
                ? Component.translatable(charge.onCredit() ? "payment.reikoeconomy.paid_credit" : "payment.reikoeconomy.paid_card")
                : charge.change() > 0
                        ? Component.translatable("payment.reikoeconomy.paid_cash_change", eco.format(charge.change()))
                        : Component.translatable("payment.reikoeconomy.paid_cash");
        return bought.copy().append(" ").append(how);
    }
}
