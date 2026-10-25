package com.reiko.economy.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * The pieces of physical cash, US style: coins below one dollar, bills from one dollar up.
 * Values are in stored units (cents). Declared from smallest to largest.
 *
 * <p>No Minecraft imports, so change-making can be unit tested without a game. The matching items live in
 * {@code ModItems}.
 */
public enum Denomination {
    PENNY("coin_1", 1, false),
    NICKEL("coin_5", 5, false),
    DIME("coin_10", 10, false),
    QUARTER("coin_25", 25, false),
    HALF_DOLLAR("coin_50", 50, false),
    ONE("bill_1", 100, true),
    TWO("bill_2", 200, true),
    FIVE("bill_5", 500, true),
    TEN("bill_10", 1000, true),
    TWENTY("bill_20", 2000, true),
    FIFTY("bill_50", 5000, true),
    HUNDRED("bill_100", 10000, true);

    private final String id;
    private final long cents;
    private final boolean bill;

    Denomination(String id, long cents, boolean bill) {
        this.id = id;
        this.cents = cents;
        this.bill = bill;
    }

    /** Registry path of the item, e.g. {@code bill_20}. */
    public String id() {
        return id;
    }

    /** Value of one piece in stored units (cents). */
    public long cents() {
        return cents;
    }

    public boolean isBill() {
        return bill;
    }

    /**
     * Splits an amount into the fewest pieces, largest first, like an ATM paying out: 37.41 becomes a $20,
     * a $10, a $5, a $2, a quarter, a dime, a nickel and a penny. Greedy is optimal for this set of values.
     *
     * @return how many of each piece, only listing pieces that are used, largest first
     */
    public static Map<Denomination, Long> breakDown(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        Map<Denomination, Long> pieces = new EnumMap<>(Denomination.class);
        long remaining = amount;
        Denomination[] all = values();
        for (int i = all.length - 1; i >= 0 && remaining > 0; i--) {
            long count = remaining / all[i].cents;
            if (count > 0) {
                pieces.put(all[i], count);
                remaining -= count * all[i].cents;
            }
        }
        return pieces;
    }
}
