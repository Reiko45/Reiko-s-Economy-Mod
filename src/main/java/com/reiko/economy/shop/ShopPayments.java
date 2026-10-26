package com.reiko.economy.shop;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.CashWallet;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.CashPlan;
import com.reiko.economy.core.EconomyService;
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
     * Whether the payment went through, the message if it didn't, the change handed back for cash, and whether
     * it went on a credit card.
     */
    public record Charge(boolean success, Component message, long change, boolean onCredit) {
        public static Charge paid(long change) {
            return new Charge(true, Component.empty(), change, false);
        }

        static Charge failed(Component message) {
            return new Charge(false, message, 0, false);
        }

        static Charge failed(String key, Object... args) {
            return failed(Component.translatable(key, args));
        }
    }

    /**
     * Charges {@code buyer} {@code total}, paid to {@code payee}'s account, or out of the economy when
     * {@code payee} is null (the server shop).
     *
     * <ul>
     *   <li>Card: the chosen card in the buyer's inventory, with its PIN. A debit card pays from the card's bank
     *       account and a credit card puts it on the card's bill (either may belong to someone who lent it).</li>
     *   <li>Cash: coins and bills from the buyer's inventory, with change back. The payee is credited the price.</li>
     * </ul>
     */
    public static Charge charge(ServerPlayer buyer, Payment payment, long total, @Nullable UUID payee, String reason,
                                Component description) {
        MinecraftServer server = buyer.server;
        EconomyService eco = EconomyService.INSTANCE;
        if (payment.method() == Payment.CARD) {
            return chargeCard(buyer, payment, total, payee, reason, description);
        }
        if (payment.method() == Payment.CASH) {
            Optional<CashPlan.Plan> plan = CashWallet.planPayment(buyer, total);
            if (plan.isEmpty()) {
                long onHand = Purse.value(buyer);
                return onHand < total
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
            return Charge.paid(plan.get().change());
        }
        return Charge.failed("payment.reikoeconomy.unknown_method");
    }

    /**
     * Charges the chosen card: a debit card's bank account, or a credit card's bill.
     *
     * @param description what was bought, for the credit card's itemized bill
     */
    public static Charge chargeCard(ServerPlayer buyer, Payment payment, long total, @Nullable UUID payee, String reason,
                                    Component description) {
        MinecraftServer server = buyer.server;
        ItemStack card = Cards.find(buyer, payment.cardId());
        if (card.isEmpty()) {
            return Charge.failed("card.reikoeconomy.no_card");
        }
        if (Cards.isCredit(card)) {
            ShopActionResult result = CreditService.charge(server, card, payment.pin(), total, payee, reason, description);
            return result.success() ? new Charge(true, Component.empty(), 0, true) : Charge.failed(result.message());
        }
        CardService.Authorization auth = CardService.authorize(server, card, payment.pin());
        if (!auth.ok()) {
            return Charge.failed(auth.message());
        }
        EconomyService eco = EconomyService.INSTANCE;
        TransactionResult result = payee != null
                ? eco.transfer(server, auth.account(), payee, total, reason)
                : eco.withdraw(server, auth.account(), total, reason);
        return result.isSuccess() ? Charge.paid(0) : Charge.failed(Component.translatable(result.translationKey()));
    }

    /** "Bought 4 Diamond for $20.00." plus how it was paid and any change. */
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
