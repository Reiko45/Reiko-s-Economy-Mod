package com.reiko.economy.api;

import java.util.Locale;

/** Outcome of an {@link EconomyApi} call. Nothing changes unless the result is {@link #SUCCESS}. */
public enum TransactionResult {
    SUCCESS,
    /** The player has never joined, so no bank account exists for them yet. */
    NO_ACCOUNT,
    /** Zero, negative, or otherwise unusable amount. */
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    /** A transfer from an account to itself. */
    SAME_ACCOUNT,
    /** The change would push a balance past {@code Money.MAX_BALANCE}. */
    BALANCE_LIMIT,
    /** A {@link TransactionEvent.Pre} listener cancelled the transaction. */
    CANCELLED;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** Translation key for a short, player-facing description of this result. */
    public String translationKey() {
        return "reikoeconomy.result." + name().toLowerCase(Locale.ROOT);
    }
}
