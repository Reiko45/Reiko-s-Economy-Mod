package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every bank account in the world, saved with the overworld in {@code data/reikoeconomy_ledger.dat}.
 *
 * <p>This is the single source of truth for balances. It is keyed by player UUID rather than stored on
 * the player entity, so offline players can still be paid and {@code /baltop} can rank everyone.
 *
 * <p>Only {@link EconomyService} changes balances; everything else should go through it so that
 * validation and {@code TransactionEvent}s always happen.
 */
public final class Ledger extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_ledger";
    private static final int DATA_VERSION = 1;

    private final Map<UUID, Account> accounts = new HashMap<>();

    public static Ledger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static SavedData.Factory<Ledger> factory() {
        // NeoForge's two-argument factory skips vanilla's DataFixer, which does not apply to mod data.
        return new SavedData.Factory<>(Ledger::new, Ledger::load);
    }

    public static Ledger load(CompoundTag tag, HolderLookup.Provider registries) {
        Ledger ledger = new Ledger();
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                ReikoEconomy.LOGGER.warn("Skipping a ledger entry with no owner: {}", entry);
                continue;
            }
            UUID owner = entry.getUUID("owner");
            Account account = new Account(owner, entry.getString("name"), entry.getLong("balance"));
            account.cardId = entry.hasUUID("card_id") ? entry.getUUID("card_id") : null;
            account.pinHash = entry.getString("pin_hash");
            account.pinFailures = entry.getInt("pin_failures");
            account.cardsIssued = entry.getInt("cards_issued");
            ledger.accounts.put(owner, account);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Account account : accounts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", account.owner);
            entry.putString("name", account.lastKnownName);
            entry.putLong("balance", account.balance);
            if (account.cardId != null) {
                entry.putUUID("card_id", account.cardId);
                entry.putString("pin_hash", account.pinHash);
                entry.putInt("pin_failures", account.pinFailures);
            }
            entry.putInt("cards_issued", account.cardsIssued);
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

    /** All accounts, richest first; ties sorted by name. */
    public List<Account> ranked() {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort(Comparator.comparingLong(Account::balance).reversed()
                .thenComparing(Account::lastKnownName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Sum of every balance. */
    public long total() {
        long sum = 0;
        for (Account account : accounts.values()) {
            sum += account.balance;
        }
        return sum;
    }

    Account create(UUID owner, String name, long startingBalance) {
        Account account = new Account(owner, name == null ? "" : name, startingBalance);
        accounts.put(owner, account);
        setDirty();
        return account;
    }

    /** Deletes an account. Only for closing a shared account, once its money has been moved out. */
    void remove(UUID owner) {
        if (accounts.remove(owner) != null) {
            setDirty();
        }
    }

    void setBalance(Account account, long balance) {
        account.balance = balance;
        setDirty();
    }

    void rename(Account account, String name) {
        account.lastKnownName = name;
        setDirty();
    }

    /** Makes {@code cardId} the account's only working card, cancelling any earlier one. */
    void issueCard(Account account, UUID cardId, String pinHash) {
        account.cardId = cardId;
        account.pinHash = pinHash;
        account.pinFailures = 0;
        account.cardsIssued++;
        setDirty();
    }

    void setPinFailures(Account account, int failures) {
        account.pinFailures = failures;
        setDirty();
    }

    /** One player's bank account. Read-only outside this package. */
    public static final class Account {
        private final UUID owner;
        private String lastKnownName;
        private long balance;
        /** The one debit card that works for this account, or null if none was issued. */
        @org.jetbrains.annotations.Nullable
        private UUID cardId;
        /** Salted SHA-256 of the card's PIN; never the PIN itself. */
        private String pinHash = "";
        /** Wrong PINs in a row. The card stops working at {@code CardService.MAX_PIN_FAILURES}. */
        private int pinFailures;
        /** Cards ever issued. The first is free. */
        private int cardsIssued;

        private Account(UUID owner, String lastKnownName, long balance) {
            this.owner = owner;
            this.lastKnownName = lastKnownName;
            this.balance = balance;
        }

        public UUID owner() {
            return owner;
        }

        /** The player's name the last time they joined. Used for display when they are offline. */
        public String lastKnownName() {
            return lastKnownName;
        }

        /** Balance in stored units (hundredths). */
        public long balance() {
            return balance;
        }

        @org.jetbrains.annotations.Nullable
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
    }
}
