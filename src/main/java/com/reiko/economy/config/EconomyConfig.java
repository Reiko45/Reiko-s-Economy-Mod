package com.reiko.economy.config;

import com.reiko.economy.auction.AuctionRules;
import com.reiko.economy.trade.TradeRules;
import com.reiko.economy.core.CreditTerms;
import com.reiko.economy.core.LoanTerms;
import com.reiko.economy.core.Money;
import com.reiko.economy.core.TaxRates;
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
    public static final ModConfigSpec.BooleanValue JOBS_ENABLED;
    public static final ModConfigSpec.IntValue JOBS_MAX_JOBS;
    public static final ModConfigSpec.IntValue JOBS_MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue JOBS_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue JOBS_PAY_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue JOBS_PAY_SPAWNER_MOBS;
    public static final ModConfigSpec.IntValue JOBS_CONTRACTS_PER_DAY;
    public static final ModConfigSpec.BooleanValue TAXES_ENABLED;
    public static final ModConfigSpec.DoubleValue SALES_TAX_PERCENT;
    public static final ModConfigSpec.DoubleValue PAY_FEE_PERCENT;
    public static final ModConfigSpec.DoubleValue WEALTH_TAX_PERCENT;
    public static final ModConfigSpec.DoubleValue WEALTH_TAX_THRESHOLD;
    public static final ModConfigSpec.IntValue WEALTH_TAX_DAYS;
    public static final ModConfigSpec.DoubleValue ATM_FEE;
    public static final ModConfigSpec.DoubleValue ATM_FEE_PERCENT;
    public static final ModConfigSpec.BooleanValue SAVINGS_ENABLED;
    public static final ModConfigSpec.DoubleValue SAVINGS_INTEREST_PERCENT;
    public static final ModConfigSpec.IntValue SAVINGS_INTEREST_DAYS;
    public static final ModConfigSpec.BooleanValue LOANS_ENABLED;
    public static final ModConfigSpec.DoubleValue LOAN_STARTING_LIMIT;
    public static final ModConfigSpec.DoubleValue LOAN_MAX_LIMIT;
    public static final ModConfigSpec.DoubleValue LOAN_LIMIT_INCREASE;
    public static final ModConfigSpec.DoubleValue LOAN_FEE_PERCENT;
    public static final ModConfigSpec.IntValue LOAN_PAYMENTS;
    public static final ModConfigSpec.IntValue LOAN_PAYMENT_DAYS;
    public static final ModConfigSpec.DoubleValue LOAN_LATE_FEE;
    public static final ModConfigSpec.BooleanValue AUCTION_ENABLED;
    public static final ModConfigSpec.DoubleValue AUCTION_LISTING_FEE;
    public static final ModConfigSpec.DoubleValue AUCTION_CUT_PERCENT;
    public static final ModConfigSpec.IntValue AUCTION_MAX_LISTINGS;
    public static final ModConfigSpec.IntValue AUCTION_LISTING_DAYS;
    public static final ModConfigSpec.DoubleValue AUCTION_MIN_RAISE_PERCENT;
    public static final ModConfigSpec.BooleanValue TRADE_ENABLED;
    public static final ModConfigSpec.IntValue TRADE_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue TRADE_REQUEST_SECONDS;
    public static final ModConfigSpec.IntValue TRADE_ACCEPT_DELAY_SECONDS;
    public static final ModConfigSpec.BooleanValue TRADE_SNEAK_CLICK;

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

        builder.comment("Jobs. What each job pays is set in datapack files: data/<namespace>/jobs/<name>.json").push("jobs");
        JOBS_ENABLED = builder
                .comment("Players can join jobs and get paid for work")
                .define("enabled", true);
        JOBS_MAX_JOBS = builder
                .comment("Jobs a player can have at once")
                .defineInRange("maxJobs", 3, 1, 50);
        JOBS_MAX_LEVEL = builder
                .comment("Highest job level")
                .defineInRange("maxLevel", 50, 1, 1000);
        JOBS_BONUS_PER_LEVEL = builder
                .comment("Extra pay for each job level above 1, in percent")
                .defineInRange("payBonusPerLevel", 2.0, 0.0, 100.0);
        JOBS_PAY_MULTIPLIER = builder
                .comment("Multiplies all job pay, to fit your server's prices. 1.0 is the pay in the job files.")
                .defineInRange("payMultiplier", 1.0, 0.0, 1000.0);
        JOBS_PAY_SPAWNER_MOBS = builder
                .comment("Pay for mobs that came from a monster spawner (mob farms)")
                .define("paySpawnerMobs", false);
        JOBS_CONTRACTS_PER_DAY = builder
                .comment("Contracts each player gets per real day (new ones at midnight on the server's clock), picked from",
                        "their jobs. 0 turns contracts off. What contracts there are is set in the job files.")
                .defineInRange("contractsPerDay", 3, 0, 10);
        builder.pop();

        builder.comment("Taxes and fees. Money taken as tax disappears from the economy, which keeps prices from creeping up",
                "as players earn more and more. Set any rate to 0 to turn that tax off.").push("taxes");
        TAXES_ENABLED = builder
                .comment("Turns all taxes and fees on or off at once")
                .define("enabled", true);
        SALES_TAX_PERCENT = builder
                .comment("Sales tax, in percent, added on top of the price at player shops and server shops.",
                        "The shop owner still gets the full price.")
                .defineInRange("salesTaxPercent", 5.0, 0.0, 100.0);
        PAY_FEE_PERCENT = builder
                .comment("Fee, in percent, that the sender pays on top of a /pay transfer")
                .defineInRange("payFeePercent", 2.0, 0.0, 100.0);
        WEALTH_TAX_PERCENT = builder
                .comment("Wealth tax, in percent, on the part of each bank balance above wealthTaxThreshold.",
                        "Taken every wealthTaxDays, from online and offline players alike.")
                .defineInRange("wealthTaxPercent", 1.0, 0.0, 100.0);
        WEALTH_TAX_THRESHOLD = builder
                .comment("Bank balances up to this amount pay no wealth tax")
                .defineInRange("wealthTaxThreshold", 10_000.0, 0.0, 1_000_000_000_000.0);
        WEALTH_TAX_DAYS = builder
                .comment("Minecraft days (20 real minutes each, while the world runs) between wealth tax collections")
                .defineInRange("wealthTaxDays", 7, 1, 365);
        ATM_FEE = builder
                .comment("Flat fee for each ATM withdrawal with a debit card. Credit card cash advances have their own fee.")
                .defineInRange("atmWithdrawalFee", 1.0, 0.0, 1_000_000.0);
        ATM_FEE_PERCENT = builder
                .comment("Extra ATM withdrawal fee, in percent of the amount, added to the flat fee")
                .defineInRange("atmWithdrawalFeePercent", 0.0, 0.0, 100.0);
        builder.pop();

        builder.comment("Savings accounts. Players move money between their bank balance and savings at an ATM; only savings",
                "earn interest. Days are Minecraft days (20 real minutes each) of time the world is running.").push("savings");
        SAVINGS_ENABLED = builder
                .translation("reikoeconomy.configuration.savings.enabled")
                .comment("Players can keep money in savings and earn interest")
                .define("enabled", true);
        SAVINGS_INTEREST_PERCENT = builder
                .translation("reikoeconomy.configuration.savings.interestPercent")
                .comment("Interest, in percent of the savings balance, paid every interestDays")
                .defineInRange("interestPercent", 0.5, 0.0, 100.0);
        SAVINGS_INTEREST_DAYS = builder
                .translation("reikoeconomy.configuration.savings.interestDays")
                .comment("Days between interest payments")
                .defineInRange("interestDays", 7, 1, 365);
        builder.pop();

        builder.comment("Bank loans, taken at an ATM. A loan is paid back in equal payments taken from the bank automatically.",
                "Players can have one loan at a time.").push("loans");
        LOANS_ENABLED = builder
                .translation("reikoeconomy.configuration.loans.enabled")
                .comment("Players can take loans")
                .define("enabled", true);
        LOAN_STARTING_LIMIT = builder
                .translation("reikoeconomy.configuration.loans.startingLimit")
                .comment("Most a player can borrow at first")
                .defineInRange("startingLimit", 1_000.0, 1.0, 1_000_000_000.0);
        LOAN_MAX_LIMIT = builder
                .translation("reikoeconomy.configuration.loans.maxLimit")
                .comment("Highest the borrowing limit can grow to")
                .defineInRange("maxLimit", 25_000.0, 1.0, 1_000_000_000.0);
        LOAN_LIMIT_INCREASE = builder
                .translation("reikoeconomy.configuration.loans.limitIncrease")
                .comment("Limit increase each time a loan is paid off without missing a payment")
                .defineInRange("limitIncrease", 500.0, 0.0, 1_000_000_000.0);
        LOAN_FEE_PERCENT = builder
                .translation("reikoeconomy.configuration.loans.interestPercent")
                .comment("Interest, in percent of the amount borrowed, added when the loan is taken out")
                .defineInRange("interestPercent", 10.0, 0.0, 100.0);
        LOAN_PAYMENTS = builder
                .translation("reikoeconomy.configuration.loans.payments")
                .comment("Number of payments a loan is split into")
                .defineInRange("payments", 4, 1, 100);
        LOAN_PAYMENT_DAYS = builder
                .translation("reikoeconomy.configuration.loans.paymentDays")
                .comment("Days between loan payments")
                .defineInRange("paymentDays", 7, 1, 365);
        LOAN_LATE_FEE = builder
                .translation("reikoeconomy.configuration.loans.lateFee")
                .comment("Fee added to the loan when a payment can't be taken from the bank in full")
                .defineInRange("lateFee", 25.0, 0.0, 1_000_000.0);
        builder.pop();

        builder.comment("The auction house (/ah, or an Auction House block). Fees and the sale cut leave the economy, like taxes.")
                .push("auction");
        AUCTION_ENABLED = builder
                .translation("reikoeconomy.configuration.auction.enabled")
                .comment("Players can use the auction house")
                .define("enabled", true);
        AUCTION_LISTING_FEE = builder
                .translation("reikoeconomy.configuration.auction.listingFee")
                .comment("Fee for putting something up, taken from the seller's bank. Not refunded.")
                .defineInRange("listingFee", 1.0, 0.0, 1_000_000.0);
        AUCTION_CUT_PERCENT = builder
                .translation("reikoeconomy.configuration.auction.saleCutPercent")
                .comment("Percent of the sale price the auction house keeps when something sells")
                .defineInRange("saleCutPercent", 5.0, 0.0, 100.0);
        AUCTION_MAX_LISTINGS = builder
                .translation("reikoeconomy.configuration.auction.maxListings")
                .comment("Listings one player can have up at once")
                .defineInRange("maxListings", 10, 1, 1000);
        AUCTION_LISTING_DAYS = builder
                .translation("reikoeconomy.configuration.auction.listingDays")
                .comment("Real days a buy-now listing stays up before the item goes back to the seller")
                .defineInRange("listingDays", 7, 1, 365);
        AUCTION_MIN_RAISE_PERCENT = builder
                .translation("reikoeconomy.configuration.auction.minRaisePercent")
                .comment("Each new bid has to beat the last one by at least this percent (and at least 1 cent)")
                .defineInRange("minRaisePercent", 5.0, 0.0, 100.0);
        builder.pop();

        builder.comment("Trading between players: each puts in items and money, and nothing moves until both accept.",
                        "Money sent in a trade pays the same fee as /pay (taxes.payFeePercent).")
                .push("trading");
        TRADE_ENABLED = builder
                .translation("reikoeconomy.configuration.trading.enabled")
                .comment("Players can trade with /trade or by sneaking and right-clicking another player")
                .define("enabled", true);
        TRADE_MAX_DISTANCE = builder
                .translation("reikoeconomy.configuration.trading.maxDistance")
                .comment("How close, in blocks, two players must be to start a trade and stay in it")
                .defineInRange("maxDistance", 16, 1, 256);
        TRADE_REQUEST_SECONDS = builder
                .translation("reikoeconomy.configuration.trading.requestSeconds")
                .comment("Seconds a trade request waits for an answer")
                .defineInRange("requestSeconds", 60, 10, 600);
        TRADE_ACCEPT_DELAY_SECONDS = builder
                .translation("reikoeconomy.configuration.trading.acceptDelaySeconds")
                .comment("After either offer changes, seconds before anyone can accept. Stops last-second swaps.")
                .defineInRange("acceptDelaySeconds", 3, 0, 30);
        TRADE_SNEAK_CLICK = builder
                .translation("reikoeconomy.configuration.trading.sneakClick")
                .comment("Sneaking and right-clicking a player with an empty hand asks them to trade")
                .define("sneakClick", true);
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

    public static boolean jobsEnabled() {
        return SPEC.isLoaded() ? JOBS_ENABLED.get() : JOBS_ENABLED.getDefault();
    }

    public static int maxJobs() {
        return SPEC.isLoaded() ? JOBS_MAX_JOBS.get() : JOBS_MAX_JOBS.getDefault();
    }

    public static int maxJobLevel() {
        return SPEC.isLoaded() ? JOBS_MAX_LEVEL.get() : JOBS_MAX_LEVEL.getDefault();
    }

    /** Pay bonus per job level, in basis points. */
    public static long jobBonusPerLevel() {
        return basisPoints(JOBS_BONUS_PER_LEVEL);
    }

    public static double jobPayMultiplier() {
        return SPEC.isLoaded() ? JOBS_PAY_MULTIPLIER.get() : JOBS_PAY_MULTIPLIER.getDefault();
    }

    public static int contractsPerDay() {
        return SPEC.isLoaded() ? JOBS_CONTRACTS_PER_DAY.get() : JOBS_CONTRACTS_PER_DAY.getDefault();
    }

    public static boolean paySpawnerMobs() {
        return SPEC.isLoaded() ? JOBS_PAY_SPAWNER_MOBS.get() : JOBS_PAY_SPAWNER_MOBS.getDefault();
    }

    /** Overrides the tax settings, for game tests. Null goes back to the settings. */
    private static volatile TaxRates taxOverride;

    public static void overrideTaxes(TaxRates rates) {
        taxOverride = rates;
    }

    /** All the tax rules, in stored units. {@link TaxRates#NONE} when taxes are turned off. */
    public static TaxRates taxRates() {
        TaxRates override = taxOverride;
        if (override != null) {
            return override;
        }
        if (!(SPEC.isLoaded() ? TAXES_ENABLED.get() : TAXES_ENABLED.getDefault())) {
            return TaxRates.NONE;
        }
        return new TaxRates(
                basisPoints(SALES_TAX_PERCENT),
                basisPoints(PAY_FEE_PERCENT),
                basisPoints(WEALTH_TAX_PERCENT),
                money(WEALTH_TAX_THRESHOLD),
                24_000L * (SPEC.isLoaded() ? WEALTH_TAX_DAYS.get() : WEALTH_TAX_DAYS.getDefault()),
                money(ATM_FEE),
                basisPoints(ATM_FEE_PERCENT));
    }

    public static boolean savingsEnabled() {
        return SPEC.isLoaded() ? SAVINGS_ENABLED.get() : SAVINGS_ENABLED.getDefault();
    }

    /** Savings interest per period, in basis points. */
    public static long savingsInterestBasisPoints() {
        return basisPoints(SAVINGS_INTEREST_PERCENT);
    }

    /** Game ticks between savings interest payments. */
    public static long savingsInterestTicks() {
        return 24_000L * (SPEC.isLoaded() ? SAVINGS_INTEREST_DAYS.get() : SAVINGS_INTEREST_DAYS.getDefault());
    }

    public static boolean loansEnabled() {
        return SPEC.isLoaded() ? LOANS_ENABLED.get() : LOANS_ENABLED.getDefault();
    }

    /** All the loan rules, in stored units. */
    public static LoanTerms loanTerms() {
        long starting = money(LOAN_STARTING_LIMIT);
        return new LoanTerms(
                starting,
                Math.max(starting, money(LOAN_MAX_LIMIT)),
                money(LOAN_LIMIT_INCREASE),
                basisPoints(LOAN_FEE_PERCENT),
                SPEC.isLoaded() ? LOAN_PAYMENTS.get() : LOAN_PAYMENTS.getDefault(),
                24_000L * (SPEC.isLoaded() ? LOAN_PAYMENT_DAYS.get() : LOAN_PAYMENT_DAYS.getDefault()),
                money(LOAN_LATE_FEE));
    }

    /** All the auction house rules, in stored units. */
    public static AuctionRules auctionRules() {
        return new AuctionRules(
                SPEC.isLoaded() ? AUCTION_ENABLED.get() : AUCTION_ENABLED.getDefault(),
                money(AUCTION_LISTING_FEE),
                basisPoints(AUCTION_CUT_PERCENT),
                SPEC.isLoaded() ? AUCTION_MAX_LISTINGS.get() : AUCTION_MAX_LISTINGS.getDefault(),
                86_400_000L * (SPEC.isLoaded() ? AUCTION_LISTING_DAYS.get() : AUCTION_LISTING_DAYS.getDefault()),
                basisPoints(AUCTION_MIN_RAISE_PERCENT));
    }

    /** The trading rules, with times in milliseconds. */
    public static TradeRules tradeRules() {
        boolean loaded = SPEC.isLoaded();
        return new TradeRules(
                loaded ? TRADE_ENABLED.get() : TRADE_ENABLED.getDefault(),
                loaded ? TRADE_MAX_DISTANCE.get() : TRADE_MAX_DISTANCE.getDefault(),
                1000L * (loaded ? TRADE_REQUEST_SECONDS.get() : TRADE_REQUEST_SECONDS.getDefault()),
                1000L * (loaded ? TRADE_ACCEPT_DELAY_SECONDS.get() : TRADE_ACCEPT_DELAY_SECONDS.getDefault()),
                loaded ? TRADE_SNEAK_CLICK.get() : TRADE_SNEAK_CLICK.getDefault());
    }

    public static int logMaxEntries() {
        return SPEC.isLoaded() ? LOG_MAX_ENTRIES.get() : LOG_MAX_ENTRIES.getDefault();
    }
}
