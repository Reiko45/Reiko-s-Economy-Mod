package com.reiko.economy.core;

/** The kinds of tax and fee. Each has the reason it's logged under in the transaction log. */
public enum TaxKind {
    SALES("sales", "tax.sales"),
    PAY("pay", "tax.pay"),
    WEALTH("wealth", "tax.wealth"),
    ATM("atm", "fee.atm"),
    AUCTION("auction", "fee.auction");

    private final String id;
    private final String reason;

    TaxKind(String id, String reason) {
        this.id = id;
        this.reason = reason;
    }

    /** Short id, used in saved data and lang keys. */
    public String id() {
        return id;
    }

    /** Reason recorded in the transaction log. */
    public String reason() {
        return reason;
    }
}
