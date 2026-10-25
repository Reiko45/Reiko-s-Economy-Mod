package com.reiko.economy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void parsesWholeAndDecimalAmounts() {
        assertEquals(OptionalLong.of(1200), Money.parse("12"));
        assertEquals(OptionalLong.of(1250), Money.parse("12.5"));
        assertEquals(OptionalLong.of(1250), Money.parse("12.50"));
        assertEquals(OptionalLong.of(5), Money.parse("0.05"));
        assertEquals(OptionalLong.of(1250), Money.parse("12.500"), "trailing zeros past 2 places are fine");
        assertEquals(OptionalLong.of(0), Money.parse("0"));
    }

    @Test
    void rejectsBadInput() {
        for (String bad : new String[] {null, "", " ", "-5", "+5", "1e3", "12.345", "abc", "12.", ".5", "1,000", "NaN"}) {
            assertTrue(Money.parse(bad).isEmpty(), "should reject: " + bad);
        }
    }

    @Test
    void rejectsAmountsOverTheMaximum() {
        assertTrue(Money.parse("1000000000000").isPresent());
        assertTrue(Money.parse("1000000000000.01").isEmpty());
        assertTrue(Money.parse("99999999999999999999999").isEmpty());
    }

    @Test
    void parsePositiveRejectsZero() {
        assertTrue(Money.parsePositive("0").isEmpty());
        assertTrue(Money.parsePositive("0.00").isEmpty());
        assertEquals(OptionalLong.of(1), Money.parsePositive("0.01"));
    }

    @Test
    void formatsWithSymbolAndGrouping() {
        assertEquals("$1,250.00", Money.format(125000, "$", false));
        assertEquals("1,250.00 Coins", Money.format(125000, "Coins", true));
        assertEquals("$0.05", Money.format(5, "$", false));
        assertEquals("-$3.00", Money.format(-300, "$", false));
        assertEquals("7.00", Money.format(700, "", false));
    }

    @Test
    void convertsConfigDecimals() {
        assertEquals(10000, Money.fromDecimal(100.0));
        assertEquals(1999, Money.fromDecimal(19.99));
        assertEquals(1, Money.fromDecimal(0.005), "rounds half up");
        assertEquals(0, Money.fromDecimal(0));
        assertEquals(0, Money.fromDecimal(-5));
    }
}
