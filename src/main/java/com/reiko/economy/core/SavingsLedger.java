package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every savings balance, when interest is next paid, and interest offline players haven't heard about yet.
 * Saved with the overworld in {@code data/reikoeconomy_savings.dat}. Savings are separate from the bank balance
 * that cards and {@code /pay} use. Only {@link SavingsService} changes it.
 */
public final class SavingsLedger extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_savings";

    private final Map<UUID, Long> savings = new HashMap<>();
    private final Map<UUID, Long> unseenInterest = new HashMap<>();
    /** Game time of the next interest payment; 0 until the first one is scheduled. */
    private long nextInterest;

    public static SavingsLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SavingsLedger::new, SavingsLedger::load), DATA_NAME);
    }

    public static SavingsLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        SavingsLedger ledger = new SavingsLedger();
        ledger.nextInterest = tag.getLong("next_interest");
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            UUID owner = entry.getUUID("owner");
            if (entry.getLong("savings") > 0) {
                ledger.savings.put(owner, entry.getLong("savings"));
            }
            if (entry.getLong("unseen_interest") > 0) {
                ledger.unseenInterest.put(owner, entry.getLong("unseen_interest"));
            }
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("next_interest", nextInterest);
        ListTag list = new ListTag();
        Set<UUID> owners = new HashSet<>(savings.keySet());
        owners.addAll(unseenInterest.keySet());
        for (UUID owner : owners) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", owner);
            entry.putLong("savings", savings.getOrDefault(owner, 0L));
            entry.putLong("unseen_interest", unseenInterest.getOrDefault(owner, 0L));
            list.add(entry);
        }
        tag.put("accounts", list);
        return tag;
    }

    public long balance(UUID owner) {
        return savings.getOrDefault(owner, 0L);
    }

    /** All savings together. */
    public long total() {
        long total = 0;
        for (long amount : savings.values()) {
            total += amount;
        }
        return total;
    }

    public long nextInterest() {
        return nextInterest;
    }

    Map<UUID, Long> balances() {
        return Map.copyOf(savings);
    }

    void set(UUID owner, long amount) {
        if (amount <= 0) {
            savings.remove(owner);
        } else {
            savings.put(owner, amount);
        }
        setDirty();
    }

    void setNextInterest(long time) {
        nextInterest = time;
        setDirty();
    }

    void addUnseenInterest(UUID owner, long amount) {
        unseenInterest.merge(owner, amount, Long::sum);
        setDirty();
    }

    long takeUnseenInterest(UUID owner) {
        Long amount = unseenInterest.remove(owner);
        if (amount != null) {
            setDirty();
        }
        return amount == null ? 0 : amount;
    }
}
