package com.reiko.economy.core;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.shop.ShopActionResult;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Savings accounts. Money moves between a player's bank balance (what cards, {@code /pay} and shops use) and
 * their savings, which earn interest every few days. Moving money in and out is free.
 */
public final class SavingsService {
    private SavingsService() {
    }

    public static long balance(MinecraftServer server, UUID owner) {
        return SavingsLedger.get(server).balance(owner);
    }

    /** Moves {@code amountText} from the bank balance into savings. Blank moves the whole balance. */
    public static ShopActionResult deposit(MinecraftServer server, UUID account, String amountText) {
        if (!EconomyConfig.savingsEnabled()) {
            return ShopActionResult.fail("savings.reikoeconomy.disabled");
        }
        EconomyService eco = EconomyService.INSTANCE;
        long checking = eco.getBalance(server, account);
        OptionalLong parsed = amountText.isBlank() ? OptionalLong.of(checking) : Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long amount = parsed.getAsLong();
        if (amount <= 0) {
            return ShopActionResult.fail("savings.reikoeconomy.nothing_to_move");
        }
        SavingsLedger ledger = SavingsLedger.get(server);
        long savings = ledger.balance(account);
        if (savings + amount > Money.MAX_BALANCE) {
            return ShopActionResult.fail(TransactionResult.BALANCE_LIMIT.translationKey());
        }
        TransactionResult result = eco.withdraw(server, account, amount, "savings.deposit");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        ledger.set(account, savings + amount);
        return ShopActionResult.ok(Component.translatable("savings.reikoeconomy.deposited", eco.format(amount),
                eco.format(savings + amount), eco.format(eco.getBalance(server, account))));
    }

    /** Moves {@code amountText} from savings back to the bank balance. Blank moves all of it. */
    public static ShopActionResult withdraw(MinecraftServer server, UUID account, String amountText) {
        EconomyService eco = EconomyService.INSTANCE;
        SavingsLedger ledger = SavingsLedger.get(server);
        long savings = ledger.balance(account);
        OptionalLong parsed = amountText.isBlank() ? OptionalLong.of(savings) : Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long amount = parsed.getAsLong();
        if (amount <= 0) {
            return ShopActionResult.fail("savings.reikoeconomy.empty");
        }
        if (amount > savings) {
            return ShopActionResult.fail("savings.reikoeconomy.not_enough", eco.format(savings));
        }
        // Withdrawing always works, even with savings turned off, so nobody's money gets stuck.
        TransactionResult result = eco.deposit(server, account, amount, "savings.withdraw");
        if (!result.isSuccess()) {
            return ShopActionResult.fail(result.translationKey());
        }
        ledger.set(account, savings - amount);
        return ShopActionResult.ok(Component.translatable("savings.reikoeconomy.withdrew", eco.format(amount),
                eco.format(savings - amount), eco.format(eco.getBalance(server, account))));
    }

    /** Takes up to {@code amount} out of savings (for loan payments and the wealth tax). Returns what was taken. */
    static long take(MinecraftServer server, UUID account, long amount) {
        SavingsLedger ledger = SavingsLedger.get(server);
        long taken = Math.min(Math.max(0, amount), ledger.balance(account));
        if (taken > 0) {
            ledger.set(account, ledger.balance(account) - taken);
        }
        return taken;
    }

    // ---- interest ----

    /** Pays interest on every savings account if it's due. The first call only schedules the first payment. */
    public static void runInterest(MinecraftServer server, long now) {
        SavingsLedger ledger = SavingsLedger.get(server);
        long period = EconomyConfig.savingsInterestTicks();
        if (ledger.nextInterest() == 0) {
            ledger.setNextInterest(now + period);
            return;
        }
        if (now < ledger.nextInterest()) {
            return;
        }
        ledger.setNextInterest(now + period);
        if (EconomyConfig.savingsEnabled()) {
            payInterest(server, EconomyConfig.savingsInterestBasisPoints());
        }
    }

    /** Pays interest on every savings account now. Returns the total paid. */
    public static long payInterest(MinecraftServer server, long basisPoints) {
        SavingsLedger ledger = SavingsLedger.get(server);
        EconomyService eco = EconomyService.INSTANCE;
        long total = 0;
        for (Map.Entry<UUID, Long> account : ledger.balances().entrySet()) {
            long interest = Math.min((account.getValue() * basisPoints + 5_000) / 10_000, Money.MAX_BALANCE - account.getValue());
            if (interest <= 0) {
                continue;
            }
            ledger.set(account.getKey(), account.getValue() + interest);
            total += interest;
            ServerPlayer player = server.getPlayerList().getPlayer(account.getKey());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("savings.reikoeconomy.interest", eco.format(interest),
                        eco.format(account.getValue() + interest)).withStyle(ChatFormatting.GOLD));
            } else {
                ledger.addUnseenInterest(account.getKey(), interest);
            }
        }
        return total;
    }

    /** Tells a player who just joined what their savings earned while they were away. */
    public static void deliver(ServerPlayer player) {
        long unseen = SavingsLedger.get(player.server).takeUnseenInterest(player.getUUID());
        if (unseen > 0) {
            EconomyService eco = EconomyService.INSTANCE;
            player.sendSystemMessage(Component.translatable("savings.reikoeconomy.interest_while_away", eco.format(unseen),
                    eco.format(balance(player.server, player.getUUID()))).withStyle(ChatFormatting.GOLD));
        }
    }
}
