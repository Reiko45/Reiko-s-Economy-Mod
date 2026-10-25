package com.reiko.economy.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Money math helpers. All amounts are stored as whole numbers of hundredths in a {@code long}
 * ({@code 1250} means 12.50), so balances never pick up floating-point rounding errors.
 *
 * <p>This class deliberately has no Minecraft imports so it can be unit tested without a game.
 */
public final class Money {
    /** Decimal places stored for every amount. */
    public static final int SCALE = 2;
    /** Stored units in one whole unit of currency. */
    public static final long UNITS_PER_WHOLE = 100L;
    /** Largest balance an account may hold: 1 trillion. Keeps sums far away from overflowing a long. */
    public static final long MAX_BALANCE = 1_000_000_000_000L * UNITS_PER_WHOLE;

    // Digits, optionally followed by a dot and more digits. No signs, exponents, commas or spaces.
    private static final Pattern AMOUNT = Pattern.compile("\\d+(\\.\\d+)?");

    private Money() {
    }

    /**
     * Parses player input such as {@code 12}, {@code 12.5} or {@code 12.50}.
     *
     * @return the amount in stored units, or empty if the text is not a plain non-negative number with at
     *         most {@value #SCALE} decimal places, or is larger than {@link #MAX_BALANCE}
     */
    public static OptionalLong parse(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String text = input.trim();
        if (text.isEmpty() || text.length() > 32 || !AMOUNT.matcher(text).matches()) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.stripTrailingZeros().scale() > SCALE) {
                return OptionalLong.empty();
            }
            long units = value.movePointRight(SCALE).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return units <= MAX_BALANCE ? OptionalLong.of(units) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Like {@link #parse(String)}, but also rejects zero. */
    public static OptionalLong parsePositive(String input) {
        OptionalLong value = parse(input);
        return value.isPresent() && value.getAsLong() > 0 ? value : OptionalLong.empty();
    }

    /** Converts a decimal amount from a config file (e.g. {@code 100.0}) to stored units, rounding half up. */
    public static long fromDecimal(double value) {
        if (Double.isNaN(value) || value <= 0) {
            return 0L;
        }
        BigDecimal units = BigDecimal.valueOf(value).movePointRight(SCALE).setScale(0, RoundingMode.HALF_UP);
        return units.compareTo(BigDecimal.valueOf(MAX_BALANCE)) > 0 ? MAX_BALANCE : units.longValueExact();
    }

    /** Plain text a player can type back in, e.g. {@code 125050} gives {@code 1250.50}. No symbol or commas. */
    public static String toPlainString(long units) {
        return BigDecimal.valueOf(units, SCALE).toPlainString();
    }

    /**
     * Formats stored units for display, e.g. {@code format(125000, "$", false)} gives {@code $1,250.00}
     * and {@code format(125000, "Coins", true)} gives {@code 1,250.00 Coins}.
     */
    public static String format(long units, String symbol, boolean symbolAfter) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        String number = format.format(BigDecimal.valueOf(units, SCALE).abs());
        String sign = units < 0 ? "-" : "";
        if (symbol == null || symbol.isBlank()) {
            return sign + number;
        }
        return symbolAfter ? sign + number + " " + symbol : sign + symbol + number;
    }
}
