package com.reiko.economy.core;

/**
 * The loan rules, in stored units and basis points, and the math for them. Pure, so it can be tested without a
 * server.
 *
 * <p>A loan works like a simple installment loan: borrowing {@code P} adds the interest up front, so the player
 * owes {@code P + interest}, split into {@code payments} equal payments, one due every {@code periodTicks}.
 * Paying early counts toward the next payments. When a payment comes due, whatever is still due is taken from
 * the bank; if the bank can't cover it, what's there is taken and a late fee is added.
 *
 * @param startingLimit  most a player can borrow at first
 * @param maxLimit       highest the limit can grow to
 * @param limitIncrease  limit increase for each loan paid off without a missed payment
 * @param feeBasisPoints interest added when the loan is taken out
 * @param payments       payments a loan is split into
 * @param periodTicks    game ticks between payments
 * @param lateFee        fee for a payment the bank couldn't cover
 */
public record LoanTerms(long startingLimit, long maxLimit, long limitIncrease, long feeBasisPoints, int payments,
                        long periodTicks, long lateFee) {

    /** Interest added when borrowing {@code principal}, rounded to the nearest cent. */
    public long interest(long principal) {
        return (principal * feeBasisPoints + 5_000) / 10_000;
    }

    /** Each payment for a loan that owes {@code owed}: owed split evenly, rounded up so the last one is smallest. */
    public long payment(long owed) {
        return (owed + payments - 1) / payments;
    }

    /** The new limit after a loan is paid off, if no payment was missed. */
    public long raisedLimit(long limit) {
        return Math.min(maxLimit, limit + limitIncrease);
    }

    /**
     * A loan between payments.
     *
     * @param owed    what's left to pay back
     * @param payment the regular payment
     * @param prepaid paid since the last due date, which counts toward the next payment
     * @param missed  whether any payment on this loan was missed
     */
    public record State(long owed, long payment, long prepaid, boolean missed) {
    }

    /** The part of the next payment still due after any early payments. */
    public static long stillDue(State state) {
        return Math.max(0, Math.min(state.payment(), state.owed()) - state.prepaid());
    }

    /**
     * Settles a due date: takes what's still due (up to {@code available}, what the bank has), and adds the late
     * fee if that wasn't enough. Early payments beyond this payment carry over to the next one.
     */
    public Due settle(State state, long available) {
        long thisPayment = Math.min(state.payment(), state.owed());
        long due = Math.max(0, thisPayment - state.prepaid());
        long carried = Math.max(0, state.prepaid() - thisPayment);
        long paid = Math.min(due, Math.max(0, available));
        boolean missed = paid < due;
        long fee = missed ? lateFee : 0;
        State next = new State(state.owed() - paid + fee, state.payment(), carried, state.missed() || missed);
        return new Due(next, paid, fee, missed);
    }

    /** What happened on a due date: the loan after it, what was taken, and the late fee if it was missed. */
    public record Due(State state, long paid, long lateFee, boolean missed) {
    }
}
