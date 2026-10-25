package com.reiko.economy.network;

/**
 * The local player's balance as last sent by the server. Only meaningful on the client. Kept free of
 * client-only imports so it can be referenced from common networking code.
 */
public final class ClientBalance {
    private static long balance;
    private static boolean known;

    private ClientBalance() {
    }

    public static void set(long value) {
        balance = value;
        known = true;
    }

    public static void clear() {
        balance = 0;
        known = false;
    }

    public static boolean isKnown() {
        return known;
    }

    public static long get() {
        return balance;
    }
}
