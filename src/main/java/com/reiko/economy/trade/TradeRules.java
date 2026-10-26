package com.reiko.economy.trade;

import net.minecraft.server.level.ServerPlayer;

/**
 * The server's trading settings.
 *
 * @param maxDistance  blocks two players can be apart and still trade
 * @param requestMillis how long a request waits for an answer
 * @param lockMillis   after an offer changes, how long before anyone can accept
 * @param sneakClick   sneak + right-click on a player with an empty hand asks them to trade
 */
public record TradeRules(boolean enabled, int maxDistance, long requestMillis, long lockMillis, boolean sneakClick) {
    /** Item slots on each side of a trade. */
    public static final int OFFER_SLOTS = 12;

    /** Whether the two players are in the same dimension and close enough to trade. */
    public boolean inRange(ServerPlayer a, ServerPlayer b) {
        return a.level() == b.level() && a.distanceToSqr(b) <= (double) maxDistance * maxDistance;
    }
}
