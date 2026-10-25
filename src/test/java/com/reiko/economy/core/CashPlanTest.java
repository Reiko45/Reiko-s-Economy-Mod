package com.reiko.economy.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CashPlanTest {
    private static Map<Denomination, Integer> wallet(Object... pairs) {
        Map<Denomination, Integer> wallet = new EnumMap<>(Denomination.class);
        for (int i = 0; i < pairs.length; i += 2) {
            wallet.put((Denomination) pairs[i], (Integer) pairs[i + 1]);
        }
        return wallet;
    }

    @Test
    void exactAmountNeedsNoChange() {
        CashPlan.Plan plan = CashPlan.plan(wallet(Denomination.TEN, 2, Denomination.FIVE, 1), 1500).orElseThrow();
        assertEquals(Map.of(Denomination.TEN, 1, Denomination.FIVE, 1), plan.handOver());
        assertEquals(0, plan.change());
    }

    @Test
    void breaksTheSmallestBillThatCoversTheRest() {
        CashPlan.Plan plan = CashPlan.plan(wallet(Denomination.TWENTY, 3), 500).orElseThrow();
        assertEquals(Map.of(Denomination.TWENTY, 1), plan.handOver());
        assertEquals(1500, plan.change());
    }

    @Test
    void usesCoinsBeforeBreakingABill() {
        CashPlan.Plan plan = CashPlan.plan(wallet(Denomination.QUARTER, 2, Denomination.TWENTY, 1), 30).orElseThrow();
        assertEquals(Map.of(Denomination.QUARTER, 2), plan.handOver());
        assertEquals(20, plan.change());
    }

    @Test
    void combinesBillsWhenNoSingleOneIsEnough() {
        CashPlan.Plan plan = CashPlan.plan(wallet(Denomination.TWENTY, 3), 5000).orElseThrow();
        assertEquals(Map.of(Denomination.TWENTY, 3), plan.handOver());
        assertEquals(1000, plan.change());
    }

    @Test
    void notEnoughCashIsEmpty() {
        assertTrue(CashPlan.plan(wallet(Denomination.ONE, 4), 500).isEmpty());
        assertTrue(CashPlan.plan(wallet(), 1).isEmpty());
    }

    @Test
    void randomWalletsAlwaysPayTheRightAmount() {
        Random random = new Random(42);
        Denomination[] all = Denomination.values();
        for (int round = 0; round < 20_000; round++) {
            Map<Denomination, Integer> wallet = new EnumMap<>(Denomination.class);
            long total = 0;
            for (Denomination d : all) {
                int count = random.nextInt(4);
                if (count > 0) {
                    wallet.put(d, count);
                    total += d.cents() * count;
                }
            }
            long amount = total == 0 ? 0 : (long) (random.nextDouble() * (total + 500));
            var plan = CashPlan.plan(wallet, amount);
            if (amount > total) {
                assertTrue(plan.isEmpty());
                continue;
            }
            long handed = 0;
            for (Map.Entry<Denomination, Integer> e : plan.orElseThrow().handOver().entrySet()) {
                assertTrue(e.getValue() <= wallet.getOrDefault(e.getKey(), 0), "can't hand over pieces you don't have");
                handed += e.getKey().cents() * e.getValue();
            }
            assertEquals(amount + plan.get().change(), handed, "handed over = price + change");
            assertTrue(plan.get().change() >= 0);
        }
    }
}
