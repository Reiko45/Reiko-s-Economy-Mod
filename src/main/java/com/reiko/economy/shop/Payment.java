package com.reiko.economy.shop;

/**
 * How a buyer wants to pay: by debit card (with its PIN) or in cash from their inventory. A bank balance can
 * only be spent at a shop through a card.
 */
public record Payment(int method, String pin) {
    public static final int CARD = 0;
    public static final int CASH = 1;

    public static Payment card(String pin) {
        return new Payment(CARD, pin);
    }

    public static Payment cash() {
        return new Payment(CASH, "");
    }
}
