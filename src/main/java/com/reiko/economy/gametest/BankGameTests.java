package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.AtmService;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.LoanLedger;
import com.reiko.economy.core.LoanService;
import com.reiko.economy.core.SavingsLedger;
import com.reiko.economy.core.SavingsService;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.core.TaxService;
import com.reiko.economy.network.AtmActionPayload;
import com.reiko.economy.shop.ShopActionResult;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for savings and loans. They assume the default loan settings: a $1,000 starting limit, 10%
 * interest, 4 payments, a $25 late fee and a $500 limit increase.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class BankGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;

    private BankGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void moneyMovesInAndOutOfSavingsAndEarnsInterest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        UUID id = player.getUUID();
        try {
            ECO.setBalance(server, id, 100_000, "gametest.setup");
            check(helper, SavingsService.deposit(server, id, "600").success(), "move $600 to savings");
            helper.assertValueEqual(ECO.getBalance(server, id), 40_000L, "$400 left in the balance");
            helper.assertValueEqual(SavingsService.balance(server, id), 60_000L, "$600 in savings");

            check(helper, SavingsService.withdraw(server, id, "100").success(), "move $100 back");
            check(helper, !SavingsService.withdraw(server, id, "600").success(), "only $500 is in savings");
            check(helper, SavingsService.deposit(server, id, "").success(), "blank moves the whole balance");
            helper.assertValueEqual(ECO.getBalance(server, id), 0L, "nothing left in the balance");
            helper.assertValueEqual(SavingsService.balance(server, id), 100_000L, "all $1,000 in savings");

            // At the ATM, through the Savings tab
            ItemStack card = giveCard(helper, player, "1234");
            ShopActionResult atm = AtmService.bank(player, card, "1234", AtmActionPayload.FROM_SAVINGS, "250");
            check(helper, atm.success(), "take $250 out at the ATM: " + atm.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, id), 25_000L, "it's in the balance");

            // Interest on schedule: nothing before it's due, 0.5% when it is.
            SavingsService.runInterest(server, CreditService.now(server));
            long due = SavingsLedger.get(server).nextInterest();
            SavingsService.runInterest(server, due - 1);
            helper.assertValueEqual(SavingsService.balance(server, id), 75_000L, "not due yet");
            SavingsService.runInterest(server, due);
            helper.assertValueEqual(SavingsService.balance(server, id), 75_000L + 375, "0.5% of $750 is $3.75");
            helper.assertValueEqual(ECO.getBalance(server, id), 25_000L, "the balance doesn't earn interest");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aLoanIsPaidBackOnScheduleAndRaisesTheLimit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        UUID id = player.getUUID();
        try {
            ECO.setBalance(server, id, 0, "gametest.setup");
            check(helper, !LoanService.borrow(server, id, "1500").success(), "over the $1,000 starting limit");
            ShopActionResult borrowed = LoanService.borrow(server, id, "1000");
            check(helper, borrowed.success(), "borrow $1,000: " + borrowed.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, id), 100_000L, "the $1,000 is in the balance");
            LoanLedger.Borrower loan = LoanLedger.get(server).find(id).orElseThrow();
            helper.assertValueEqual(loan.owed(), 110_000L, "owes $1,100 with interest");
            helper.assertValueEqual(loan.payment(), 27_500L, "in payments of $275");
            check(helper, !LoanService.borrow(server, id, "10").success(), "one loan at a time");

            check(helper, LoanService.repay(server, id, "100").success(), "pay $100 early");
            LoanService.dueNow(server, id);
            helper.assertValueEqual(ECO.getBalance(server, id), 100_000L - 10_000 - 17_500, "only the other $175 was taken");
            helper.assertValueEqual(loan.owed(), 82_500L, "$825 left");

            check(helper, !LoanService.repay(server, id, "").success(), "$825 is more than the $725 balance");
            ECO.setBalance(server, id, 100_000, "gametest.setup");
            check(helper, LoanService.repay(server, id, "").success(), "pay off the rest");
            check(helper, !loan.hasLoan(), "the loan is closed");
            helper.assertValueEqual(ECO.getBalance(server, id), 100_000L - 82_500, "$825 was taken");
            helper.assertValueEqual(loan.limit(), 150_000L, "paying on time raised the limit to $1,500");
            check(helper, LoanService.borrow(server, id, "1500").success(), "and $1,500 can be borrowed now");
        } finally {
            LoanService.forgive(server, id);
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aMissedPaymentUsesSavingsThenAddsALateFee(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        UUID id = player.getUUID();
        try {
            check(helper, LoanService.borrow(server, id, "1000").success(), "borrow $1,000");
            ECO.setBalance(server, id, 10_000, "gametest.setup");
            check(helper, SavingsService.deposit(server, id, "50").success(), "$50 in the balance, $50 in savings");

            LoanService.dueNow(server, id);
            LoanLedger.Borrower loan = LoanLedger.get(server).find(id).orElseThrow();
            helper.assertValueEqual(ECO.getBalance(server, id), 0L, "the balance was used");
            helper.assertValueEqual(SavingsService.balance(server, id), 0L, "then savings");
            helper.assertValueEqual(loan.owed(), 110_000L - 10_000 + 2_500, "$100 paid, $25 late fee added");
            check(helper, loan.missedAPayment(), "the miss is remembered");

            ECO.setBalance(server, id, 200_000, "gametest.setup");
            check(helper, LoanService.repay(server, id, "").success(), "pay it all off");
            helper.assertValueEqual(loan.limit(), 100_000L, "a missed payment means no limit increase");
        } finally {
            LoanService.forgive(server, id);
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theWealthTaxCountsSavings(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer saver = joinMockPlayer(helper);
        UUID id = saver.getUUID();
        TaxRates rates = new TaxRates(0, 0, 100, 1_000_000, 7 * 24_000L, 0, 0); // 1% over $10,000
        EconomyConfig.overrideTaxes(rates);
        try {
            ECO.setBalance(server, id, 2_005_000, "gametest.setup");
            check(helper, SavingsService.deposit(server, id, "20000").success(), "$50 in the balance, $20,000 in savings");
            TaxService.collectWealthTax(server, rates);
            helper.assertValueEqual(ECO.getBalance(server, id), 0L, "the $100.50 tax took the balance first");
            helper.assertValueEqual(SavingsService.balance(server, id), 2_000_000L - 5_050, "then the rest from savings");

            helper.assertValueEqual(server.getCommands().getDispatcher().execute("bank", saver.createCommandSourceStack()), 1,
                    "/bank runs");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            leave(server, saver);
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
