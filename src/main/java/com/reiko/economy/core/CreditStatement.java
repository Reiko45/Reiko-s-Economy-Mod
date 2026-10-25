package com.reiko.economy.core;

/**
 * What happens to a credit card account when a statement period ends. Pure arithmetic, so it can be tested
 * without a game running; {@code CreditService} applies the result and tells the player.
 *
 * <p>Each statement says what was owed at the time and the minimum payment. By the next statement:
 * <ul>
 *   <li>Paid in full: no interest, and the limit grows.</li>
 *   <li>Paid at least the minimum: interest on what's left of the statement, and the limit grows.</li>
 *   <li>Not paid, but the bank account covered the minimum automatically: interest on the rest, no fee, and the
 *       limit stays the same.</li>
 *   <li>Missed: interest, a late fee, a lower limit, and after enough misses in a row the card freezes until
 *       it is paid off.</li>
 * </ul>
 * Purchases made after a statement are not charged interest until they appear on a statement and go unpaid.
 */
public final class CreditStatement {
    private CreditStatement() {
    }

    /**
     * A credit account's money state.
     *
     * @param balance            owed right now; below zero after overpaying
     * @param statementBalance   owed on the last statement
     * @param minimumDue         minimum payment for the last statement
     * @param paidSinceStatement paid by the player since the last statement
     * @param missed             missed payments in a row
     */
    public record State(long balance, long limit, long statementBalance, long minimumDue, long paidSinceStatement,
                        int missed, boolean frozen) {
        /** Credit left to spend. */
        public long available() {
            return Math.max(0, limit - balance);
        }

        /** What the player still has to pay to meet the minimum for the last statement. */
        public long minimumLeft() {
            return statementBalance > 0 ? Math.max(0, minimumDue - paidSinceStatement) : 0;
        }
    }

    public enum Outcome { NOTHING_DUE, PAID_IN_FULL, PAID_MINIMUM, AUTOPAID, MISSED }

    /** The new state and what changed, for the messages. */
    public record Result(State state, Outcome outcome, long autopaid, long interest, long lateFee, long limitChange,
                         boolean frozeNow, boolean unfrozeNow) {
    }

    /**
     * Ends the statement period.
     *
     * @param autopaid what was just taken from the owner's bank account toward the minimum (0 if it couldn't be)
     */
    public static Result close(State state, CreditTerms terms, long autopaid) {
        long balance = state.balance() - autopaid;
        long paid = state.paidSinceStatement() + autopaid;
        long limit = state.limit();
        int missed = state.missed();
        boolean frozen = state.frozen();
        long interest = 0;
        long lateFee = 0;
        Outcome outcome;

        if (state.statementBalance() <= 0) {
            outcome = Outcome.NOTHING_DUE;
        } else {
            long unpaid = Math.min(Math.max(0, state.statementBalance() - paid), Math.max(0, balance));
            interest = terms.interestOn(unpaid);
            if (paid >= state.statementBalance()) {
                outcome = Outcome.PAID_IN_FULL;
            } else if (state.paidSinceStatement() >= state.minimumDue()) {
                outcome = Outcome.PAID_MINIMUM;
            } else if (paid >= state.minimumDue()) {
                outcome = Outcome.AUTOPAID;
            } else {
                outcome = Outcome.MISSED;
            }
            switch (outcome) {
                case PAID_IN_FULL, PAID_MINIMUM -> {
                    missed = 0;
                    if (limit < terms.maxLimit()) {
                        limit = Math.min(terms.maxLimit(), limit + terms.limitIncrease());
                    }
                }
                case AUTOPAID -> missed = 0;
                case MISSED -> {
                    lateFee = terms.lateFee();
                    missed++;
                    if (limit > terms.lowestLimit()) {
                        limit = Math.max(terms.lowestLimit(), limit - terms.limitDecrease());
                    }
                    frozen = frozen || missed >= terms.freezeAfter();
                }
                default -> {
                }
            }
            balance += interest + lateFee;
        }

        boolean unfrozeNow = false;
        if (frozen && balance <= 0) {
            frozen = false;
            missed = 0;
            unfrozeNow = true;
        }
        long statementBalance = Math.max(0, balance);
        State next = new State(balance, limit, statementBalance, terms.minimumPaymentFor(statementBalance), 0, missed, frozen);
        return new Result(next, outcome, autopaid, interest, lateFee, limit - state.limit(),
                frozen && !state.frozen(), unfrozeNow);
    }
}
