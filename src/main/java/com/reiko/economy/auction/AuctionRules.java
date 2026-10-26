package com.reiko.economy.auction;

/**
 * The auction house rules, in stored units, milliseconds and basis points (hundredths of a percent), with the
 * math for them. Pure, so it can be tested without a server.
 *
 * @param listingFee      fee to put something up
 * @param cutBasisPoints  the auction house's share of a sale
 * @param maxListings     listings one player can have up at once
 * @param listingMillis   how long a buy-now listing stays up
 * @param raiseBasisPoints how much each bid has to beat the last one by
 */
public record AuctionRules(boolean enabled, long listingFee, long cutBasisPoints, int maxListings, long listingMillis,
                           long raiseBasisPoints) {
    /** Auction lengths players can pick, in hours. */
    public static final int[] AUCTION_HOURS = {1, 6, 24, 48};
    /** A bid in the last minute pushes the end back to a minute away, so nobody can win by bidding at the last second. */
    public static final long SNIPE_GUARD_MILLIS = 60_000;

    /** The auction house's cut of a sale, rounded to the nearest cent. */
    public long cut(long price) {
        return (price * cutBasisPoints + 5_000) / 10_000;
    }

    /** The lowest bid allowed: the starting bid if there are none yet, else the last bid plus the minimum raise. */
    public long minimumBid(long startingBid, long currentBid, boolean hasBids) {
        if (!hasBids) {
            return startingBid;
        }
        long raise = Math.max(1, (currentBid * raiseBasisPoints + 9_999) / 10_000);
        return currentBid + raise;
    }

    public static boolean validHours(int hours) {
        for (int allowed : AUCTION_HOURS) {
            if (allowed == hours) {
                return true;
            }
        }
        return false;
    }
}
