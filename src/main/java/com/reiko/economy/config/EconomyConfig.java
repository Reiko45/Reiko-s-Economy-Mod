package com.reiko.economy.config;

import com.reiko.economy.core.CreditTerms;
import com.reiko.economy.core.Money;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server settings, stored in {@code config/reikoeconomy-server.toml}. A copy placed in a world's
 * {@code serverconfig/} folder overrides it for that world only.
 *
 * <p>Read values through the helper methods below rather than the raw {@code ConfigValue}s: the helpers
 * fall back to the defaults when no world is loaded, instead of throwing.
 */
public final class EconomyConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
    public static final ModConfigSpec.BooleanValue SYMBOL_AFTER_AMOUNT;
    public static final ModConfigSpec.DoubleValue STARTING_BALANCE;
    public static final ModConfigSpec.IntValue BALTOP_PAGE_SIZE;
    public static final ModConfigSpec.IntValue LOG_MAX_ENTRIES;
    public static final ModConfigSpec.DoubleValue ATM_MAX_WITHDRAWAL;
    public static final ModConfigSpec.DoubleValue CARD_REPLACEMENT_FEE;
    public static final ModConfigSpec.BooleanValue VILLAGERS_USE_MONEY;
    public static final ModConfigSpec.DoubleValue EMERALD_VALUE;
    public static final ModConfigSpec.BooleanValue CREDIT_ENABLED;
    public static final ModConfigSpec.DoubleValue CREDIT_STARTING_LIMIT;
    public static final ModConfigSpec.DoubleValue CREDIT_MAX_LIMIT;
    public static final ModConfigSpec.DoubleValue CREDIT_LIMIT_INCREASE;
    public static final ModConfigSpec.DoubleValue CREDIT_LIMIT_DECREASE;
    public static final ModConfigSpec.IntValue CREDIT_STATEMENT_DAYS;
    public static final ModConfigSpec.DoubleValue CREDIT_INTEREST_RATE;
    public static final ModConfigSpec.DoubleValue CREDIT_MINIMUM_PERCENT;
    public static final ModConfigSpec.DoubleValue CREDIT_MINIMUM_PAYMENT;
    public static final ModConfigSpec.DoubleValue CREDIT_LATE_FEE;
    public static final ModConfigSpec.IntValue CREDIT_FREEZE_AFTER;
    public static final ModConfigSpec.DoubleValue CASH_ADVANCE_FEE_PERCENT;
    public static final ModConfigSpec.DoubleValue CASH_ADVANCE_MIN_FEE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("How money is shown to players").push("currency");
        CURRENCY_SYMBOL = builder
                .comment("Symbol shown with every amount, for example $, ¢ or Coins")
                .define("symbol", "$");
        SYMBOL_AFTER_AMOUNT = builder
                .comment("Show the symbol after the amount (100.00 Coins) instead of before it ($100.00)")
                .define("symbolAfterAmount", false);
        builder.pop();

        builder.comment("Bank accounts").push("accounts");
        STARTING_BALANCE = builder
                .comment("Money every new bank account starts with. Amounts use 2 decimal places.")
                .defineInRange("startingBalance", 100.0, 0.0, 1_000_000_000.0);
        builder.pop();

        builder.comment("Commands").push("commands");
        BALTOP_PAGE_SIZE = builder
                .comment("Players listed per page of /baltop")
                .defineInRange("baltopPageSize", 10, 1, 50);
        builder.pop();

        builder.comment("Transaction log").push("log");
        LOG_MAX_ENTRIES = builder
                .comment("How many recent balance changes to keep for /eco history and /transactions. Oldest are dropped first.")
                .defineInRange("maxEntries", 5000, 100, 100_000);
        builder.pop();

        builder.comment("ATMs").push("atm");
        ATM_MAX_WITHDRAWAL = builder
                .comment("Most cash one ATM withdrawal can pay out. 10000 is 100 $100 bills, under two inventory slots.")
                .defineInRange("maxWithdrawal", 10_000.0, 1.0, 1_000_000_000.0);
        CARD_REPLACEMENT_FEE = builder
                .comment("Cost of a debit card after a player's first, which is free. Taken from their balance.")
                .defineInRange("cardReplacementFee", 5.0, 0.0, 1_000_000.0);
        builder.pop();

        builder.comment("Villagers and wandering traders").push("villagers");
        VILLAGERS_USE_MONEY = builder
                .comment("Villagers take card or cash instead of emeralds, and pay cash for what they buy.",
                        "Set to false to keep vanilla emerald trading.")
                .define("useMoney", true);
        EMERALD_VALUE = builder
                .comment("What one emerald in a vanilla trade is worth. A trade that cost 3 emeralds costs 3 times this,",
                        "and a villager that gave 1 emerald pays this much in cash.")
                .defineInRange("emeraldValue", 10.0, 0.01, 1_000_000.0);
        builder.pop();

        builder.comment("Credit cards, issued at ATMs. Days are Minecraft days (20 real minutes each) of time the world is running.")
                .push("credit");
        CREDIT_ENABLED = builder
                .comment("Players can get and use credit cards")
                .define("enabled", true);
        CREDIT_STARTING_LIMIT = builder
                .comment("Credit limit of a new credit card")
                .defineInRange("startingLimit", 500.0, 1.0, 1_000_000_000.0);
        CREDIT_MAX_LIMIT = builder
                .comment("Highest a credit limit can grow to")
                .defineInRange("maxLimit", 5_000.0, 1.0, 1_000_000_000.0);
        CREDIT_LIMIT_INCREASE = builder
                .comment("Limit increase for each statement paid on time (at least the minimum, by the due date)")
                .defineInRange("limitIncrease", 100.0, 0.0, 1_000_000_000.0);
        CREDIT_LIMIT_DECREASE = builder
                .comment("Limit decrease for each missed payment. A limit never drops below half the starting limit.")
                .defineInRange("limitDecrease", 100.0, 0.0, 1_000_000_000.0);
        CREDIT_STATEMENT_DAYS = builder
                .comment("Days between statements. A statement's payment is due when the next one comes.")
                .defineInRange("statementDays", 7, 1, 365);
        CREDIT_INTEREST_RATE = builder
                .comment("Interest, in percent, on the part of a statement not paid by its due date")
                .defineInRange("interestRate", 5.0, 0.0, 100.0);
        CREDIT_MINIMUM_PERCENT = builder
                .comment("Minimum payment, in percent of the statement balance")
                .defineInRange("minimumPaymentPercent", 10.0, 0.0, 100.0);
        CREDIT_MINIMUM_PAYMENT = builder
                .comment("Smallest minimum payment, unless the whole statement is less")
                .defineInRange("minimumPayment", 10.0, 0.0, 1_000_000.0);
        CREDIT_LATE_FEE = builder
                .comment("Fee added when the minimum payment is missed and the bank account can't cover it")
                .defineInRange("lateFee", 25.0, 0.0, 1_000_000.0);
        CREDIT_FREEZE_AFTER = builder
                .comment("Missed payments in a row before the card stops working until it is paid off")
                .defineInRange("freezeAfterMissedPayments", 3, 1, 100);
        CASH_ADVANCE_FEE_PERCENT = builder
                .comment("Fee for taking cash out of an ATM with a credit card, in percent of the amount")
                .defineInRange("cashAdvanceFeePercent", 5.0, 0.0, 100.0);
        CASH_ADVANCE_MIN_FEE = builder
                .comment("Smallest cash advance fee")
                .defineInRange("cashAdvanceMinimumFee", 5.0, 0.0, 1_000_000.0);
        builder.pop();

        SPEC = builder.build();
    }

    private EconomyConfig() {
    }

    public static String symbol() {
        return SPEC.isLoaded() ? CURRENCY_SYMBOL.get() : CURRENCY_SYMBOL.getDefault();
    }

    public static boolean symbolAfterAmount() {
        return SPEC.isLoaded() ? SYMBOL_AFTER_AMOUNT.get() : SYMBOL_AFTER_AMOUNT.getDefault();
    }

    /** Starting balance in stored units (hundredths). */
    public static long startingBalance() {
        double value = SPEC.isLoaded() ? STARTING_BALANCE.get() : STARTING_BALANCE.getDefault();
        return Money.fromDecimal(value);
    }

    public static int baltopPageSize() {
        return SPEC.isLoaded() ? BALTOP_PAGE_SIZE.get() : BALTOP_PAGE_SIZE.getDefault();
    }

    /** Fee for every debit card after the first, in stored units. */
    public static long cardReplacementFee() {
        return Money.fromDecimal(SPEC.isLoaded() ? CARD_REPLACEMENT_FEE.get() : CARD_REPLACEMENT_FEE.getDefault());
    }

    /** Largest single ATM withdrawal, in stored units. */
    public static long atmMaxWithdrawal() {
        return Money.fromDecimal(SPEC.isLoaded() ? ATM_MAX_WITHDRAWAL.get() : ATM_MAX_WITHDRAWAL.getDefault());
    }

    public static boolean villagersUseMoney() {
        return SPEC.isLoaded() ? VILLAGERS_USE_MONEY.get() : VILLAGERS_USE_MONEY.getDefault();
    }

    /** Worth of one vanilla emerald in villager trades, in stored units. Always at least 1. */
    public static long emeraldValue() {
        return Math.max(1, Money.fromDecimal(SPEC.isLoaded() ? EMERALD_VALUE.get() : EMERALD_VALUE.getDefault()));
    }

    public static boolean creditEnabled() {
        return SPEC.isLoaded() ? CREDIT_ENABLED.get() : CREDIT_ENABLED.getDefault();
    }

    /** All the credit card rules, in stored units, read once so they can't change halfway through a statement. */
    public static CreditTerms creditTerms() {
        long starting = money(CREDIT_STARTING_LIMIT);
        return new CreditTerms(
                starting,
                Math.max(starting, money(CREDIT_MAX_LIMIT)),
                money(CREDIT_LIMIT_INCREASE),
                money(CREDIT_LIMIT_DECREASE),
                24_000L * (SPEC.isLoaded() ? CREDIT_STATEMENT_DAYS.get() : CREDIT_STATEMENT_DAYS.getDefault()),
                basisPoints(CREDIT_INTEREST_RATE),
                basisPoints(CREDIT_MINIMUM_PERCENT),
                money(CREDIT_MINIMUM_PAYMENT),
                money(CREDIT_LATE_FEE),
                SPEC.isLoaded() ? CREDIT_FREEZE_AFTER.get() : CREDIT_FREEZE_AFTER.getDefault(),
                basisPoints(CASH_ADVANCE_FEE_PERCENT),
                money(CASH_ADVANCE_MIN_FEE));
    }

    private static long money(ModConfigSpec.DoubleValue value) {
        return Money.fromDecimal(SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    /** A percentage in hundredths of a percent: 5.0% is 500. */
    private static long basisPoints(ModConfigSpec.DoubleValue value) {
        return Math.round((SPEC.isLoaded() ? value.get() : value.getDefault()) * 100);
    }

    public static int logMaxEntries() {
        return SPEC.isLoaded() ? LOG_MAX_ENTRIES.get() : LOG_MAX_ENTRIES.getDefault();
    }
}
