package com.reiko.economy.core;

/**
 * The credit card rules from the server settings, with money in stored units (hundredths) and percentages
 * in basis points (hundredths of a percent, so 5% is 500).
 *
 * @param statementTicks game ticks between statements
 * @param freezeAfter    missed payments in a row before the card stops working
 */
public record CreditTerms(long startingLimit, long maxLimit, long limitIncrease, long limitDecrease, long statementTicks,
                          long interestBasisPoints, long minimumBasisPoints, long minimumPayment, long lateFee,
                          int freezeAfter, long cashAdvanceBasisPoints, long cashAdvanceMinimumFee) {

    /** Missed payments never push a limit below this. */
    public long lowestLimit() {
        return startingLimit / 2;
    }

    /** The minimum payment on a statement: a percentage with a floor, but never more than the statement. */
    public long minimumPaymentFor(long statementBalance) {
        if (statementBalance <= 0) {
            return 0;
        }
        return Math.min(statementBalance, Math.max(minimumPayment, percentRoundedUp(statementBalance, minimumBasisPoints)));
    }

    /** Interest on the part of a statement that wasn't paid, rounded to the nearest cent. */
    public long interestOn(long unpaid) {
        return unpaid <= 0 ? 0 : percentRounded(unpaid, interestBasisPoints);
    }

    public long cashAdvanceFee(long amount) {
        return Math.max(cashAdvanceMinimumFee, percentRounded(amount, cashAdvanceBasisPoints));
    }

    static long percentRounded(long amount, long basisPoints) {
        return (amount * basisPoints + 5_000) / 10_000;
    }

    static long percentRoundedUp(long amount, long basisPoints) {
        return (amount * basisPoints + 9_999) / 10_000;
    }
}
