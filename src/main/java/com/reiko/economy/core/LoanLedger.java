package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
 * Every player's loan record: the loan they have now, if any, and their borrowing limit. Saved with the
 * overworld in {@code data/reikoeconomy_loans.dat}. Only {@link LoanService} changes it.
 */
public final class LoanLedger extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_loans";

    private final Map<UUID, Borrower> borrowers = new HashMap<>();

    /** One player's loan record. Read-only outside this package. */
    public static final class Borrower {
        private final UUID owner;
        private long limit;
        private long borrowed;
        private long owed;
        private long payment;
        private long prepaid;
        private boolean missed;
        private long nextDue;
        private int repaid;

        private Borrower(UUID owner) {
            this.owner = owner;
        }

        public UUID owner() {
            return owner;
        }

        /** Most they can borrow. */
        public long limit() {
            return limit;
        }

        /** What the current loan was for, before interest. */
        public long borrowed() {
            return borrowed;
        }

        /** What's left to pay back; 0 when there's no loan. */
        public long owed() {
            return owed;
        }

        public boolean hasLoan() {
            return owed > 0;
        }

        public long payment() {
            return payment;
        }

        /** Game time the next payment is due. */
        public long nextDue() {
            return nextDue;
        }

        public boolean missedAPayment() {
            return missed;
        }

        /** Loans paid off so far. */
        public int repaid() {
            return repaid;
        }

        public LoanTerms.State state() {
            return new LoanTerms.State(owed, payment, prepaid, missed);
        }
    }

    public static LoanLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LoanLedger::new, LoanLedger::load), DATA_NAME);
    }

    public static LoanLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        LoanLedger ledger = new LoanLedger();
        ListTag list = tag.getList("borrowers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            Borrower borrower = new Borrower(entry.getUUID("owner"));
            borrower.limit = entry.getLong("limit");
            borrower.borrowed = entry.getLong("borrowed");
            borrower.owed = entry.getLong("owed");
            borrower.payment = entry.getLong("payment");
            borrower.prepaid = entry.getLong("prepaid");
            borrower.missed = entry.getBoolean("missed");
            borrower.nextDue = entry.getLong("next_due");
            borrower.repaid = entry.getInt("repaid");
            ledger.borrowers.put(borrower.owner, borrower);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Borrower borrower : borrowers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", borrower.owner);
            entry.putLong("limit", borrower.limit);
            entry.putLong("borrowed", borrower.borrowed);
            entry.putLong("owed", borrower.owed);
            entry.putLong("payment", borrower.payment);
            entry.putLong("prepaid", borrower.prepaid);
            entry.putBoolean("missed", borrower.missed);
            entry.putLong("next_due", borrower.nextDue);
            entry.putInt("repaid", borrower.repaid);
            list.add(entry);
        }
        tag.put("borrowers", list);
        return tag;
    }

    public Optional<Borrower> find(UUID owner) {
        return Optional.ofNullable(borrowers.get(owner));
    }

    public Collection<Borrower> borrowers() {
        return Collections.unmodifiableCollection(borrowers.values());
    }

    Borrower getOrCreate(UUID owner, long startingLimit) {
        return borrowers.computeIfAbsent(owner, id -> {
            Borrower borrower = new Borrower(id);
            borrower.limit = startingLimit;
            setDirty();
            return borrower;
        });
    }

    void startLoan(Borrower borrower, long borrowed, long owed, long payment, long nextDue) {
        borrower.borrowed = borrowed;
        borrower.owed = owed;
        borrower.payment = payment;
        borrower.prepaid = 0;
        borrower.missed = false;
        borrower.nextDue = nextDue;
        setDirty();
    }

    /** An early payment: less owed, and it counts toward the next payment. */
    void pay(Borrower borrower, long amount) {
        borrower.owed -= amount;
        borrower.prepaid += amount;
        setDirty();
    }

    void apply(Borrower borrower, LoanTerms.State state, long nextDue) {
        borrower.owed = state.owed();
        borrower.payment = state.payment();
        borrower.prepaid = state.prepaid();
        borrower.missed = state.missed();
        borrower.nextDue = nextDue;
        setDirty();
    }

    /** Ends the loan. The limit goes up if {@code newLimit} is higher. */
    void close(Borrower borrower, long newLimit, boolean countAsRepaid) {
        borrower.owed = 0;
        borrower.prepaid = 0;
        borrower.payment = 0;
        borrower.limit = Math.max(borrower.limit, newLimit);
        if (countAsRepaid) {
            borrower.repaid++;
        }
        setDirty();
    }

    void setLimit(Borrower borrower, long limit) {
        borrower.limit = limit;
        setDirty();
    }
}
