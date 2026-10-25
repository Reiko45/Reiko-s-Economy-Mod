package com.reiko.economy.shop;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.CashWallet;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.CashPlan;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.item.DebitCardItem;
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

    /** Whether the payment went through, the message if it didn't, and the change handed back for cash. */
    public record Charge(boolean success, Component message, long change) {
        static Charge failed(Component message) {
            return new Charge(false, message, 0);
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
     *   <li>Card: the card in the buyer's hand or inventory, with its PIN, pays from the card's account (which
     *       may belong to someone who lent it).</li>
     *   <li>Cash: coins and bills from the buyer's inventory, with change back. The payee is credited the price.</li>
     * </ul>
     */
    public static Charge charge(ServerPlayer buyer, Payment payment, long total, @Nullable UUID payee, String reason) {
        MinecraftServer server = buyer.server;
        EconomyService eco = EconomyService.INSTANCE;
        if (payment.method() == Payment.CARD) {
            ItemStack card = DebitCardItem.findCard(buyer);
            if (card.isEmpty()) {
                return Charge.failed("card.reikoeconomy.no_card");
            }
            CardService.Authorization auth = CardService.authorize(server, card, payment.pin());
            if (!auth.ok()) {
                return Charge.failed(auth.message());
            }
            TransactionResult result = payee != null
                    ? eco.transfer(server, auth.account(), payee, total, reason)
                    : eco.withdraw(server, auth.account(), total, reason);
            return result.isSuccess() ? new Charge(true, Component.empty(), 0)
                    : Charge.failed(Component.translatable(result.translationKey()));
        }
        if (payment.method() == Payment.CASH) {
            Optional<CashPlan.Plan> plan = CashWallet.planPayment(buyer, total);
            if (plan.isEmpty()) {
                long onHand = CashWallet.value(buyer.getInventory().items);
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
            return new Charge(true, Component.empty(), plan.get().change());
        }
        return Charge.failed("payment.reikoeconomy.unknown_method");
    }

    /** "Bought 4 Diamond for $20.00." plus how it was paid and any change. */
    public static Component receipt(Component bought, Payment payment, Charge charge) {
        EconomyService eco = EconomyService.INSTANCE;
        Component how = payment.method() == Payment.CARD
                ? Component.translatable("payment.reikoeconomy.paid_card")
                : charge.change() > 0
                        ? Component.translatable("payment.reikoeconomy.paid_cash_change", eco.format(charge.change()))
                        : Component.translatable("payment.reikoeconomy.paid_cash");
        return bought.copy().append(" ").append(how);
    }
}
