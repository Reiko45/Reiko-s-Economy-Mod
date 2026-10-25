package com.reiko.economy.config;

import com.reiko.economy.core.Money;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-world server settings, stored in {@code <world>/serverconfig/reikoeconomy-server.toml}.
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
}
