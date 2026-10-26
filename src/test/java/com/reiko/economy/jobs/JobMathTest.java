package com.reiko.economy.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JobMathTest {
    @Test
    void eachLevelNeedsMoreXp() {
        assertEquals(100, JobMath.xpToNext(1));
        assertEquals(200, JobMath.xpToNext(2));
        assertEquals(4_900, JobMath.xpToNext(49));
    }

    @Test
    void xpCarriesOverAndCanSkipLevels() {
        assertEquals(new JobMath.Progress(1, 99), JobMath.addXp(new JobMath.Progress(1, 0), 99, 50));
        assertEquals(new JobMath.Progress(2, 20), JobMath.addXp(new JobMath.Progress(1, 0), 120, 50));
        // 100 to reach level 2, 200 more to reach 3, then 50 toward level 4
        assertEquals(new JobMath.Progress(3, 50), JobMath.addXp(new JobMath.Progress(1, 0), 350, 50));
    }

    @Test
    void xpStopsAtTheHighestLevel() {
        assertEquals(new JobMath.Progress(3, 0), JobMath.addXp(new JobMath.Progress(2, 150), 10_000, 3));
        assertEquals(new JobMath.Progress(3, 0), JobMath.addXp(new JobMath.Progress(3, 0), 500, 3));
    }

    @Test
    void eachLevelAddsToThePay() {
        assertEquals(0, JobMath.bonusBasisPoints(1, 200));
        assertEquals(1_800, JobMath.bonusBasisPoints(10, 200), "level 10 at 2% a level is +18%");
        assertEquals(500.0, JobMath.pay(500, 1, 200, 1.0), 1e-9);
        assertEquals(590.0, JobMath.pay(500, 10, 200, 1.0), 1e-9);
        assertEquals(1_180.0, JobMath.pay(500, 10, 200, 2.0), 1e-9, "the server multiplier applies on top");
    }
}
