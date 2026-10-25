package com.reiko.economy.api;

/** The kind of balance change a {@link TransactionEvent} describes. */
public enum TransactionType {
    /** Money added to one account from outside the economy (admin give, selling to the admin shop, ...). */
    DEPOSIT,
    /** Money removed from one account and out of the economy (admin take, buying from the admin shop, ...). */
    WITHDRAW,
    /** Money moved from one account to another. The total in the economy does not change. */
    TRANSFER,
    /** An account's balance overwritten with an exact value (admin set or reset). */
    SET
}
