package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Every credit card account, saved with the overworld in {@code data/reikoeconomy_credit.dat}. Separate from
 * the bank ledger: what a player owes on credit is not money in their account.
 *
 * <p>Only {@link CreditService} changes these accounts.
 */
public final class CreditLedger extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_credit";
    private static final int DATA_VERSION = 1;
    /** Most history lines kept per account; the oldest are dropped first. */
    public static final int HISTORY_LIMIT = 60;

    private final Map<UUID, Account> accounts = new HashMap<>();

    public static CreditLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CreditLedger::new, CreditLedger::load), DATA_NAME);
    }

    public static CreditLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        CreditLedger ledger = new CreditLedger();
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Account account = new Account(entry.getUUID("owner"));
            account.name = entry.getString("name");
            account.cardId = entry.hasUUID("card_id") ? entry.getUUID("card_id") : null;
            account.pinHash = entry.getString("pin_hash");
            account.pinFailures = entry.getInt("pin_failures");
            account.cardsIssued = entry.getInt("cards_issued");
            account.limit = entry.getLong("limit");
            account.balance = entry.getLong("balance");
            account.statementBalance = entry.getLong("statement_balance");
            account.minimumDue = entry.getLong("minimum_due");
            account.paidSinceStatement = entry.getLong("paid_since_statement");
            account.nextStatement = entry.getLong("next_statement");
            account.missed = entry.getInt("missed");
            account.frozen = entry.getBoolean("frozen");
            ListTag history = entry.getList("history", Tag.TAG_COMPOUND);
            for (int j = 0; j < history.size(); j++) {
                CompoundTag line = history.getCompound(j);
                Component description = ComponentSerialization.CODEC
                        .parse(registries.createSerializationContext(NbtOps.INSTANCE), line.get("text"))
                        .result().orElse(Component.empty());
                account.history.add(new HistoryEntry(line.getLong("time"), description, line.getLong("amount")));
            }
            ledger.accounts.put(account.owner, account);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Account account : accounts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", account.owner);
            entry.putString("name", account.name);
            if (account.cardId != null) {
                entry.putUUID("card_id", account.cardId);
            }
            entry.putString("pin_hash", account.pinHash);
            entry.putInt("pin_failures", account.pinFailures);
            entry.putInt("cards_issued", account.cardsIssued);
            entry.putLong("limit", account.limit);
            entry.putLong("balance", account.balance);
            entry.putLong("statement_balance", account.statementBalance);
            entry.putLong("minimum_due", account.minimumDue);
            entry.putLong("paid_since_statement", account.paidSinceStatement);
            entry.putLong("next_statement", account.nextStatement);
            entry.putInt("missed", account.missed);
            entry.putBoolean("frozen", account.frozen);
            ListTag history = new ListTag();
            for (HistoryEntry line : account.history) {
                CompoundTag saved = new CompoundTag();
                saved.putLong("time", line.time());
                saved.putLong("amount", line.amount());
                ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), line.description())
                        .result().ifPresent(text -> saved.put("text", text));
                history.add(saved);
            }
            entry.put("history", history);
            list.add(entry);
        }
        tag.putInt("version", DATA_VERSION);
        tag.put("accounts", list);
        return tag;
    }

    public Optional<Account> find(UUID owner) {
        return Optional.ofNullable(accounts.get(owner));
    }

    public Collection<Account> accounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    Account create(UUID owner, String name, long limit, long nextStatement) {
        Account account = new Account(owner);
        account.name = name;
        account.limit = limit;
        account.nextStatement = nextStatement;
        accounts.put(owner, account);
        setDirty();
        return account;
    }

    void issueCard(Account account, UUID cardId, String pinHash, String name) {
        account.cardId = cardId;
        account.pinHash = pinHash;
        account.pinFailures = 0;
        account.cardsIssued++;
        account.name = name;
        setDirty();
    }

    void setPinFailures(Account account, int failures) {
        account.pinFailures = failures;
        setDirty();
    }

    /** Adds a purchase, cash advance or fee to what the account owes. */
    void charge(Account account, long amount) {
        account.balance += amount;
        setDirty();
    }

    /** Records a payment. Paying everything off unfreezes the card. Returns true if it did. */
    boolean pay(Account account, long amount) {
        account.balance -= amount;
        account.paidSinceStatement += amount;
        setDirty();
        if (account.frozen && account.balance <= 0) {
            account.frozen = false;
            account.missed = 0;
            return true;
        }
        return false;
    }

    void apply(Account account, CreditStatement.State state, long nextStatement) {
        account.balance = state.balance();
        account.limit = state.limit();
        account.statementBalance = state.statementBalance();
        account.minimumDue = state.minimumDue();
        account.paidSinceStatement = state.paidSinceStatement();
        account.missed = state.missed();
        account.frozen = state.frozen();
        account.nextStatement = nextStatement;
        setDirty();
    }

    void setLimit(Account account, long limit) {
        account.limit = limit;
        setDirty();
    }

    /**
     * Adds a line to the account's history, for the itemized bill. Doesn't change the balance.
     *
     * @param amount above zero for charges, below zero for payments, zero for notes like a statement
     */
    void record(Account account, long time, Component description, long amount) {
        account.history.add(new HistoryEntry(time, description, amount));
        while (account.history.size() > HISTORY_LIMIT) {
            account.history.remove(0);
        }
        setDirty();
    }

    /** Wipes the debt, the current statement and any missed payments. */
    void forgive(Account account) {
        account.balance = 0;
        account.statementBalance = 0;
        account.minimumDue = 0;
        account.paidSinceStatement = 0;
        account.missed = 0;
        account.frozen = false;
        setDirty();
    }

    /** One line of an itemized bill: when (game time), what, and how much. */
    public record HistoryEntry(long time, Component description, long amount) {
    }

    /** One player's credit card account. Read-only outside this package. */
    public static final class Account {
        private final UUID owner;
        private String name = "";
        @Nullable
        private UUID cardId;
        private String pinHash = "";
        private int pinFailures;
        private int cardsIssued;
        private long limit;
        private long balance;
        private long statementBalance;
        private long minimumDue;
        private long paidSinceStatement;
        /** Game time of the next statement; the current statement's payment is due then. */
        private long nextStatement;
        private int missed;
        private boolean frozen;
        /** Oldest first. */
        private final List<HistoryEntry> history = new ArrayList<>();

        private Account(UUID owner) {
            this.owner = owner;
        }

        public UUID owner() {
            return owner;
        }

        public String name() {
            return name;
        }

        @Nullable
        public UUID cardId() {
            return cardId;
        }

        String pinHash() {
            return pinHash;
        }

        public int pinFailures() {
            return pinFailures;
        }

        public int cardsIssued() {
            return cardsIssued;
        }

        public long limit() {
            return limit;
        }

        public long balance() {
            return balance;
        }

        public long nextStatement() {
            return nextStatement;
        }

        public boolean frozen() {
            return frozen;
        }

        /** The itemized bill, newest first. */
        public List<HistoryEntry> history() {
            List<HistoryEntry> newestFirst = new ArrayList<>(history);
            Collections.reverse(newestFirst);
            return newestFirst;
        }

        public CreditStatement.State state() {
            return new CreditStatement.State(balance, limit, statementBalance, minimumDue, paidSinceStatement, missed, frozen);
        }
    }
}
