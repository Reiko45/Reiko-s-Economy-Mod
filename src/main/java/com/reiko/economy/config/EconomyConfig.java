package com.reiko.economy.config;

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

    /** Largest single ATM withdrawal, in stored units. */
    public static long atmMaxWithdrawal() {
        return Money.fromDecimal(SPEC.isLoaded() ? ATM_MAX_WITHDRAWAL.get() : ATM_MAX_WITHDRAWAL.getDefault());
    }

    public static int logMaxEntries() {
        return SPEC.isLoaded() ? LOG_MAX_ENTRIES.get() : LOG_MAX_ENTRIES.getDefault();
    }
}
