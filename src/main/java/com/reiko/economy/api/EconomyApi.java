package com.reiko.economy.api;

import com.reiko.economy.core.EconomyService;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Public API for other mods. Get it with {@code EconomyApi.get()}.
 *
 * <p>Rules for callers:
 * <ul>
 *   <li>Call only on the server thread.</li>
 *   <li>Amounts are {@code long}s in hundredths: {@code 1250} means 12.50. Use {@link #parse(String)} and
 *       {@link #format(long)} to convert to and from text.</li>
 *   <li>Every change fires {@link TransactionEvent.Pre} (cancelable) and {@link TransactionEvent.Post}.</li>
 *   <li>The {@code reason} is a short machine-readable tag such as {@code "mymod.lottery_win"}.</li>
 * </ul>
 */
public interface EconomyApi {
    static EconomyApi get() {
        return EconomyService.INSTANCE;
    }

    /** True once the player has a bank account, which happens the first time they join. */
    boolean hasAccount(MinecraftServer server, UUID player);

    /** The player's balance, or 0 if they have no account. */
    long getBalance(MinecraftServer server, UUID player);

    /** Adds money to one account from outside the economy. */
    TransactionResult deposit(MinecraftServer server, UUID player, long amount, String reason);

    /** Removes money from one account. Fails with {@link TransactionResult#INSUFFICIENT_FUNDS} rather than going negative. */
    TransactionResult withdraw(MinecraftServer server, UUID player, long amount, String reason);

    /** Moves money between two accounts. Both must exist. */
    TransactionResult transfer(MinecraftServer server, UUID from, UUID to, long amount, String reason);

    /** Overwrites a balance with an exact, non-negative value. */
    TransactionResult setBalance(MinecraftServer server, UUID player, long amount, String reason);

    /** Formats an amount with the world's currency symbol, e.g. {@code $1,250.00}. */
    String format(long amount);

    /** Parses text like {@code 12.50} into stored units, or empty if it is not a valid amount. */
    OptionalLong parse(String text);
}
