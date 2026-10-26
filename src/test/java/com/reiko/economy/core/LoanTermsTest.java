package com.reiko.economy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoanTermsTest {
    /** $1,000 to start, up to $25,000, +$500 per loan, 10% interest, 4 payments a week apart, $25 late fee. */
    private static final LoanTerms TERMS = new LoanTerms(100_000, 2_500_000, 50_000, 1_000, 4, 7 * 24_000L, 2_500);

    @Test
    void interestIsAddedUpFrontAndSplitIntoEqualPayments() {
        assertEquals(10_000, TERMS.interest(100_000), "10% of $1,000");
        assertEquals(27_500, TERMS.payment(110_000), "$1,100 in 4 payments of $275");
        assertEquals(27_501, TERMS.payment(110_001), "rounded up, so the last payment is the smallest");
    }

    @Test
    void aDuePaymentIsTakenInFull() {
        LoanTerms.Due due = TERMS.settle(new LoanTerms.State(110_000, 27_500, 0, false), 1_000_000);
        assertEquals(27_500, due.paid());
        assertEquals(82_500, due.state().owed());
        assertFalse(due.missed());
    }

    @Test
    void earlyPaymentsCountTowardTheNextOne() {
        // $100 paid early (already off what's owed) leaves $175 due.
        LoanTerms.Due due = TERMS.settle(new LoanTerms.State(100_000, 27_500, 10_000, false), 1_000_000);
        assertEquals(17_500, due.paid());
        assertEquals(82_500, due.state().owed());

        // $400 paid early covers this payment and $125 of the next.
        LoanTerms.Due ahead = TERMS.settle(new LoanTerms.State(70_000, 27_500, 40_000, false), 1_000_000);
        assertEquals(0, ahead.paid());
        assertEquals(12_500, ahead.state().prepaid());
        assertEquals(70_000, ahead.state().owed());
    }

    @Test
    void aShortPaymentTakesWhatThereIsAndAddsALateFee() {
        LoanTerms.Due due = TERMS.settle(new LoanTerms.State(110_000, 27_500, 0, false), 10_000);
        assertEquals(10_000, due.paid());
        assertEquals(2_500, due.lateFee());
        assertEquals(110_000 - 10_000 + 2_500, due.state().owed());
        assertTrue(due.missed());
        assertTrue(due.state().missed(), "remembered for the limit");
    }

    @Test
    void theLastPaymentIsWhatsLeft() {
        LoanTerms.Due due = TERMS.settle(new LoanTerms.State(5_000, 27_500, 0, false), 1_000_000);
        assertEquals(5_000, due.paid());
        assertEquals(0, due.state().owed());
    }

    @Test
    void theLimitGrowsUpToTheMaximum() {
        assertEquals(150_000, TERMS.raisedLimit(100_000));
        assertEquals(2_500_000, TERMS.raisedLimit(2_480_000));
    }
}
