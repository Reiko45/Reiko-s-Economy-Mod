package com.reiko.economy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.reiko.economy.core.CreditStatement.Outcome;
import com.reiko.economy.core.CreditStatement.Result;
import com.reiko.economy.core.CreditStatement.State;
import org.junit.jupiter.api.Test;

class CreditStatementTest {
    /** The default settings: $500 start, $5,000 cap, +/-$100, 5% interest, 10% or $10 minimum, $25 late fee, freeze after 3. */
    private static final CreditTerms TERMS = new CreditTerms(50_000, 500_000, 10_000, 10_000, 168_000,
            500, 1_000, 1_000, 2_500, 3, 500, 500);

    /** A $500-limit account whose last statement was {@code statement} (with the usual minimum) and owes {@code balance} now. */
    private static State owing(long balance, long statement, long paid) {
        return new State(balance, 50_000, statement, TERMS.minimumPaymentFor(statement), paid, 0, false);
    }

    @Test
    void minimumIsTenPercentWithATenDollarFloor() {
        assertEquals(0, TERMS.minimumPaymentFor(0));
        assertEquals(500, TERMS.minimumPaymentFor(500), "a $5 statement is due in full");
        assertEquals(1_000, TERMS.minimumPaymentFor(8_000), "10% of $80 is under the $10 floor");
        assertEquals(3_201, TERMS.minimumPaymentFor(32_005), "10% of $320.05, rounded up to the cent");
    }

    @Test
    void payingInFullMeansNoInterestAndAHigherLimit() {
        Result result = CreditStatement.close(owing(0, 20_000, 20_000), TERMS, 0);
        assertEquals(Outcome.PAID_IN_FULL, result.outcome());
        assertEquals(0, result.interest());
        assertEquals(60_000, result.state().limit());
        assertEquals(0, result.state().statementBalance());
        assertEquals(0, result.state().minimumDue());
    }

    @Test
    void newPurchasesAfterTheStatementAreNotChargedInterest() {
        // The $200 statement was paid in full; $50 of new purchases since then go on the next statement.
        Result result = CreditStatement.close(owing(5_000, 20_000, 20_000), TERMS, 0);
        assertEquals(0, result.interest());
        assertEquals(5_000, result.state().balance());
        assertEquals(5_000, result.state().statementBalance());
        assertEquals(1_000, result.state().minimumDue());
    }

    @Test
    void payingTheMinimumChargesInterestOnTheRest() {
        // $200 statement, $20 paid: interest is 5% of $180 = $9.
        Result result = CreditStatement.close(owing(18_000, 20_000, 2_000), TERMS, 0);
        assertEquals(Outcome.PAID_MINIMUM, result.outcome());
        assertEquals(900, result.interest());
        assertEquals(0, result.lateFee());
        assertEquals(18_900, result.state().balance());
        assertEquals(60_000, result.state().limit(), "the minimum on time still counts as on time");
    }

    @Test
    void theBankCoveringTheMinimumAvoidsTheFeeButDoesNotRaiseTheLimit() {
        Result result = CreditStatement.close(owing(20_000, 20_000, 0), TERMS, 2_000);
        assertEquals(Outcome.AUTOPAID, result.outcome());
        assertEquals(900, result.interest());
        assertEquals(0, result.lateFee());
        assertEquals(18_900, result.state().balance());
        assertEquals(50_000, result.state().limit());
        assertEquals(0, result.state().missed());
    }

    @Test
    void missingAPaymentAddsAFeeAndLowersTheLimit() {
        Result result = CreditStatement.close(owing(20_000, 20_000, 0), TERMS, 0);
        assertEquals(Outcome.MISSED, result.outcome());
        assertEquals(1_000, result.interest());
        assertEquals(2_500, result.lateFee());
        assertEquals(23_500, result.state().balance());
        assertEquals(40_000, result.state().limit());
        assertEquals(1, result.state().missed());
        assertFalse(result.state().frozen());
    }

    @Test
    void threeMissesInARowFreezeTheCardUntilItIsPaidOff() {
        State state = owing(20_000, 20_000, 0);
        for (int i = 0; i < 3; i++) {
            state = CreditStatement.close(state, TERMS, 0).state();
        }
        assertTrue(state.frozen());
        assertEquals(3, state.missed());
        assertEquals(TERMS.lowestLimit(), state.limit(), "the limit stops at half the starting limit");

        // Paying the minimum doesn't unfreeze it...
        State paidMinimum = new State(state.balance() - state.minimumDue(), state.limit(), state.statementBalance(),
                state.minimumDue(), state.minimumDue(), state.missed(), true);
        assertTrue(CreditStatement.close(paidMinimum, TERMS, 0).state().frozen());
        // ...paying it all does.
        State paidOff = new State(0, state.limit(), state.statementBalance(), state.minimumDue(), state.statementBalance(), state.missed(), true);
        Result result = CreditStatement.close(paidOff, TERMS, 0);
        assertFalse(result.state().frozen());
        assertTrue(result.unfrozeNow());
        assertEquals(0, result.state().missed());
    }

    @Test
    void anUnusedCardChangesNothing() {
        Result result = CreditStatement.close(owing(0, 0, 0), TERMS, 0);
        assertEquals(Outcome.NOTHING_DUE, result.outcome());
        assertEquals(0, result.interest());
        assertEquals(50_000, result.state().limit(), "no history, no limit increase");
    }

    @Test
    void theLimitStopsAtTheCap() {
        State nearCap = new State(0, 495_000, 10_000, 1_000, 10_000, 0, false);
        assertEquals(500_000, CreditStatement.close(nearCap, TERMS, 0).state().limit());
    }

    @Test
    void cashAdvanceFeeIsAPercentageWithAFloor() {
        assertEquals(500, TERMS.cashAdvanceFee(2_000), "5% of $20 is under the $5 floor");
        assertEquals(1_000, TERMS.cashAdvanceFee(20_000), "5% of $200");
    }
}
