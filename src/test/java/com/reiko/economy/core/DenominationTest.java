package com.reiko.economy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DenominationTest {
    @Test
    void coinsAreUnderADollarAndBillsAreADollarOrMore() {
        for (Denomination d : Denomination.values()) {
            assertEquals(d.cents() >= 100, d.isBill(), d.name());
        }
    }

    @Test
    void breaksDownIntoTheFewestPieces() {
        Map<Denomination, Long> pieces = Denomination.breakDown(3741);
        assertEquals(Map.of(
                Denomination.TWENTY, 1L, Denomination.TEN, 1L, Denomination.FIVE, 1L, Denomination.TWO, 1L,
                Denomination.QUARTER, 1L, Denomination.DIME, 1L, Denomination.NICKEL, 1L, Denomination.PENNY, 1L), pieces);
    }

    @Test
    void usesHalfDollarsAndTwos() {
        // 2.99 = $2 + 50¢ + 25¢ + 10¢ + 10¢ + 4 × 1¢
        assertEquals(Map.of(Denomination.TWO, 1L, Denomination.HALF_DOLLAR, 1L, Denomination.QUARTER, 1L,
                Denomination.DIME, 2L, Denomination.PENNY, 4L), Denomination.breakDown(299));
    }

    @Test
    void largeAmountsAreHundreds() {
        assertEquals(Map.of(Denomination.HUNDRED, 250L), Denomination.breakDown(2_500_000));
    }

    @Test
    void piecesAlwaysAddUpToTheAmount() {
        for (long amount = 0; amount <= 50_000; amount += 7) {
            long total = 0;
            for (Map.Entry<Denomination, Long> entry : Denomination.breakDown(amount).entrySet()) {
                assertTrue(entry.getValue() > 0);
                total += entry.getKey().cents() * entry.getValue();
            }
            assertEquals(amount, total);
        }
    }

    @Test
    void zeroIsNoPiecesAndNegativeIsAnError() {
        assertFalse(Denomination.breakDown(0).entrySet().iterator().hasNext());
        assertThrows(IllegalArgumentException.class, () -> Denomination.breakDown(-1));
    }
}
