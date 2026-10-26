package com.reiko.economy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TaxRatesTest {
    /** 5% sales tax, 2% /pay fee, 1% wealth tax over $10,000 weekly, $1 + 0.5% ATM fee. */
    private static final TaxRates RATES = new TaxRates(500, 200, 100, 1_000_000, 7 * 24_000L, 100, 50);

    @Test
    void salesTaxIsRoundedToTheNearestCent() {
        assertEquals(50, RATES.salesTax(1_000), "5% of $10.00");
        assertEquals(1, RATES.salesTax(10), "5% of 10 cents is half a cent, rounded up");
        assertEquals(0, RATES.salesTax(9), "5% of 9 cents rounds down to nothing");
        assertEquals(0, RATES.salesTax(0));
    }

    @Test
    void wealthTaxOnlyCountsTheBalanceAboveTheThreshold() {
        assertEquals(0, RATES.wealthTax(500_000), "$5,000 is under the threshold");
        assertEquals(0, RATES.wealthTax(1_000_000), "exactly $10,000 pays nothing");
        assertEquals(10_000, RATES.wealthTax(2_000_000), "1% of the $10,000 above it");
        assertEquals(1_000_000_000_000L, RATES.wealthTax(Money.MAX_BALANCE + 1_000_000), "no overflow at the largest balance");
    }

    @Test
    void feesAddUp() {
        assertEquals(200, RATES.payFee(10_000), "2% of $100");
        assertEquals(100 + 10, RATES.atmFee(2_000), "$1 plus 0.5% of $20");
        assertEquals(0, TaxRates.NONE.atmFee(2_000));
        assertEquals(0, TaxRates.NONE.salesTax(1_000_000));
    }

    @Test
    void ratesReadNaturally() {
        assertEquals("5", TaxRates.percentText(500));
        assertEquals("2.5", TaxRates.percentText(250));
        assertEquals("1.25", TaxRates.percentText(125));
        assertEquals("0", TaxRates.percentText(0));
    }
}
