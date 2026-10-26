package com.reiko.economy.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuctionRulesTest {
    /** $1 fee, 5% cut, 10 listings, 7 days, bids up by 5%. */
    private static final AuctionRules RULES = new AuctionRules(true, 100, 500, 10, 7 * 86_400_000L, 500);

    @Test
    void theCutIsRoundedToTheNearestCent() {
        assertEquals(500, RULES.cut(10_000), "5% of $100");
        assertEquals(75, RULES.cut(1_500), "5% of $15");
        assertEquals(1, RULES.cut(10), "5% of 10 cents is half a cent, rounded up");
    }

    @Test
    void bidsStartAtTheStartingBidThenGoUpByFivePercent() {
        assertEquals(1_000, RULES.minimumBid(1_000, 0, false), "the starting bid");
        assertEquals(1_050, RULES.minimumBid(1_000, 1_000, true), "$10 then at least $10.50");
        assertEquals(1_103, RULES.minimumBid(1_000, 1_050, true), "5% of $10.50 is 52.5 cents, rounded up");
        assertEquals(2, RULES.minimumBid(1, 1, true), "always at least a cent more");
    }

    @Test
    void onlyTheOfferedAuctionLengthsAreAllowed() {
        assertTrue(AuctionRules.validHours(24));
        assertFalse(AuctionRules.validHours(5));
        assertFalse(AuctionRules.validHours(0));
    }
}
