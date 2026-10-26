package com.reiko.economy.bounty;

/**
 * The server's bounty settings, in stored units and milliseconds.
 *
 * @param minAmount      the smallest bounty
 * @param feeBasisPoints the posting fee, on top of the bounty; it leaves the economy like a tax
 * @param maxActive      bounties one player can have up at once
 * @param durationMillis how long a bounty lasts before it's refunded
 * @param announceClaims tell the whole server when a bounty is collected
 */
public record BountyRules(boolean enabled, long minAmount, long feeBasisPoints, int maxActive, long durationMillis,
                          boolean announceClaims) {
    public long fee(long amount) {
        return Math.floorDiv(amount * feeBasisPoints, 10_000L);
    }
}
