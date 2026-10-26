package com.reiko.economy.core;

/**
 * The tax and fee rules, in stored units and basis points (hundredths of a percent: 5% is 500). Pure math, so
 * it can be tested without a server. All money taken as tax leaves the economy.
 *
 * @param salesBasisPoints        sales tax added on top of shop purchases
 * @param payFeeBasisPoints       fee the sender pays on top of a {@code /pay} transfer
 * @param wealthBasisPoints       wealth tax on the part of a bank balance above {@code wealthThreshold}
 * @param wealthThreshold         balances up to this amount pay no wealth tax
 * @param wealthPeriodTicks       game ticks between wealth tax collections
 * @param atmFlatFee              flat fee for each debit card ATM withdrawal
 * @param atmFeeBasisPoints       extra ATM withdrawal fee, in percent of the amount
 */
public record TaxRates(long salesBasisPoints, long payFeeBasisPoints, long wealthBasisPoints, long wealthThreshold,
                       long wealthPeriodTicks, long atmFlatFee, long atmFeeBasisPoints) {
    /** No taxes or fees at all. */
    public static final TaxRates NONE = new TaxRates(0, 0, 0, 0, 7 * 24_000L, 0, 0);

    /** Sales tax on a purchase of {@code amount}, rounded to the nearest cent. */
    public long salesTax(long amount) {
        return percentRounded(amount, salesBasisPoints);
    }

    /** Fee on a {@code /pay} transfer of {@code amount}, rounded to the nearest cent. */
    public long payFee(long amount) {
        return percentRounded(amount, payFeeBasisPoints);
    }

    /** Wealth tax on a bank balance: a percentage of the part above the threshold. */
    public long wealthTax(long balance) {
        return balance <= wealthThreshold ? 0 : percentRounded(balance - wealthThreshold, wealthBasisPoints);
    }

    /** Fee for withdrawing {@code amount} at an ATM with a debit card. */
    public long atmFee(long amount) {
        return atmFlatFee + percentRounded(amount, atmFeeBasisPoints);
    }

    public boolean hasWealthTax() {
        return wealthBasisPoints > 0;
    }

    /** {@code amount} times a percentage in basis points, rounded half up. Amounts are at most 10^14, so no overflow. */
    static long percentRounded(long amount, long basisPoints) {
        return amount <= 0 || basisPoints <= 0 ? 0 : (amount * basisPoints + 5_000) / 10_000;
    }

    /** A rate for display: 500 basis points is "5", 250 is "2.5", 125 is "1.25". */
    public static String percentText(long basisPoints) {
        return java.math.BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString();
    }
}
