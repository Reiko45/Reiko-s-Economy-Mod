package com.reiko.economy.core;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopNotices;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Bank loans. A player borrows up to their limit into their bank balance and pays it back, plus interest, in
 * equal payments that are taken from the bank automatically (the bank balance first, then savings). A payment
 * the bank can't cover adds a late fee. One loan at a time; paying one off without missing a payment raises the
 * limit. See {@link LoanTerms} for the math.
 */
public final class LoanService {
    /** Most due dates handled at once after the server was off for a long time. */
    private static final int MAX_CATCH_UP = 100;

    private LoanService() {
    }

    /** What the player can borrow right now: their limit, or the starting limit if they've never borrowed. */
    public static long limit(MinecraftServer server, UUID owner) {
        return LoanLedger.get(server).find(owner).map(LoanLedger.Borrower::limit)
                .orElse(EconomyConfig.loanTerms().startingLimit());
    }

    /** Borrows {@code amountText} into the account's bank balance. */
    public static ShopActionResult borrow(MinecraftServer server, UUID account, String amountText) {
        if (!EconomyConfig.loansEnabled()) {
            return ShopActionResult.fail("loan.reikoeconomy.disabled");
        }
        OptionalLong parsed = Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail("loan.reikoeconomy.type_amount");
        }
        long amount = parsed.getAsLong();
        LoanTerms terms = EconomyConfig.loanTerms();
        LoanLedger ledger = LoanLedger.get(server);
        EconomyService eco = EconomyService.INSTANCE;
        LoanLedger.Borrower existing = ledger.find(account).orElse(null);
        if (existing != null && existing.hasLoan()) {
            return ShopActionResult.fail("loan.reikoeconomy.already", eco.format(existing.owed()));
        }
        long limit = existing == null ? terms.startingLimit() : existing.limit();
        if (amount > limit) {
            return ShopActionResult.fail("loan.reikoeconomy.over_limit", eco.format(limit));
        }
        long owed = amount + terms.interest(amount);
        TransactionResult result = eco.deposit(server, account, amount, "loan.borrow");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        LoanLedger.Borrower borrower = ledger.getOrCreate(account, terms.startingLimit());
        long payment = terms.payment(owed);
        ledger.startLoan(borrower, amount, owed, payment, CreditService.now(server) + terms.periodTicks());
        return ShopActionResult.ok(Component.translatable("loan.reikoeconomy.borrowed", eco.format(amount), eco.format(owed),
                terms.payments(), eco.format(payment), terms.periodTicks() / 24_000));
    }

    /** Pays {@code amountText} off the loan from the account's bank balance. Blank pays off all of it. */
    public static ShopActionResult repay(MinecraftServer server, UUID account, String amountText) {
        LoanLedger ledger = LoanLedger.get(server);
        EconomyService eco = EconomyService.INSTANCE;
        LoanLedger.Borrower borrower = ledger.find(account).filter(LoanLedger.Borrower::hasLoan).orElse(null);
        if (borrower == null) {
            return ShopActionResult.fail("loan.reikoeconomy.none");
        }
        OptionalLong parsed = amountText.isBlank() ? OptionalLong.of(borrower.owed()) : Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long amount = Math.min(parsed.getAsLong(), borrower.owed());
        TransactionResult result = eco.withdraw(server, account, amount, "loan.payment");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result == TransactionResult.INSUFFICIENT_FUNDS
                    ? Component.translatable("loan.reikoeconomy.cant_pay", eco.format(amount), eco.format(eco.getBalance(server, account)))
                    : Component.translatable(result.translationKey()));
        }
        ledger.pay(borrower, amount);
        if (!borrower.hasLoan()) {
            return ShopActionResult.ok(paidOff(ledger, borrower));
        }
        return ShopActionResult.ok(Component.translatable("loan.reikoeconomy.paid", eco.format(amount), eco.format(borrower.owed())));
    }

    /** Closes a loan that's been paid off and says so, with the new limit if it went up. */
    private static Component paidOff(LoanLedger ledger, LoanLedger.Borrower borrower) {
        LoanTerms terms = EconomyConfig.loanTerms();
        boolean raise = !borrower.missedAPayment();
        long before = borrower.limit();
        ledger.close(borrower, raise ? terms.raisedLimit(before) : before, true);
        EconomyService eco = EconomyService.INSTANCE;
        return borrower.limit() > before
                ? Component.translatable("loan.reikoeconomy.paid_off_raise", eco.format(borrower.limit()))
                : Component.translatable("loan.reikoeconomy.paid_off");
    }

    // ---- payments ----

    /** Takes every loan payment that's come due. */
    public static void runPayments(MinecraftServer server, long now) {
        LoanLedger ledger = LoanLedger.get(server);
        LoanTerms terms = null;
        for (LoanLedger.Borrower borrower : List.copyOf(ledger.borrowers())) {
            if (!borrower.hasLoan() || borrower.nextDue() > now) {
                continue;
            }
            if (terms == null) {
                terms = EconomyConfig.loanTerms();
            }
            int handled = 0;
            while (borrower.hasLoan() && borrower.nextDue() <= now && handled++ < MAX_CATCH_UP) {
                takePayment(server, ledger, borrower, terms);
            }
            if (borrower.hasLoan() && borrower.nextDue() <= now) {
                ledger.apply(borrower, borrower.state(), now + terms.periodTicks());
            }
        }
    }

    /** Settles one due date: takes what's due from the bank balance, then savings, and adds a late fee if short. */
    private static void takePayment(MinecraftServer server, LoanLedger ledger, LoanLedger.Borrower borrower, LoanTerms terms) {
        EconomyService eco = EconomyService.INSTANCE;
        UUID owner = borrower.owner();
        long due = LoanTerms.stillDue(borrower.state());
        long fromChecking = Math.min(due, eco.getBalance(server, owner));
        if (fromChecking > 0 && !eco.withdraw(server, owner, fromChecking, "loan.autopay").isSuccess()) {
            fromChecking = 0;
        }
        long fromSavings = SavingsService.take(server, owner, due - fromChecking);
        LoanTerms.Due result = terms.settle(borrower.state(), fromChecking + fromSavings);
        ledger.apply(borrower, result.state(), borrower.nextDue() + terms.periodTicks());

        Component message;
        if (!borrower.hasLoan()) {
            message = paidOff(ledger, borrower);
        } else if (result.missed()) {
            message = Component.translatable("loan.reikoeconomy.missed", eco.format(result.paid()), eco.format(due),
                    eco.format(result.lateFee()), eco.format(borrower.owed()));
        } else if (result.paid() > 0) {
            message = Component.translatable("loan.reikoeconomy.autopaid", eco.format(result.paid()), eco.format(borrower.owed()));
        } else {
            return; // paid ahead; nothing to say
        }
        ShopNotices.alert(server, owner, message);
    }

    // ---- admin ----

    /** Clears a player's loan without raising their limit. Returns false if they had none. */
    public static boolean forgive(MinecraftServer server, UUID owner) {
        LoanLedger ledger = LoanLedger.get(server);
        LoanLedger.Borrower borrower = ledger.find(owner).filter(LoanLedger.Borrower::hasLoan).orElse(null);
        if (borrower == null) {
            return false;
        }
        ledger.close(borrower, borrower.limit(), false);
        return true;
    }

    public static void setLimit(MinecraftServer server, UUID owner, long limit) {
        LoanLedger ledger = LoanLedger.get(server);
        ledger.setLimit(ledger.getOrCreate(owner, EconomyConfig.loanTerms().startingLimit()), limit);
    }

    /** Makes the next loan payment due now, for admins and tests. */
    public static void dueNow(MinecraftServer server, UUID owner) {
        LoanLedger ledger = LoanLedger.get(server);
        ledger.find(owner).filter(LoanLedger.Borrower::hasLoan).ifPresent(borrower -> {
            ledger.apply(borrower, borrower.state(), CreditService.now(server));
            runPayments(server, CreditService.now(server));
        });
    }
}
