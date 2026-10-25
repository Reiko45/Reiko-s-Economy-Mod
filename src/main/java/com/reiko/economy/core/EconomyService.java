package com.reiko.economy.core;

import com.reiko.economy.api.EconomyApi;
import com.reiko.economy.api.TransactionEvent;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.api.TransactionType;
import com.reiko.economy.config.EconomyConfig;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The one place balances change. Commands, shops, ATMs and other mods all call this, so every change is
 * validated the same way and fires the same events.
 *
 * <p>All methods must be called on the server thread.
 */
public final class EconomyService implements EconomyApi {
    public static final EconomyService INSTANCE = new EconomyService();

    private EconomyService() {
    }

    /**
     * Returns the player's account, creating it with the configured starting balance if this is the first
     * time we have seen them. Also refreshes the stored name if the player renamed their profile.
     */
    public Ledger.Account ensureAccount(MinecraftServer server, UUID player, @Nullable String name) {
        Ledger ledger = Ledger.get(server);
        Optional<Ledger.Account> existing = ledger.find(player);
        if (existing.isPresent()) {
            Ledger.Account account = existing.get();
            if (name != null && !name.isEmpty() && !name.equals(account.lastKnownName())) {
                ledger.rename(account, name);
            }
            return account;
        }
        return ledger.create(player, name, EconomyConfig.startingBalance());
    }

    public Optional<Ledger.Account> findAccount(MinecraftServer server, UUID player) {
        return Ledger.get(server).find(player);
    }

    @Override
    public boolean hasAccount(MinecraftServer server, UUID player) {
        return Ledger.get(server).find(player).isPresent();
    }

    @Override
    public long getBalance(MinecraftServer server, UUID player) {
        return Ledger.get(server).find(player).map(Ledger.Account::balance).orElse(0L);
    }

    @Override
    public TransactionResult deposit(MinecraftServer server, UUID player, long amount, String reason) {
        if (amount <= 0) {
            return TransactionResult.INVALID_AMOUNT;
        }
        Ledger ledger = Ledger.get(server);
        Optional<Ledger.Account> account = ledger.find(player);
        if (account.isEmpty()) {
            return TransactionResult.NO_ACCOUNT;
        }
        long newBalance = account.get().balance() + amount;
        if (amount > Money.MAX_BALANCE || newBalance > Money.MAX_BALANCE) {
            return TransactionResult.BALANCE_LIMIT;
        }
        if (isCancelled(new TransactionEvent.Pre(server, TransactionType.DEPOSIT, null, player, amount, reason))) {
            return TransactionResult.CANCELLED;
        }
        ledger.setBalance(account.get(), newBalance);
        NeoForge.EVENT_BUS.post(new TransactionEvent.Post(server, TransactionType.DEPOSIT, null, player, amount, reason));
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult withdraw(MinecraftServer server, UUID player, long amount, String reason) {
        if (amount <= 0) {
            return TransactionResult.INVALID_AMOUNT;
        }
        Ledger ledger = Ledger.get(server);
        Optional<Ledger.Account> account = ledger.find(player);
        if (account.isEmpty()) {
            return TransactionResult.NO_ACCOUNT;
        }
        if (account.get().balance() < amount) {
            return TransactionResult.INSUFFICIENT_FUNDS;
        }
        if (isCancelled(new TransactionEvent.Pre(server, TransactionType.WITHDRAW, player, null, amount, reason))) {
            return TransactionResult.CANCELLED;
        }
        ledger.setBalance(account.get(), account.get().balance() - amount);
        NeoForge.EVENT_BUS.post(new TransactionEvent.Post(server, TransactionType.WITHDRAW, player, null, amount, reason));
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult transfer(MinecraftServer server, UUID from, UUID to, long amount, String reason) {
        if (amount <= 0) {
            return TransactionResult.INVALID_AMOUNT;
        }
        if (from.equals(to)) {
            return TransactionResult.SAME_ACCOUNT;
        }
        Ledger ledger = Ledger.get(server);
        Optional<Ledger.Account> source = ledger.find(from);
        Optional<Ledger.Account> target = ledger.find(to);
        if (source.isEmpty() || target.isEmpty()) {
            return TransactionResult.NO_ACCOUNT;
        }
        if (source.get().balance() < amount) {
            return TransactionResult.INSUFFICIENT_FUNDS;
        }
        if (target.get().balance() + amount > Money.MAX_BALANCE) {
            return TransactionResult.BALANCE_LIMIT;
        }
        if (isCancelled(new TransactionEvent.Pre(server, TransactionType.TRANSFER, from, to, amount, reason))) {
            return TransactionResult.CANCELLED;
        }
        ledger.setBalance(source.get(), source.get().balance() - amount);
        ledger.setBalance(target.get(), target.get().balance() + amount);
        NeoForge.EVENT_BUS.post(new TransactionEvent.Post(server, TransactionType.TRANSFER, from, to, amount, reason));
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult setBalance(MinecraftServer server, UUID player, long amount, String reason) {
        if (amount < 0) {
            return TransactionResult.INVALID_AMOUNT;
        }
        if (amount > Money.MAX_BALANCE) {
            return TransactionResult.BALANCE_LIMIT;
        }
        Ledger ledger = Ledger.get(server);
        Optional<Ledger.Account> account = ledger.find(player);
        if (account.isEmpty()) {
            return TransactionResult.NO_ACCOUNT;
        }
        if (isCancelled(new TransactionEvent.Pre(server, TransactionType.SET, null, player, amount, reason))) {
            return TransactionResult.CANCELLED;
        }
        ledger.setBalance(account.get(), amount);
        NeoForge.EVENT_BUS.post(new TransactionEvent.Post(server, TransactionType.SET, null, player, amount, reason));
        return TransactionResult.SUCCESS;
    }

    @Override
    public String format(long amount) {
        return Money.format(amount, EconomyConfig.symbol(), EconomyConfig.symbolAfterAmount());
    }

    @Override
    public OptionalLong parse(String text) {
        return Money.parse(text);
    }

    private static boolean isCancelled(TransactionEvent.Pre event) {
        return NeoForge.EVENT_BUS.post(event).isCanceled();
    }
}
