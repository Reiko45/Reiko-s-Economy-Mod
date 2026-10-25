package com.reiko.economy.core;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.api.TransactionType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The most recent balance changes in the world, oldest dropped first once the configured limit is reached.
 * Saved with the overworld in {@code data/reikoeconomy_transactions.dat}. Backs {@code /eco history} and
 * {@code /transactions}, and gives admins a trail to follow when hunting duplication bugs.
 */
public final class TransactionLog extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_transactions";

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    /** One balance change. {@code from} is null for deposits and sets; {@code to} is null for withdrawals. */
    public record Entry(long time, TransactionType type, @Nullable UUID from, @Nullable UUID to, long amount, String reason) {
        public boolean involves(UUID player) {
            return player.equals(from) || player.equals(to);
        }
    }

    public static TransactionLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TransactionLog::new, TransactionLog::load), DATA_NAME);
    }

    public void add(Entry entry, int maxEntries) {
        entries.addLast(entry);
        while (entries.size() > Math.max(1, maxEntries)) {
            entries.removeFirst();
        }
        setDirty();
    }

    /** Entries involving the player, newest first. */
    public List<Entry> forPlayer(UUID player) {
        List<Entry> result = new ArrayList<>();
        Iterator<Entry> newestFirst = entries.descendingIterator();
        while (newestFirst.hasNext()) {
            Entry entry = newestFirst.next();
            if (entry.involves(player)) {
                result.add(entry);
            }
        }
        return result;
    }

    public int size() {
        return entries.size();
    }

    public static TransactionLog load(CompoundTag tag, HolderLookup.Provider registries) {
        TransactionLog log = new TransactionLog();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            TransactionType type;
            try {
                type = TransactionType.valueOf(entry.getString("type"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            log.entries.addLast(new Entry(entry.getLong("time"), type,
                    entry.hasUUID("from") ? entry.getUUID("from") : null,
                    entry.hasUUID("to") ? entry.getUUID("to") : null,
                    entry.getLong("amount"), entry.getString("reason")));
        }
        return log;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag item = new CompoundTag();
            item.putLong("time", entry.time());
            item.putString("type", entry.type().name());
            if (entry.from() != null) {
                item.putUUID("from", entry.from());
            }
            if (entry.to() != null) {
                item.putUUID("to", entry.to());
            }
            item.putLong("amount", entry.amount());
            item.putString("reason", entry.reason());
            list.add(item);
        }
        tag.putInt("version", 1);
        tag.put("entries", list);
        return tag;
    }
}
