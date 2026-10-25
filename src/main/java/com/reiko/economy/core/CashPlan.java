package com.reiko.economy.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decides which coins and bills a player hands over to pay in cash, and how much change they get back.
 *
 * <p>It pays as exactly as it can with pieces worth no more than what's still owed, largest first, then covers
 * whatever is left with the smallest single piece that's enough. Paying 0.30 with two quarters and a $20 hands
 * over both quarters and gets 0.20 back, rather than breaking the $20. No Minecraft imports, so it's unit tested.
 */
public final class CashPlan {
    private CashPlan() {
    }

    /** The pieces to hand over and the change owed back, in stored units. */
    public record Plan(Map<Denomination, Integer> handOver, long change) {
    }

    /**
     * @param wallet how many of each piece the payer has
     * @return the plan, or empty if the wallet is worth less than {@code amount}
     */
    public static Optional<Plan> plan(Map<Denomination, Integer> wallet, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        long total = 0;
        for (Map.Entry<Denomination, Integer> entry : wallet.entrySet()) {
            total += entry.getKey().cents() * entry.getValue();
        }
        if (total < amount) {
            return Optional.empty();
        }

        Map<Denomination, Integer> left = new EnumMap<>(Denomination.class);
        left.putAll(wallet);
        Map<Denomination, Integer> handOver = new EnumMap<>(Denomination.class);
        long owed = amount;

        // 1. Pay with pieces worth no more than what's still owed, largest first.
        Denomination[] all = Denomination.values();
        for (int i = all.length - 1; i >= 0 && owed > 0; i--) {
            Denomination piece = all[i];
            int have = left.getOrDefault(piece, 0);
            int use = (int) Math.min(have, owed / piece.cents());
            if (use > 0) {
                handOver.merge(piece, use, Integer::sum);
                left.put(piece, have - use);
                owed -= use * piece.cents();
            }
        }

        // 2. Cover the rest with the smallest single piece that's enough. One always exists: every piece left
        // over is worth more than what was owed when step 1 reached it, and the wallet covers the amount.
        long change = 0;
        if (owed > 0) {
            for (Denomination piece : all) {
                if (left.getOrDefault(piece, 0) > 0 && piece.cents() >= owed) {
                    handOver.merge(piece, 1, Integer::sum);
                    change = piece.cents() - owed;
                    owed = 0;
                    break;
                }
            }
        }
        if (owed > 0) {
            throw new IllegalStateException("could not cover " + owed + " from " + wallet);
        }
        return Optional.of(new Plan(Collections.unmodifiableMap(handOver), change));
    }
}
