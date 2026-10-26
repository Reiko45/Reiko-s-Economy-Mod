package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What taxes have taken out of the economy, when the next wealth tax is due, and wealth tax that offline
 * players haven't heard about yet. Saved with the overworld in {@code data/reikoeconomy_taxes.dat}.
 *
 * <p>Only {@link TaxService} changes it.
 */
public final class TaxLedger extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_taxes";

    private final Map<TaxKind, Long> collected = new EnumMap<>(TaxKind.class);
    /** Game time of the next wealth tax; 0 until the first one is scheduled. */
    private long nextWealthTax;
    private final Map<UUID, Long> unseenWealthTax = new HashMap<>();

    public static TaxLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TaxLedger::new, TaxLedger::load), DATA_NAME);
    }

    public static TaxLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        TaxLedger ledger = new TaxLedger();
        CompoundTag totals = tag.getCompound("collected");
        for (TaxKind kind : TaxKind.values()) {
            if (totals.contains(kind.id())) {
                ledger.collected.put(kind, totals.getLong(kind.id()));
            }
        }
        ledger.nextWealthTax = tag.getLong("next_wealth_tax");
        ListTag unseen = tag.getList("unseen_wealth_tax", Tag.TAG_COMPOUND);
        for (int i = 0; i < unseen.size(); i++) {
            CompoundTag entry = unseen.getCompound(i);
            if (entry.hasUUID("player")) {
                ledger.unseenWealthTax.put(entry.getUUID("player"), entry.getLong("amount"));
            }
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag totals = new CompoundTag();
        collected.forEach((kind, amount) -> totals.putLong(kind.id(), amount));
        tag.put("collected", totals);
        tag.putLong("next_wealth_tax", nextWealthTax);
        ListTag unseen = new ListTag();
        unseenWealthTax.forEach((player, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putLong("amount", amount);
            unseen.add(entry);
        });
        tag.put("unseen_wealth_tax", unseen);
        return tag;
    }

    /** Everything this kind of tax has taken since the world began. */
    public long collected(TaxKind kind) {
        return collected.getOrDefault(kind, 0L);
    }

    public long nextWealthTax() {
        return nextWealthTax;
    }

    /** Wealth tax taken while the player was offline that they haven't been told about yet. */
    public long unseenWealthTax(UUID player) {
        return unseenWealthTax.getOrDefault(player, 0L);
    }

    void addCollected(TaxKind kind, long amount) {
        if (amount > 0) {
            collected.merge(kind, amount, (a, b) -> Math.min(Long.MAX_VALUE - b, a) + b);
            setDirty();
        }
    }

    void setNextWealthTax(long time) {
        nextWealthTax = time;
        setDirty();
    }

    void addUnseenWealthTax(UUID player, long amount) {
        unseenWealthTax.merge(player, amount, Long::sum);
        setDirty();
    }

    /** Removes and returns the wealth tax a player hasn't been told about, or 0. */
    long takeUnseenWealthTax(UUID player) {
        Long amount = unseenWealthTax.remove(player);
        if (amount != null) {
            setDirty();
        }
        return amount == null ? 0 : amount;
    }
}
